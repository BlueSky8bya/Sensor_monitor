package com.gachon_HCI_Lab.user_mobile.common

import android.content.Context
import android.os.Environment
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData
import com.opencsv.CSVWriter
import java.io.*
import java.util.Date

object CsvController {
    private const val TAG = "CsvController"

    // [수정] 문자열 결합 대신 File 생성자 사용하여 경로 오류 방지
    private fun getCsvWriter(directory: File, fileName: String): CSVWriter {
        if (!directory.exists()) directory.mkdirs() // 폴더가 없으면 여기서 한 번 더 생성 보장
        val file = File(directory, fileName)
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
        return CSVWriter(writer)
    }

    // [수정] 공용 다운로드 경로를 안전하게 가져오는 함수
    fun getDownloadPath(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    // [수정] 기존 getExternalPath의 위험한 mkdir() 로직 제거 및 절대 경로 보장
    fun getSensorDirectory(sensorName: String): File {
        val sensorType = sensorName.split("_").getOrNull(0) ?: "Unknown"
        val basePath = getDownloadPath()
        // File(부모, 자식) 구조를 쓰면 중간에 /가 누락되어 'storage' 폴더가 생기는 일을 막을 수 있음
        val dir = File(basePath, "sensor_data/$sensorType")
        if (!dir.exists()) {
            dir.mkdirs() // mkdir() 대신mkdirs() 사용
        }
        return dir
    }

    private fun getTime(): String {
        return (System.currentTimeMillis() / 1000L).toString()
    }

    private fun setFileName(sensorName: String): String {
        return "${sensorName}_${getTime()}.csv"
    }

    // [추가] SensorActivity와 LoginActivity에서 호출하는 함수 정의
    fun deleteFilesInDirectory(dirPath: String) {
        val dir = File(dirPath)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteFilesInDirectory(file.absolutePath)
                } else {
                    file.delete()
                }
            }
            dir.delete() // 필요 시 폴더 자체도 삭제, 파일만 지우려면 이 줄 제외
            Log.d(TAG, "Successfully deleted: $dirPath")
        }
    }

    // [보강] 기존 getExistFileName의 안정성 (orEmpty 처리)
    fun getExistFileName(name: String): String? {
        val sensorType = name.split("_").getOrNull(0) ?: return null
        val directory = File(getDownloadPath(), "sensor_data/$sensorType")
        return directory.listFiles()?.find { it.name.startsWith(name) }?.name
    }

    // 데이터 저장 핵심 로직 (정제 버전)
    fun csvSave(sensorName: String, abstractSensorSet: List<AbstractSensor>) {
        val sensorFolder = getSensorDirectory(sensorName)
        val fileName = setFileName(sensorName)
        val file = File(sensorFolder, fileName)

        // 헤더 설정
        val headerData = when {
            abstractSensorSet.all { it is OneAxisData } -> arrayOf("time", "value")
            abstractSensorSet.all { it is ThreeAxisData } -> arrayOf("time", "x", "y", "z")
            else -> arrayOf("time", "value")
        }

        try {
            // BufferedWriter를 수동으로 닫지 않아도 되는 use 블록 권장 (여기서는 CSVWriter 제약상 기존 유지)
            val writer = getCsvWriter(sensorFolder, fileName)
            try {
                if (file.length() == 0L) {
                    writer.writeNext(headerData)
                    writer.flush()
                }
                for (sensor in abstractSensorSet) {
                    writer.writeNext(convertSensorToStringArray(sensor))
                }
                writer.flush()
            } finally {
                writer.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CSV 저장 실패: ${e.message}")
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

    // 파일 이동 로직 (안정성 보강)
    fun moveFile(sourcePath: String, destinationPath: String) {
        val sourceFile = File(sourcePath)
        val destinationFile = File(destinationPath)

        if (!sourceFile.exists()) return

        destinationFile.parentFile?.mkdirs()

        try {
            // renameTo가 실패할 경우를 대비해 복사 후 삭제
            if (!sourceFile.renameTo(destinationFile)) {
                sourceFile.copyTo(destinationFile, overwrite = true)
                sourceFile.delete()
            }
            Log.d(TAG, "파일 이동 성공: ${destinationFile.name}")
        } catch (e: IOException) {
            Log.e(TAG, "파일 이동 실패: ${e.message}")
        }
    }
}