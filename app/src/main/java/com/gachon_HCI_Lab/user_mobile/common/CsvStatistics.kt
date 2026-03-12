package com.gachon_HCI_Lab.user_mobile.common

import android.content.ContentValues
import android.util.Log
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

object CsvStatistics {
    fun makeMean(file: File, name: String = "") {
        // use 블록을 사용하여 예외 발생 시에도 자동으로 스트림을 닫도록 변경
        val fileReader = CSVReader(InputStreamReader(file.inputStream()))
        val fileList = try {
            fileReader.use { it.readAll() }
        } catch (e: Exception) {
            Log.e("CsvStatistics", "Read error: ${e.message}")
            return
        }

        if (fileList.size <= 1) return // 헤더만 있거나 빈 파일이면 처리 안 함

        val dataRows = fileList.drop(1) // 헤더 제외한 데이터만 추출
        var valueSum = 0.0
        var validCount = 0

        for (row in dataRows) {
            // row[1]이 존재하는지 확인 후 변환
            val value = row.getOrNull(1)?.toDoubleOrNull()
            if (value != null) {
                valueSum += value
                validCount++
            }
        }

        if (validCount == 0) return
        val valueMean = valueSum / validCount
        
        val statsDir = File(CsvController.getDownloadPath(), "sensor_data/statistics")
        if (!statsDir.exists()) statsDir.mkdirs()

        val toName = file.name.split("_").getOrNull(0) + "_mean.csv"
        val toFile = File(statsDir, toName)
        
        // CSVWriter 또한 닫기 처리가 중요함
        try {
            val csvWriter = CSVWriter(FileWriter(toFile, true))
            csvWriter.use { writer ->
                val time = file.name.split("_").getOrNull(1)?.split(".")?.get(0) ?: "unknown"
                writer.writeNext(arrayOf(time, valueMean.toString()))
            }
        } catch (e: Exception) {
            Log.e("CsvStatistics", "Write error: ${e.toString()}")
        }
    }
}