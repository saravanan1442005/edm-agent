package com.example.edmagent.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.data.EnrollRequest
import com.example.edmagent.network.RetrofitClient
import java.util.concurrent.TimeUnit

class EnrollmentWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val deviceUuid = DeviceInfoCollector.getOrCreateUUID(context)

            // Skip if already enrolled locally
            if (isAlreadyEnrolled(context)) {
                Log.d("EnrollmentWorker", "Already enrolled — skipping")
                return Result.success()
            }

            // 1. Enroll device
            val enrollResponse = RetrofitClient.instance.enroll(
                EnrollRequest(
                    deviceUuid = deviceUuid,
                    enrollmentToken = "QR_PROVISIONED"
                )
            )

            if (enrollResponse.isSuccessful) {
                val body = enrollResponse.body()
                
                val status = body?.status ?: ""
                val deviceId = body?.deviceId ?: ""

                // If already enrolled on backend, treat it as successful enrollment
                if (status == "ENROLLED" || status == "ALREADY_ENROLLED") {
                    Log.d("EnrollmentWorker", "Enrolled successfully (Status: $status): $deviceId")
                    markEnrolled(context, deviceId)

                    // 2. Send device info immediately after enrollment
                    val deviceInfo = DeviceInfoCollector.collect(context)
                    RetrofitClient.instance.sendDeviceInfo(deviceInfo)
                }

                // 3. Schedule periodic sync every 6 hours
                val periodicSync = PeriodicWorkRequestBuilder<DeviceSyncWorker>(
                    6, TimeUnit.HOURS
                ).build()
                
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
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

    private fun isAlreadyEnrolled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("edm_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_enrolled", false)
    }

    private fun markEnrolled(context: Context, deviceId: String) {
        val prefs = context.getSharedPreferences("edm_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_enrolled", true)
            .putString("device_id", deviceId)
            .apply()
    }
}
