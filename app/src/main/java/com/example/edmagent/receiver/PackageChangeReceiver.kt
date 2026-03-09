package com.example.edmagent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.edmagent.worker.DeviceSyncWorker

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                Log.d("PackageReceiver", "App installed: $packageName")
                triggerSync(context)
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                Log.d("PackageReceiver", "App removed: $packageName")
                triggerSync(context)
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d("PackageReceiver", "App updated: $packageName")
                triggerSync(context)
            }
        }
    }

    private fun triggerSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<DeviceSyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
        Log.d("PackageReceiver", "Sync triggered")
    }
}