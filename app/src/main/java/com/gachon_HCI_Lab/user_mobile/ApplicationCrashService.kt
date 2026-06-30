package com.gachon_HCI_Lab.user_mobile;

import android.app.Application
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class ApplicationCrashService : Application() {

    override fun onCreate() {
        super.onCreate()

        // 글로벌 예외 처리기 설정
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 강제 종료 이유 기록
            val errorDetails = StringWriter().apply {
                PrintWriter(this).use { throwable.printStackTrace(it) }
            }.toString()

            // 로그 파일에 기록
            logError(
                """
                Uncaught exception in thread: ${thread.name}
                Message: ${throwable.localizedMessage}
                Stack Trace: 
                $errorDetails
                """.trimIndent()
            )

            // 앱 강제 종료
            android.os.Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    private fun logError(message: String) {
        // 로그 파일 경로 설정 (다운로드 폴더에 로그 파일을 저장)
        val path = File(filesDir, "Error_Log.txt")
        try {
            val writer = FileWriter(path, true)
            writer.appendLine(message)
            writer.close()
        } catch (e: IOException) {
            Log.e("ApplicationCrashService", "Error writing log: ${e.message}")
        }
    }
}
