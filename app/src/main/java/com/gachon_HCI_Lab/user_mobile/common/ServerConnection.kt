package com.gachon_HCI_Lab.user_mobile.common

import android.app.Activity
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.gachon_HCI_Lab.user_mobile.activity.SensorActivity
import com.gachon_HCI_Lab.user_mobile.common.ServerConnection.Companion.postFile
import okhttp3.*
import java.io.*
import java.util.Date
import java.util.Locale


/** 서버와의 통신을 위한 싱글톤 클래스
 postFile, Login기능을 제공한다.
 [postFile] : 서버에 csv파일을 전송한다.
 [Login] : 서버에 로그인 요청을 보내고, 성공시 sensorActivity로 이동한다.*/
abstract class ServerConnection{
    companion object{
        // Private 변수 선언
        private val tag = "Server Connection"
        private val requestUrl = "http://114.70.120.121:443/forUser/postCurrentData/"
        private val loginURL = "http://114.70.120.121:443/forUser/registUser/"

         /**
         [postFile]
         서버에 csv파일을 전송한다.
         file: 전송할 csv파일
         userID: 로그인되어있는 유저ID
         battery: 현재 워치 배터리 잔량
         timestamp: 현재 시간(파일명에 들어간 시간과 동일)
         url: 서버 주소(default값이 지정되어있으므로, 다른 경로에 전송할때만 사용)*/
//        fun postFile(file: File, userID: String, battery: String, timestamp: String, url: String = requestUrl) {
//            //Post에 붙일 요청 body생성부분
//            val requestBody = MultipartBody.Builder()
//                .setType(MultipartBody.FORM)
//                .addFormDataPart("csvfile", file.name, RequestBody.create(MediaType.parse("text/csv"), file))
//                .addFormDataPart("userID", userID)
//                .addFormDataPart("battery", battery)
//                .addFormDataPart("timestamp", timestamp)
//                .build()
//
//            //Post요청 생성
//            val request = Request.Builder()
//                .url(url)
//                .post(requestBody)
//                .build()
//            //전송
//            val client = OkHttpClient()
//            client.newCall(request).enqueue(object : Callback {
//                override fun onFailure(call: Call, e: IOException) {
//                    Log.e(tag,e.printStackTrace().toString())
//                }
//
//                override fun onResponse(call: Call, response: Response) {
//                    Log.d(tag, "File Send response code: " + response.body()?.string().toString())
//                }
//            })
//        }

         fun postFile(file: File, userID: String, battery: String, timestamp: String, url: String = requestUrl) {
             // Post에 붙일 요청 body 생성 부분
             val requestBody = MultipartBody.Builder()
                 .setType(MultipartBody.FORM)
                 .addFormDataPart("csvfile", file.name, RequestBody.create(MediaType.parse("text/csv"), file))
                 .addFormDataPart("userID", userID)
                 .addFormDataPart("battery", battery)
                 .addFormDataPart("timestamp", timestamp)
                 .build()

             // Post 요청 생성
             val request = Request.Builder()
                 .url(url)
                 .post(requestBody)
                 .build()

             // 전송
             val client = OkHttpClient()
             client.newCall(request).enqueue(object : Callback {
                 override fun onFailure(call: Call, e: IOException) {
                     // 오류 메시지 로그 기록
                     val errorMessage = "File Send failed at ${Date()}: ${e.message}"
                     Log.e(tag, errorMessage)

                     // 오류 로그 파일에 기록
                     saveErrorLog(errorMessage)
                 }

                 override fun onResponse(call: Call, response: Response) {
                     // 응답 코드 로그 기록
                     val responseBody = response.body()?.string().orEmpty()
                     Log.d(tag, "File Send response code: $responseBody")
                 }
             })
         }

        fun saveErrorLog(errorMessage: Comparable<*>) {
            val logFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Error_Log.txt")
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.append("$errorMessage\n")
                }
            } catch (e: IOException) {
                Log.e("FileLogger", "Error writing log file: ${e.message}", e)
            }
        }

        /**
       * [login]
         서버에 로그인 요청을 보내고, 성공시 sensorActivity로 이동한다.
         authcode: 로그인할 유저의 ID
         deviceID: 현재 디바이스의 ID
         regID: 현재 디바이스의 regID
         context: 현재 액티비티*/
        fun login(authcode : String, deviceID : String = "123456", regID: String = "1234567", context: Activity) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = Date()
            val strDate: String = dateFormat.format(date)

            val client = OkHttpClient()

            val httpBuilder = HttpUrl.parse(loginURL)?.newBuilder()
            if (httpBuilder != null) {
                httpBuilder.addQueryParameter("userID", authcode)
                httpBuilder.addQueryParameter("deviceID", deviceID)
                httpBuilder.addQueryParameter("regID", regID)
                httpBuilder.addQueryParameter("timestamp",strDate)
            }

            val request = Request.Builder()
                .url(httpBuilder!!.build())
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(tag,e.printStackTrace().toString())
                    val handler = android.os.Handler(Looper.getMainLooper())
                    handler.postDelayed(Runnable {
                        Toast.makeText(
                            context,
                            "Login failed!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }, 0)
                }

                override fun onResponse(call: Call, response: Response) {
                   Log.d(tag, "Login response code: "+response.code().toString())
                    if(response.code().toString() == "200"){
                        CacheManager.saveCacheFile(context, authcode, "login.txt")

                        val intent = Intent(context, SensorActivity::class.java)
                        intent.putExtra("ID", authcode)
                        intent.putExtra("DeviceID", deviceID)
                        context.startActivity(intent)
                        context.finish()
                    }
                    else{
                        val handler = android.os.Handler(Looper.getMainLooper())
                        handler.postDelayed(Runnable {
                            Toast.makeText(
                                context,
                                "Login failed!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }, 0)
                    }

                }
            })
        }

    }

}