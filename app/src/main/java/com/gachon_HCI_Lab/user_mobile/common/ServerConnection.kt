package com.gachon_HCI_Lab.user_mobile.common

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.activity.SensorActivity
import okhttp3.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class ServerConnection {
    companion object {
        private const val TAG = "Server Connection"
        private const val REQUEST_URL = "http://114.70.120.121:443/forUser/postCurrentData/"
        // [중요] 로그인 주소가 반드시 정의되어 있어야 합니다.
        private const val LOGIN_URL = "http://114.70.120.121:443/forUser/registUser/"

        // 클라이언트를 싱글톤으로 관리 (메모리 효율)
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * [login] LoginActivity에서 호출하는 함수
         */
        fun login(authcode: String, deviceID: String = "123456", regID: String = "1234567", context: Activity) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val strDate: String = dateFormat.format(Date())

            val httpBuilder = HttpUrl.parse(LOGIN_URL)?.newBuilder()?.apply {
                addQueryParameter("userID", authcode)
                addQueryParameter("deviceID", deviceID)
                addQueryParameter("regID", regID)
                addQueryParameter("timestamp", strDate)
            }

            if (httpBuilder == null) {
                Log.e(TAG, "URL 생성 실패")
                return
            }

            val request = Request.Builder()
                .url(httpBuilder.build())
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Login 실패: ${e.message}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "네트워크 연결 실패", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code() == 200) {
                        Log.d(TAG, "Login 성공")
                        CacheManager.saveCacheFile(context, authcode, "login.txt")

                        val intent = Intent(context, SensorActivity::class.java).apply {
                            putExtra("ID", authcode)
                        }
                        context.startActivity(intent)
                        context.finish()
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            android.widget.Toast.makeText(context, "로그인 실패 (코드: ${response.code()})", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    response.close()
                }
            })
        }

        /**
         * [postFile] AcceptService에서 호출하는 함수
         */
        fun postFile(file: File, userID: String, battery: String, timestamp: String, onResult: (Boolean) -> Unit) {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("csvfile", file.name, RequestBody.create(MediaType.parse("text/csv"), file))
                .addFormDataPart("userID", userID)
                .addFormDataPart("battery", battery)
                .addFormDataPart("timestamp", timestamp)
                .build()

            val request = Request.Builder().url(REQUEST_URL).post(requestBody).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val errorMessage = "Network Error: ${e.message}"
                    Log.e(TAG, errorMessage)
                    saveErrorLog(errorMessage)
                    onResult(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "전송 성공: ${response.code()}")
                        onResult(true)
                    } else {
                        saveErrorLog("Server Error: ${response.code()}")
                        onResult(false)
                    }
                    response.close()
                }
            })
        }

        fun saveErrorLog(errorMessage: String) {
            CsvController.writeLog("SERVER_ERROR: $errorMessage")
        }
    }
}