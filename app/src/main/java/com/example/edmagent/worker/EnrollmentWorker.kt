package com.example.edmagent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.data.EnrollRequest
import com.example.edmagent.network.RetrofitClient
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder

class EnrollmentWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val deviceUuid = DeviceInfoCollector.getOrCreateUUID(context)

            // 1. Enroll device
            val enrollResponse = RetrofitClient.instance.enroll(
                EnrollRequest(
                    deviceUuid = deviceUuid,
                    enrollmentToken = "QR_PROVISIONED"
                )
            )

            if (enrollResponse.isSuccessful) {
                Log.d("EnrollmentWorker", "Enrolled: ${enrollResponse.body()?.deviceId}")

                // 2. Send device info immediately after enrollment
                val deviceInfo = DeviceInfoCollector.collect(context)
                RetrofitClient.instance.sendDeviceInfo(deviceInfo)

                // 3. Schedule periodic sync every 6 hours
                val periodicSync = PeriodicWorkRequestBuilder<DeviceSyncWorker>(
                    6, TimeUnit.HOURS
                ).build()
                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "edm_sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicSync
                )

                Result.success()
            } else {
                Log.w("EnrollmentWorker", "Enrollment failed: ${enrollResponse.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("EnrollmentWorker", "Error: ${e.message}")
            Result.retry()
        }
    }
}