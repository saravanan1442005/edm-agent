package com.example.edmagent.collector

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.example.edmagent.data.DeviceInfoRequest
import java.util.UUID

object DeviceInfoCollector {

    fun collect(context: Context): DeviceInfoRequest {
        return DeviceInfoRequest(
            deviceUuid = getOrCreateUUID(context),
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            osVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            serialNumber = getSerial(),
            imei = getImeiSafely(context)
        )
    }

    private fun getSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: SecurityException) {
            Log.w("DeviceInfoCollector", "Serial restricted: ${e.message}")
            "RESTRICTED"
        }
    }

    private fun getImeiSafely(context: Context): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.imei
            } else {
                @Suppress("DEPRECATION")
                tm.deviceId
            }
        } catch (e: SecurityException) {
            Log.w("DeviceInfoCollector", "IMEI restricted: ${e.message}")
            null
        }
    }

    fun getOrCreateUUID(context: Context): String {
        val prefs = context.getSharedPreferences("edm_prefs", Context.MODE_PRIVATE)
        return prefs.getString("device_uuid", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_uuid", it).apply()
        }
    }
}