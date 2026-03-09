package com.example.edmagent.worker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.edmagent.collector.DeviceInfoCollector
import com.example.edmagent.network.RetrofitClient
import com.example.edmagent.receiver.DeviceAdminReceiver
import kotlinx.coroutines.*

object CommandPoller {

    private var pollingJob: Job? = null

    fun start(context: Context) {
        if (pollingJob?.isActive == true) return
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    pollAndExecute(context)
                } catch (e: Exception) {
                    Log.e("CommandPoller", "Polling error", e)
                }
                delay(5000)
            }
        }
        Log.d("CommandPoller", "Polling started")
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        Log.d("CommandPoller", "Polling stopped")
    }

    private suspend fun pollAndExecute(context: Context) {
        val deviceUuid = DeviceInfoCollector.getOrCreateUUID(context)
        val response = RetrofitClient.instance.getPendingCommands(deviceUuid)
        if (!response.isSuccessful) return

        val commands = response.body() ?: return
        if (commands.isEmpty()) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)

        for (command in commands) {
            val id = command["id"] ?: continue
            val type = command["command"] ?: continue

            try {
                when (type) {
                    "LOCK"           -> dpm.lockNow()
                    "DISABLE_CAMERA" -> dpm.setCameraDisabled(admin, true)
                    "ENABLE_CAMERA"  -> dpm.setCameraDisabled(admin, false)
                    "RESET_PASSWORD" -> dpm.resetPassword("", 0)
                    "WIPE"           -> dpm.wipeData(0)
                }
                RetrofitClient.instance.markCommandExecuted(id)
                Log.d("CommandPoller", "Command $type executed")
            } catch (e: SecurityException) {
                Log.e("CommandPoller", "Command $type failed — need Device Owner", e)
            }
        }
    }
}