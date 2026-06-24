package com.example.smstopc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object EmailSender {

    private const val NTFY_URL = "https://ntfy.sh"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun send(
        ntfyTopic: String = AppPreferences.ntfyTopic,
        smsSender: String,
        smsContent: String,
        verificationCode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val innerJson = JSONObject().apply {
                put("sender", smsSender)
                put("code", verificationCode)
                put("content", smsContent)
            }.toString()

            val json = JSONObject().apply {
                put("topic", ntfyTopic)
                put("title", "验证码: $verificationCode")
                put("message", innerJson)
                put("priority", 4)
                put("tags", JSONArray().apply { put("key") })
            }

            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(NTFY_URL).post(body).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val id = JSONObject(responseBody).optString("id", "")
                if (id.isNotBlank()) Result.success(Unit)
                else Result.failure(Exception("ntfy.sh 返回异常: $responseBody"))
            } else {
                Result.failure(Exception("ntfy.sh HTTP ${response.code}: $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("ntfy.sh 发送失败: ${e.message}"))
        }
    }
}
