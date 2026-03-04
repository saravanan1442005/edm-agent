package com.example.edmagent.network

import com.example.edmagent.data.AppInventoryRequest
import com.example.edmagent.data.DeviceInfoRequest
import com.example.edmagent.data.EnrollRequest
import com.example.edmagent.data.EnrollResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface EdmApiService {

    @POST("api/enroll")
    suspend fun enroll(@Body request: EnrollRequest): Response<EnrollResponse>

    @POST("api/device-info")
    suspend fun sendDeviceInfo(@Body request: DeviceInfoRequest): Response<Map<String, String>>

    @POST("api/app-inventory")
    suspend fun sendAppInventory(@Body request: AppInventoryRequest): Response<Map<String, String>>
}