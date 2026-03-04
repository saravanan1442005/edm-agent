package com.example.edmagent

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.edmagent.collector.AppInventoryCollector
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.data.AppInventoryRequest
import com.example.edmagent.data.EnrollRequest
import com.example.edmagent.network.RetrofitClient
import com.example.edmagent.receiver.DeviceAdminReceiver
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvUuid: TextView
    private lateinit var tvModel: TextView
    private lateinit var tvManufacturer: TextView
    private lateinit var tvOsVersion: TextView
    private lateinit var tvSdk: TextView
    private lateinit var tvSerial: TextView
    private lateinit var tvImei: TextView
    private lateinit var btnEnroll: Button
    private lateinit var btnSync: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hide system status bar decorations for clean look
        window.statusBarColor = 0xFF0078D4.toInt()

        tvStatus = findViewById(R.id.tvStatus)
        tvUuid = findViewById(R.id.tvUuid)
        tvModel = findViewById(R.id.tvModel)
        tvManufacturer = findViewById(R.id.tvManufacturer)
        tvOsVersion = findViewById(R.id.tvOsVersion)
        tvSdk = findViewById(R.id.tvSdk)
        tvSerial = findViewById(R.id.tvSerial)
        tvImei = findViewById(R.id.tvImei)
        btnEnroll = findViewById(R.id.btnEnroll)
        btnSync = findViewById(R.id.btnSync)

        checkDeviceOwnerStatus()
        displayDeviceInfo()

        btnEnroll.setOnClickListener { enrollDevice() }
        btnSync.setOnClickListener { syncNow() }
    }

    private fun checkDeviceOwnerStatus() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, DeviceAdminReceiver::class.java)
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        val isAdminActive = dpm.isAdminActive(componentName)

        tvStatus.text = buildString {
            append("Device Owner    ${if (isDeviceOwner) "✔  Active" else "✘  Not Active"}\n")
            append("Admin Active     ${if (isAdminActive) "✔  Active" else "✘  Not Active"}\n")
            append("Backend          http://10.0.2.2:8080")
        }
    }

    private fun displayDeviceInfo() {
        val info = DeviceInfoCollector.collect(this)
        tvUuid.text = info.deviceUuid
        tvModel.text = info.model
        tvManufacturer.text = info.manufacturer
        tvOsVersion.text = "Android ${info.osVersion}"
        tvSdk.text = info.sdkVersion.toString()
        tvSerial.text = info.serialNumber
        tvImei.text = info.imei ?: "N/A"
    }

    private fun enrollDevice() {
        btnEnroll.isEnabled = false
        btnEnroll.text = "Enrolling..."
        lifecycleScope.launch {
            try {
                val deviceUuid = DeviceInfoCollector.getOrCreateUUID(this@MainActivity)
                val response = RetrofitClient.instance.enroll(
                    EnrollRequest(
                        deviceUuid = deviceUuid,
                        enrollmentToken = "MANUAL_ENROLL"
                    )
                )
                if (response.isSuccessful) {
                    appendStatus("✔  Enrolled successfully\n    ID: ${response.body()?.deviceId?.take(18)}...")
                    btnEnroll.text = "Enrolled ✔"
                } else {
                    appendStatus("✘  Enrollment failed (${response.code()})")
                    btnEnroll.isEnabled = true
                    btnEnroll.text = "Enroll Device"
                }
            } catch (e: Exception) {
                appendStatus("✘  Network error: ${e.message}")
                btnEnroll.isEnabled = true
                btnEnroll.text = "Enroll Device"
                Log.e("MainActivity", "Enroll error", e)
            }
        }
    }

    private fun syncNow() {
        btnSync.isEnabled = false
        btnSync.text = "Syncing..."
        lifecycleScope.launch {
            try {
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
                    appendStatus("✔  Sync complete — ${apps.size} apps transmitted")
                } else {
                    appendStatus("✘  Sync failed (${response.code()})")
                }
            } catch (e: Exception) {
                appendStatus("✘  Network error: ${e.message}")
                Log.e("MainActivity", "Sync error", e)
            } finally {
                btnSync.isEnabled = true
                btnSync.text = "Sync Now"
            }
        }
    }

    private fun appendStatus(message: String) {
        val current = tvStatus.text.toString()
        tvStatus.text = "$current\n$message"
    }
}