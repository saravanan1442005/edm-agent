package com.example.edmagent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.edmagent.collector.AppInventoryCollector
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.data.AppInventoryRequest
import com.example.edmagent.network.RetrofitClient

class DeviceSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val deviceUuid = DeviceInfoCollector.getOrCreateUUID(context)

            // 1. Send device info
            val deviceInfo = DeviceInfoCollector.collect(context)
            val deviceInfoResponse = RetrofitClient.instance.sendDeviceInfo(deviceInfo)
            if (deviceInfoResponse.isSuccessful) {
                Log.d("DeviceSyncWorker", "Device info sent successfully")
            } else {
                Log.w("DeviceSyncWorker", "Device info failed: ${deviceInfoResponse.code()}")
            }

            // 2. Send app inventory
            val apps = AppInventoryCollector.collect(context)
            val inventoryRequest = AppInventoryRequest(
                deviceUuid = deviceUuid,
                apps = apps
            )
            val inventoryResponse = RetrofitClient.instance.sendAppInventory(inventoryRequest)
            if (inventoryResponse.isSuccessful) {
                Log.d("DeviceSyncWorker", "App inventory sent: ${apps.size} apps")
            } else {
                Log.w("DeviceSyncWorker", "Inventory failed: ${inventoryResponse.code()}")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DeviceSyncWorker", "Sync failed: ${e.message}")
            Result.retry()
        }
    }
}