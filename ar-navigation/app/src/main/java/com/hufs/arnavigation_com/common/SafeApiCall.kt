package com.scanpang.arnavigation.common

import retrofit2.HttpException
import java.io.IOException

suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> {
    return try {
        val response = apiCall()
        NetworkResult.Success(response)
    } catch (e: HttpException) {
        val errorBodyString = e.response()?.errorBody()?.string()
        android.util.Log.e("AR_NAV_TEST", "HTTP 403 진짜 원인: $errorBodyString")

        NetworkResult.Error(code = e.code(), message = errorBodyString ?: e.message())
    } catch (e: IOException) {
        NetworkResult.Error(code = -1, message = "Network connection failed. Please check your internet.")
    } catch (e: Exception) {
        NetworkResult.Error(code = -2, message = e.localizedMessage ?: "Unknown Error Occurred")
    }
}