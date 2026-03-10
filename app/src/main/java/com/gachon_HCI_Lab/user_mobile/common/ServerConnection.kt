package com.gachon_HCI_Lab.user_mobile.common

import android.os.Environment
import android.util.Log
import okhttp3.*
import java.io.*
import java.util.Date

abstract class ServerConnection {
    companion object {
        private val tag = "Server Connection"
        private val requestUrl = "http://114.70.120.121:443/forUser/postCurrentData/"

        /**
         * [수정 포인트] 콜백(onResult) 추가
         * 이유: 비동기 전송 성공 여부를 호출부(Service)에 알려주어 파일 삭제 여부를 결정하기 위함
         */
        fun postFile(file: File, userID: String, battery: String, timestamp: String, onResult: (Boolean) -> Unit) {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("csvfile", file.name, RequestBody.create(MediaType.parse("text/csv"), file))
                .addFormDataPart("userID", userID)
                .addFormDataPart("battery", battery)
                .addFormDataPart("timestamp", timestamp)
                .build()

            val request = Request.Builder().url(requestUrl).post(requestBody).build()
            
            // OkHttpClient를 매번 생성하지 않고 재사용하는 것이 좋지만, 기존 구조 유지를 위해 유지함
            val client = OkHttpClient()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val errorMessage = "Network Error at ${Date()}: ${e.message}"
                    Log.e(tag, errorMessage)
                    saveErrorLog(errorMessage)
                    onResult(false) // 실패 알림
                }

                override fun onResponse(call: Call, response: Response) {
                    // [수정 포인트] response.isSuccessful 체크
                    // 이유: 서버가 404나 500을 응답해도 onResponse가 호출되므로 실제 성공 여부를 확인해야 함
                    if (response.isSuccessful) {
                        Log.d(tag, "전송 성공: ${response.code()}")
                        onResult(true) // 성공 알림
                    } else {
                        val errorMsg = "Server Error (${response.code()}): ${response.message()}"
                        Log.e(tag, errorMsg)
                        saveErrorLog(errorMsg)
                        onResult(false) // 실패 알림
                    }
                    // [수정 포인트] response.close()
                    // 이유: 스트림을 닫지 않으면 메모리 누수 및 소켓 부족 현상이 발생할 수 있음
                    response.close()
                }
            })
        }

        fun saveErrorLog(errorMessage: String) {
            val logFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Error_Log.txt")
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.append("${Date()}: $errorMessage\n")
                }
            } catch (e: IOException) {
                Log.e("FileLogger", "Error writing log file: ${e.message}")
            }
        }
    }
}