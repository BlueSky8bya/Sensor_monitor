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
import java.util.TimeZone

object CsvController {
    private const val TAG = "CsvController"

    // 직전 쓰기 실패로 디스크에 못 남긴 로그 줄을 임시 보관(메모리). 스토리지 복구 시 다음 호출에서 함께 flush.
    private val pendingLogs = ArrayDeque<String>()
    private const val MAX_PENDING = 500

    // 로그/파일명 시각은 기기 시간대와 무관하게 항상 한국시간(KST)으로 기록.
    // 주의: 서버 전송 timestamp는 epoch(System.currentTimeMillis)라 시간대 영향 없음 — 별개.
    val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

    // KST 고정 SimpleDateFormat 생성 헬퍼.
    fun kstFormat(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = KST }

    /**
     * 앱의 주요 이벤트나 에러를 파일로 남기는 함수.
     *
     * 파일관리자로 폴더를 수동 삭제하면 scoped storage 색인이 desync되어
     * 기존 파일명 append가 EEXIST(open failed)로 실패할 수 있다. 이를 견고하게 처리:
     *  1) 정상 경로(Download/sensor_data/logs)에 append 시도
     *  2) EEXIST류 실패 시 손상 핸들을 delete 후 재생성하여 1회 재시도
     *  3) 그래도 실패하면 해당 줄을 메모리 버퍼에 보관 → 스토리지 복구 후 다음 호출에서 함께 기록
     * 덕분에 위치는 그대로 두면서, 삭제 직후 연결 시점 로그도 유실되지 않는다.
     */
    @Synchronized
    fun writeLog(message: String) {
        val timestamp = kstFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        pendingLogs.addLast("[$timestamp] $message")

        try {
            val logDir = File(getDownloadPath(), "sensor_data/logs")
            if (!logDir.exists() && !logDir.mkdirs()) {
                Log.e(TAG, "Log directory creation failed")
                return // 줄은 pendingLogs에 남아 다음 호출에서 재시도
            }

            // 로그 파일명 날짜 포맷 yyMMdd (예: app_debug_log_260319.txt)
            val logFile = File(logDir, "app_debug_log_${kstFormat("yyMMdd").format(Date())}.txt")

            try {
                flushPending(logFile)
            } catch (e: Exception) {
                // 수동 삭제 등으로 인한 색인 desync(EEXIST) 복구: 손상 핸들 제거 후 재시도.
                val msg = (e.message ?: "") + (e.cause?.message ?: "")
                if (msg.contains("EEXIST")) {
                    runCatching { logFile.delete() }
                    flushPending(logFile) // 재시도 실패 시 아래 catch로
                } else {
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Log file write failed: ${e.message}", e)
            // 메모리 무한 증가 방지: 오래된 줄부터 버림
            while (pendingLogs.size > MAX_PENDING) pendingLogs.removeFirst()
        }
    }

    // pendingLogs를 파일에 append하고 성공 시 비운다. 실패하면 예외를 던져 호출부가 복구하도록 한다.
    private fun flushPending(logFile: File) {
        FileWriter(logFile, true).buffered().use { writer ->
            for (line in pendingLogs) writer.appendLine(line)
        }
        pendingLogs.clear()
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
     * 파편 파일명 생성 로직
     * yyyyMMdd -> yyMMdd로 변경하여 병합 로직과 일관성 유지
     * (예: Accelerometer_260319_204501.csv)
     */
    private fun setFileName(sensorName: String): String {
        val sdf = kstFormat("yyMMdd_HHmmss")
        val date = sdf.format(Date())
        return "${sensorName}_${date}.csv"
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