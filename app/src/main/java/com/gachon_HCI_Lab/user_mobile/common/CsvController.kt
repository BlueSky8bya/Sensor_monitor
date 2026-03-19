package com.gachon_HCI_Lab.user_mobile.common

import android.os.Environment
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData
import com.opencsv.CSVWriter
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvController {
    private const val TAG = "CsvController"

    /**
     * 앱의 주요 이벤트나 에러를 파일로 남기는 함수
     */
    fun writeLog(message: String) {
        try {
            val basePath = getDownloadPath()
            val logDir = File(basePath, "sensor_data/logs")

            if (!logDir.exists()) {
                val created = logDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Log directory creation failed")
                    return
                }
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timestamp = sdf.format(Date())

            // [수정] 로그 파일명 날짜 포맷도 yyMMdd로 통일 (예: app_debug_log_260319.txt)
            val dateStr = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())
            val fileName = "app_debug_log_${dateStr}.txt"
            val logFile = File(logDir, fileName)

            FileWriter(logFile, true).buffered().use { writer ->
                writer.appendLine("[$timestamp] $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Log file write failed: ${e.message}", e)
        }
    }

    fun getDownloadPath(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun getSensorDirectory(sensorName: String): File {
        val sensorType = sensorName.split("_").getOrNull(0) ?: "Unknown"
        val basePath = getDownloadPath()
        val sensorDataDir = File(basePath, "sensor_data")
        if (!sensorDataDir.exists()) sensorDataDir.mkdirs()

        val dir = File(sensorDataDir, sensorType)
        if (!dir.exists()) {
            dir.mkdirs()
            writeLog("DIRECTORY_CREATED: $sensorType")
        }
        return dir
    }

    /**
     * [수정] 파편 파일명 생성 로직
     * yyyyMMdd -> yyMMdd로 변경하여 병합 로직과 일관성 유지
     * (예: Accelerometer_260319_204501.csv)
     */
    private fun setFileName(sensorName: String): String {
        val sdf = SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault())
        val date = sdf.format(Date())
        return "${sensorName}_${date}.csv"
    }

    fun deleteFilesInDirectory(dirPath: String) {
        Log.w(TAG, "WARNING: deleteFilesInDirectory 호출됨 (무시됨) - 경로: $dirPath")
        writeLog("누가 지우려고 해요: $dirPath")
    }

    fun csvSave(sensorName: String, abstractSensorSet: List<AbstractSensor>) {
        val sensorFolder = getSensorDirectory(sensorName)
        val fileName = setFileName(sensorName)
        val file = File(sensorFolder, fileName)

        val headerData = when {
            abstractSensorSet.all { it is OneAxisData } -> arrayOf("time", "value")
            abstractSensorSet.all { it is ThreeAxisData } -> arrayOf("time", "x", "y", "z")
            else -> arrayOf("time", "value")
        }

        try {
            // UTF-8 BOM 추가 없이 순수 UTF-8로 저장 (분석 툴 호환성 고려)
            val writer = CSVWriter(BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), "UTF-8")))

            writer.use { w ->
                if (file.length() == 0L) {
                    w.writeNext(headerData)
                }

                for (sensor in abstractSensorSet) {
                    w.writeNext(convertSensorToStringArray(sensor))
                }
                w.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CSV 저장 실패: ${e.message}")
            writeLog("ERROR: CSV 저장 실패 ($sensorName) - ${e.message}")
        }
    }

    private fun convertSensorToStringArray(abstractSensor: AbstractSensor): Array<String> {
        val time = abstractSensor.time.toString()
        return when (abstractSensor) {
            is OneAxisData -> arrayOf(time, abstractSensor.value.toString())
            is ThreeAxisData -> arrayOf(time, abstractSensor.xValue.toString(), abstractSensor.yValue.toString(), abstractSensor.zValue.toString())
            else -> emptyArray()
        }
    }
}