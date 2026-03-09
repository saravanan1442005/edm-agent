package com.example.edmagent

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.edmagent.collector.AppInventoryCollector
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.data.AppInventoryRequest
import com.example.edmagent.data.EnrollRequest
import com.example.edmagent.network.RetrofitClient
import com.example.edmagent.receiver.DeviceAdminReceiver
import com.example.edmagent.worker.CommandPoller
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnEnroll: MaterialButton
    private lateinit var btnSync: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = ContextCompat.getColor(this, R.color.windows_blue)

        tvStatus = findViewById(R.id.tvStatus)
        btnEnroll = findViewById(R.id.btnEnroll)
        btnSync = findViewById(R.id.btnSync)

        setupDeviceInfoRows()
        refreshStatus()
        checkEnrollmentState()

        btnEnroll.setOnClickListener { enrollDevice() }
        btnSync.setOnClickListener { syncNow() }
    }

    private fun setupDeviceInfoRows() {
        val info = DeviceInfoCollector.collect(this)
        
        setupRow(findViewById(R.id.rowUuid), "Device UUID", info.deviceUuid)
        setupRow(findViewById(R.id.rowModel), "Model", info.model)
        setupRow(findViewById(R.id.rowManufacturer), "Manufacturer", info.manufacturer)
        setupRow(findViewById(R.id.rowOs), "OS Version", "Android ${info.osVersion}")
        setupRow(findViewById(R.id.rowSdk), "SDK Level", info.sdkVersion.toString())
        setupRow(findViewById(R.id.rowSerial), "Serial", info.serialNumber)
        setupRow(findViewById(R.id.rowImei), "IMEI", info.imei ?: "Not Available")
    }

    private fun setupRow(view: View, label: String, value: String) {
        view.findViewById<TextView>(R.id.label).text = label
        view.findViewById<TextView>(R.id.value).text = value
    }

    override fun onResume() {
        super.onResume()
        CommandPoller.start(this)
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        CommandPoller.stop()
    }

    private fun checkEnrollmentState() {
        val prefs = getSharedPreferences("edm_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_enrolled", false)) {
            setEnrolledUI()
        }
    }

    private fun setEnrolledUI() {
        btnEnroll.isEnabled = false
        btnEnroll.text = "Enrolled"
        btnEnroll.icon = ContextCompat.getDrawable(this, android.R.drawable.checkbox_on_background)
        btnEnroll.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success_green))
    }

    private fun refreshStatus() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        val componentName = ComponentName(this, DeviceAdminReceiver::class.java)
        val isAdminActive = dpm.isAdminActive(componentName)
        val isEnrolled = getSharedPreferences("edm_prefs", Context.MODE_PRIVATE).getBoolean("is_enrolled", false)

        tvStatus.text = buildString {
            if (isEnrolled) {
                append("● Device Enrolled\n")
            } else {
                append("○ Enrollment Pending\n")
            }
            // User requested: "change personal device not managed into device owner not activated"
            append(if (isDeviceOwner) "● Managed by Organization\n" else "○ Device Owner Not Activated\n")
            append(if (isAdminActive) "● Admin privileges active" else "○ Admin privileges pending")
        }
    }

    private fun enrollDevice() {
        btnEnroll.isEnabled = false
        btnEnroll.text = "Connecting..."
        lifecycleScope.launch {
            try {
                val deviceUuid = DeviceInfoCollector.getOrCreateUUID(this@MainActivity)
                val response = RetrofitClient.instance.enroll(
                    EnrollRequest(deviceUuid, "MANUAL_ENROLL")
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    // Backend logic: handle "ALREADY_ENROLLED" as success
                    if (body?.status == "ENROLLED" || body?.status == "ALREADY_ENROLLED") {
                        getSharedPreferences("edm_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("is_enrolled", true)
                            .putString("device_id", body.deviceId)
                            .apply()
                        setEnrolledUI()
                        refreshStatus()
                    } else {
                        tvStatus.text = "${tvStatus.text}\n✘ Status: ${body?.status}"
                        btnEnroll.isEnabled = true
                        btnEnroll.text = "Retry"
                    }
                } else {
                    tvStatus.text = "${tvStatus.text}\n✘ Server error: ${response.code()}"
                    btnEnroll.isEnabled = true
                    btnEnroll.text = "Retry"
                }
            } catch (e: Exception) {
                tvStatus.text = "${tvStatus.text}\n✘ Network error"
                btnEnroll.isEnabled = true
                btnEnroll.text = "Retry"
            }
        }
    }

    private fun syncNow() {
        btnSync.isEnabled = false
        btnSync.text = "Syncing..."
        lifecycleScope.launch {
            try {
                val deviceUuid = DeviceInfoCollector.getOrCreateUUID(this@MainActivity)
                val deviceInfo = DeviceInfoCollector.collect(this@MainActivity)
                RetrofitClient.instance.sendDeviceInfo(deviceInfo)

                val apps = AppInventoryCollector.collect(this@MainActivity)
                RetrofitClient.instance.sendAppInventory(AppInventoryRequest(deviceUuid, apps))

                refreshStatus()
                tvStatus.text = "${tvStatus.text}\n✔ Last sync: Just now"
            } catch (e: Exception) { // Fixed: Added type annotation ': Exception'
                tvStatus.text = "${tvStatus.text}\n✘ Sync failed"
            } finally {
                btnSync.isEnabled = true
                btnSync.text = "Sync Now"
            }
        }
    }
}
