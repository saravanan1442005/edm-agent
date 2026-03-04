package com.example.edmagent

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.edmagent.collector.AppInventoryCollector
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.data.AppInventoryRequest
import com.example.edmagent.data.EnrollRequest
import com.example.edmagent.network.RetrofitClient
import com.example.edmagent.receiver.DeviceAdminReceiver
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var btnEnroll: MaterialButton
    private lateinit var btnSync: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tvStatus = findViewById(R.id.tvStatus)
        tvDeviceInfo = findViewById(R.id.tvDeviceInfo)
        btnEnroll = findViewById(R.id.btnEnroll)
        btnSync = findViewById(R.id.btnSync)

        updateStatus()
        displayDeviceInfo()

        btnEnroll.setOnClickListener {
            enrollDevice()
        }

        btnSync.setOnClickListener {
            syncNow()
        }
    }

    private fun updateStatus(message: String? = null) {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        val componentName = ComponentName(this, DeviceAdminReceiver::class.java)
        val isAdminActive = dpm.isAdminActive(componentName)

        val status = buildString {
            append("🛡️ Security Status: ${if (isDeviceOwner) "Managed" else "Unmanaged"}\n")
            append("🔑 Admin Policy: ${if (isAdminActive) "Active" else "Inactive"}\n")
            if (message != null) {
                append("\n$message")
            }
        }
        tvStatus.text = status
    }

    private fun displayDeviceInfo() {
        val info = DeviceInfoCollector.collect(this)
        tvDeviceInfo.text = buildString {
            append("DEVICE TELEMETRY\n")
            append("----------------\n")
            append("UUID:   ${info.deviceUuid}\n")
            append("Model:  ${info.model}\n")
            append("Brand:  ${info.manufacturer}\n")
            append("OS:     Android ${info.osVersion} (API ${info.sdkVersion})\n")
            append("Serial: ${info.serialNumber}\n")
            if (info.imei != null) append("IMEI:   ${info.imei}")
        }
    }

    private fun enrollDevice() {
        lifecycleScope.launch {
            try {
                updateStatus("⏳ Enrolling device with endpoint...")
                val deviceUuid = DeviceInfoCollector.getOrCreateUUID(this@MainActivity)
                val response = RetrofitClient.instance.enroll(
                    EnrollRequest(
                        deviceUuid = deviceUuid,
                        enrollmentToken = "MANUAL_ENROLL"
                    )
                )
                if (response.isSuccessful) {
                    updateStatus("✅ Device successfully enrolled.\nEndpoint ID: ${response.body()?.deviceId}")
                } else {
                    updateStatus("❌ Enrollment failed (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                updateStatus("❌ Connection error: ${e.message}")
                Log.e("MainActivity", "Enroll error", e)
            }
        }
    }

    private fun syncNow() {
        lifecycleScope.launch {
            try {
                updateStatus("⏳ Synchronizing telemetry data...")
                val deviceUuid = DeviceInfoCollector.getOrCreateUUID(this@MainActivity)

                // Send device info
                val deviceInfo = DeviceInfoCollector.collect(this@MainActivity)
                RetrofitClient.instance.sendDeviceInfo(deviceInfo)

                // Send app inventory
                val apps = AppInventoryCollector.collect(this@MainActivity)
                val response = RetrofitClient.instance.sendAppInventory(
                    AppInventoryRequest(deviceUuid = deviceUuid, apps = apps)
                )

                if (response.isSuccessful) {
                    updateStatus("✅ Sync complete. ${apps.size} packages indexed.")
                } else {
                    updateStatus("❌ Sync failed (HTTP ${response.code()})")
                }
            } catch (e: Exception) {
                updateStatus("❌ Synchronization error: ${e.message}")
                Log.e("MainActivity", "Sync error", e)
            }
        }
    }
}