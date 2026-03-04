package com.example.edmagent.data

data class EnrollRequest(
    val deviceUuid: String,
    val enrollmentToken: String
)

data class EnrollResponse(
    val status: String,
    val deviceId: String
)

data class DeviceInfoRequest(
    val deviceUuid: String,
    val model: String,
    val manufacturer: String,
    val osVersion: String,
    val sdkVersion: Int,
    val serialNumber: String,
    val imei: String? = null
)

data class AppInfoDto(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val installSource: String,
    val isSystemApp: Boolean
)

data class AppInventoryRequest(
    val deviceUuid: String,
    val apps: List<AppInfoDto>
)