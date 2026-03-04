package com.example.edmagent.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.worker.EnrollmentWorker

class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.d("DeviceAdminReceiver", "Device Admin Enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.d("DeviceAdminReceiver", "Device Admin Disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.d("DeviceAdminReceiver", "Provisioning Complete - starting enrollment")
        val request = OneTimeWorkRequestBuilder<EnrollmentWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}