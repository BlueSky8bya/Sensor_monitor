package com.gachon_HCI_Lab.user_mobile.common

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Environment
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.*

/**
 * csv 관련 처리 객체
 * */
object CsvController {

    // csv Writer, Reader 이름을 입력해야 함
    fun getCSVReader(path: String, fileName: String): CSVReader{
        return CSVReader(FileReader("$path/$fileName"))
    }

    private fun getCsvWriter(path: String, fileName: String): CSVWriter {
        val file = File(path, fileName)
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
        return CSVWriter(writer)
    }

    // 파일이 저장될 외부 저장소 path 불러오는 함수
    // 파일이 저장될 path 리턴
    fun getExternalPath(context: Context): String{
        val dir: File? = context.getExternalFilesDir(null)
        val path = dir?.absolutePath + File.separator + "sensor"

        // 외부 저장소 경로가 있는지 확인, 없으면 생성
        val file: File = File(path)
        if (!file.exists()) {
            file.mkdir()
        }
        return path
    }

    fun getExternalPath(context: Context, dirName: String): String {
        val dir: File? = context.getExternalFilesDir(null)
        val path = dir?.absolutePath + File.separator + dirName

        // 외부 저장소 경로가 있는지 확인, 없으면 생성
        val file: File = File(path)
        if (!file.exists()) {
            file.mkdir()
        }
        return path
    }

    // 파일 존재 확인 함수
    // 센서명을 인풋으로 넣는다
    // 존재하면 파일명, 없으면 null 리턴
    fun fileExist(context: Context, name: String): String? {
        val path: String = getExternalPath(context)
        val directory: File = File(path)

        if (directory.exists()) {
            val files: Array<out File>? = directory.listFiles()

            for (file in files!!) {
                if (file.name.contains(name)) {
                    return file.name
                }
            }
        }
        return null
    }

    // 현재 날짜 시간을 리턴하는 메소드
    // 형태: "yyyy-MM-dd_HH:mm:ss"
    private fun getTime(): String {
        return (System.currentTimeMillis() / 1000L).toString()
    }

    private fun setFileName(sensorName: String): String {
        return sensorName + "_" + getTime() + ".csv"
    }


    fun getExistFileName(context: Context, name: String): String? {
        val sensorType = name.split("_").getOrNull(0) ?: return null

        // 📌 공용 다운로드 경로 기준 (csvFirst, sendCSV와 동일하게 맞춤)
        val basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val directory = File("$basePath/sensor_data/$sensorType")

        if (directory.exists()) {
            val files: Array<out File>? = directory.listFiles()

            for (file in files.orEmpty()) {
                // 정확도 향상을 위해 startsWith 사용
                if (file.name.startsWith(name)) {
                    return file.name
                }
            }
        }
        return null
    }


    //디바이스의 센서_unixtime.csv파일명 가져오기
    //unixtime은 알 수 없기 때문에 파일명을 알아내기 위해 사용
//    fun getExistFileName(context: Context, name: String): String? {
//        val path: String = getExternalPath(context, "sensor")
//        val directory: File = File(path)
//
//        if (directory.exists()) {
//            val files: Array<out File>? = directory.listFiles()
//
//            for (file in files!!) {
//                if (file.name.contains(name)) {
//                    return file.name
//                }
//            }
//        }
//        return null
//    }

    fun getDownloadPath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    /**
     * csv 파일이 존재하지 않는다면 실행되는 메소드
     * 입력받은 sensorName을 활용하여 파일명 작성
     * */

    fun csvFirst(context: Context, sensorName: String, abstractSensorSet: List<AbstractSensor>): String? {
        val sensorType = sensorName.split("_").getOrNull(0) ?: "UnknownSensor"

        // 공용 다운로드 폴더 경로 (sendCSV()와 동일하게 통일)
        val basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        val sensorPath = "$basePath/sensor_data/$sensorType"

        // 디렉토리 생성
        File(sensorPath).mkdirs()

        val newName = setFileName(sensorName)
        val filePath = "$sensorPath/$newName"
        val file = File(filePath)

        val existingFileName = getExistFileName(context, sensorName)

        if (existingFileName == null || existingFileName != newName) {
            val csvWriter = getCsvWriter(sensorPath, newName)

            try {
                if (file.length() == 0L) {
                    val headerData = when {
                        abstractSensorSet.all { it is OneAxisData } -> arrayOf("time", "value")
                        abstractSensorSet.all { it is ThreeAxisData } -> arrayOf("time", "x", "y", "z")
                        else -> arrayOf("time", "value")
                    }
                    csvWriter.writeNext(headerData)
                    csvWriter.flush()
                    Log.d(this.toString(), "헤더 작성 완료: ${headerData.joinToString()}")
                }

                for (sensor in abstractSensorSet) {
                    val data: Array<String> = convertSensorToStringArray(sensor)
                    csvWriter.writeNext(data)
                    Log.d(this.toString(), "데이터 작성 완료: ${data.joinToString()}")
                }
                csvWriter.flush()
            } catch (e: Exception) {
                Log.e(TAG, "CSV 작성 중 오류: ${e.message}", e)
                return null
            } finally {
                try {
                    csvWriter.close()
                } catch (e: IOException) {
                    Log.e(TAG, "CSVWriter 닫기 중 오류: ${e.message}", e)
                }
            }

            Log.d(this.toString(), "CSV 저장 완료: $filePath")
        }

        return filePath
    }

//    fun csvFirst(context: Context, sensorName: String, abstractSensorSet: List<AbstractSensor>): String? {
//        val sensorType = sensorName.split("_").getOrNull(0) ?: "UnknownSensor"
//
//        // 내부 저장소 (다운로드 폴더) + 센서별 폴더
//        val internalBasePath = getDownloadPath()
//        val internalPath = "$internalBasePath/sensor_data/$sensorType"
//
//        // 외부 저장소 (앱 전용) + 센서별 폴더
//        val externalBasePath = getExternalPath(context)
//        val externalPath = "$externalBasePath/sensor_data/$sensorType"
//
//        // 디렉토리 생성
//        File(internalPath).mkdirs()
//        File(externalPath).mkdirs()
//
//        val newName = setFileName(sensorName)
//        val externalFile = File(externalPath, newName)
//
//        // 최근 파일명을 가져옵니다.
//        val existingFileName = getExistFileName(context, sensorName)
//
//        // 기존 파일명과 새로 생성할 파일명이 다르면 새 파일을 생성합니다.
//        if (existingFileName == null || existingFileName != newName) {
//            val externalCsvWriter = getCsvWriter(externalPath, newName)
//            val internalCsvWriter = getCsvWriter(internalPath, newName)
//
//            try {
//                // 외부 저장소와 내부 다운로드 폴더에서 파일이 비어 있는 경우 헤더 작성
//                if (externalFile.length() == 0L) {
//                    val headerData = when {
//                        abstractSensorSet.all { it is OneAxisData } -> {
//                            arrayOf("time", "value")
//                        }
//                        abstractSensorSet.all { it is ThreeAxisData } -> {
//                            arrayOf("time", "x", "y", "z")
//                        }
//                        else -> {
//                            arrayOf("time", "value")
//                        }
//                    }
//                    externalCsvWriter.writeNext(headerData)
//                    internalCsvWriter.writeNext(headerData)
//                    Log.d(this.toString(), "헤더 작성 완료: ${headerData.joinToString()}")
//                    externalCsvWriter.flush()
//                    internalCsvWriter.flush()
//                }
//
//                // 데이터 기록
//                for (sensor in abstractSensorSet) {
//                    val data: Array<String> = convertSensorToStringArray(sensor)
//                    externalCsvWriter.writeNext(data)
//                    internalCsvWriter.writeNext(data)
//                    Log.d(this.toString(), "데이터 작성 완료: ${data.joinToString()}")
//                }
//                externalCsvWriter.flush()
//                internalCsvWriter.flush()
//            } catch (e: Exception) {
//                Log.e(TAG, "CSV 생성 및 작성 중 오류: ${e.message}", e)
//                return null
//            } finally {
//                try {
//                    externalCsvWriter.close()
//                    internalCsvWriter.close()
//                } catch (e: IOException) {
//                    Log.e(TAG, "CSVWriter 닫기 중 오류: ${e.message}", e)
//                }
//            }
//
//            Log.d(this.toString(), "CSV 생성 및 저장 완료")
//        }
//
//        return "$externalPath/$newName" // 외부 경로 반환
//    }


//    fun csvFirst(context: Context, sensorName: String, abstractSensorSet: List<AbstractSensor>): String? {
//        val internalPath = getDownloadPath() // 문자열 경로 사용
//        val externalPath: String = getExternalPath(context)
//        val newName = setFileName(sensorName)
//
//        val externalFile = File(externalPath, newName)
//
//        // 최근 파일명을 가져옵니다.
//        val existingFileName = getExistFileName(context, sensorName)
//
//        // 기존 파일명과 새로 생성할 파일명이 다르면 새 파일을 생성합니다.
//        if (existingFileName == null || existingFileName != newName) {
//            val externalCsvWriter = getCsvWriter(externalPath, newName)
//            val internalCsvWriter = getCsvWriter(internalPath, newName)
//
//            try {
//                // 외부 저장소와 내부 다운로드 폴더에서 파일이 비어 있는 경우 헤더 작성
//                if (externalFile.length() == 0L) {
//                    val headerData = when {
//                        abstractSensorSet.all { it is OneAxisData } -> {
//                            arrayOf("time", "value")
//                        }
//                        abstractSensorSet.all { it is ThreeAxisData } -> {
//                            arrayOf("time", "x", "y", "z")
//                        }
//                        else -> {
//                            arrayOf("time", "value")
//                        }
//                    }
//                    externalCsvWriter.writeNext(headerData)
//                    internalCsvWriter.writeNext(headerData)
//                    Log.d(this.toString(), "헤더 작성 완료: ${headerData.joinToString()}")
//                    externalCsvWriter.flush()
//                    internalCsvWriter.flush()
//                }
//
//                // 데이터 기록
//                for (sensor in abstractSensorSet) {
//                    val data: Array<String> = convertSensorToStringArray(sensor)
//                    externalCsvWriter.writeNext(data)
//                    internalCsvWriter.writeNext(data)
//                    Log.d(this.toString(), "데이터 작성 완료: ${data.joinToString()}")
//                }
//                externalCsvWriter.flush()
//                internalCsvWriter.flush()
//            } catch (e: Exception) {
//                Log.e(TAG, "CSV 생성 및 작성 중 오류: ${e.message}", e)
//                return null
//            } finally {
//                try {
//                    externalCsvWriter.close()
//                    internalCsvWriter.close()
//                } catch (e: IOException) {
//                    Log.e(TAG, "CSVWriter 닫기 중 오류: ${e.message}", e)
//                }
//            }
//
//            Log.d(this.toString(), "CSV 생성 및 저장 완료")
//        }
//
//        return "$externalPath/$newName" // 파일 경로를 반환
//    }


    /**
     * csv 파일이 존재한 경우, 파일에 csv 데이터를 작성하는 메소드
     * sensorName: 해당하는 센서 이름
     * abstractSensorSet: 센서 List
     * */

//    fun csvSave(context: Context, sensorName: String, abstractSensorSet: List<AbstractSensor>) {
//        val externalBasePath = getExternalPath(context)
//        val internalBasePath = getDownloadPath()
//
//        val name = fileExist(context, sensorName) ?: setFileName(sensorName)
//        Log.d(this.toString(), "csv 작성 시작: $name")
//
//        // 센서 이름 추출
//        val sensorType = sensorName.split("_").getOrNull(0) ?: "UnknownSensor"
//
//        // 센서별 폴더 경로
//        val internalSensorFolder = File(internalBasePath, "sensor_data/$sensorType")
//        val externalSensorFolder = File(externalBasePath, "sensor_data/$sensorType")
//
//        // 폴더 생성
//        if (!internalSensorFolder.exists()) {
//            internalSensorFolder.mkdirs()
//            Log.d(this.toString(), "내부 센서 폴더 생성 완료: ${internalSensorFolder.path}")
//        }
//        if (!externalSensorFolder.exists()) {
//            externalSensorFolder.mkdirs()
//            Log.d(this.toString(), "외부 센서 폴더 생성 완료: ${externalSensorFolder.path}")
//        }
//
//        val headerData = when {
//            abstractSensorSet.all { it is OneAxisData } -> arrayOf("time", "value")
//            abstractSensorSet.all { it is ThreeAxisData } -> arrayOf("time", "x", "y", "z")
//            else -> arrayOf("time", "value")
//        }
//
//        val externalFile = File(externalSensorFolder, name)
//        val internalFile = File(internalSensorFolder, name)
//        val externalCsvWriter = getCsvWriter(externalSensorFolder.path, name)
//        val internalCsvWriter = getCsvWriter(internalSensorFolder.path, name)
//
//        try {
//            if (externalFile.length() == 0L) {
//                externalCsvWriter.writeNext(headerData)
//                externalCsvWriter.flush()
//            }
//            if (internalFile.length() == 0L) {
//                internalCsvWriter.writeNext(headerData)
//                internalCsvWriter.flush()
//            }
//
//            for (sensor in abstractSensorSet) {
//                val data = convertSensorToStringArray(sensor)
//                externalCsvWriter.writeNext(data)
//                internalCsvWriter.writeNext(data)
//            }
//
//            externalCsvWriter.flush()
//            internalCsvWriter.flush()
//        } catch (e: Exception) {
//            Log.e(TAG, "CSV 저장 중 오류: ${e.message}", e)
//        } finally {
//            try {
//                externalCsvWriter.close()
//                internalCsvWriter.close()
//            } catch (e: IOException) {
//                Log.e(TAG, "CSVWriter 닫기 오류: ${e.message}", e)
//            }
//        }
//    }

    fun csvSave(context: Context, sensorName: String, abstractSensorSet: List<AbstractSensor>) {
        val downloadBasePath = getDownloadPath() // /storage/emulated/0/Download
        val name = setFileName(sensorName)
        Log.d(this.toString(), "csv 작성 시작: $name")

        // 센서 이름 추출
        val sensorType = sensorName.split("_").getOrNull(0) ?: "UnknownSensor"

        // sensor_data/센서이름 폴더 지정
        val sensorFolder = File(downloadBasePath, "sensor_data/$sensorType")

        // 폴더가 없으면 생성
        if (!sensorFolder.exists()) {
            sensorFolder.mkdirs()
            Log.d(this.toString(), "센서 폴더 생성 완료: ${sensorFolder.path}")
        }

        // 저장 파일 경로 지정
        val file = File(sensorFolder, name)
        val writer = getCsvWriter(sensorFolder.path, name)

        // 헤더 결정
        val headerData = when {
            abstractSensorSet.all { it is OneAxisData } -> arrayOf("time", "value")
            abstractSensorSet.all { it is ThreeAxisData } -> arrayOf("time", "x", "y", "z")
            else -> arrayOf("time", "value")
        }

        try {
            if (file.length() == 0L) {
                writer.writeNext(headerData)
                writer.flush()
            }

            for (sensor in abstractSensorSet) {
                val data = convertSensorToStringArray(sensor)
                writer.writeNext(data)
            }

            writer.flush()
        } catch (e: Exception) {
            Log.e("csvSave", "CSV 저장 중 오류: ${e.message}", e)
        } finally {
            try {
                writer.close()
            } catch (e: IOException) {
                Log.e("csvSave", "CSVWriter 닫기 오류: ${e.message}", e)
            }
        }
    }

    private fun convertSensorToStringArray(abstractSensor: AbstractSensor): Array<String> {
        val time = abstractSensor.time.toString()
//        val type = abstractSensor.type
        return when (abstractSensor) {
            is OneAxisData -> {
                val value = abstractSensor.value.toString()
                arrayOf(time, value)
            }
            is ThreeAxisData -> {
                val xValue = abstractSensor.xValue.toString()
                val yValue = abstractSensor.yValue.toString()
                val zValue = abstractSensor.zValue.toString()
                arrayOf(time, xValue, yValue, zValue)
            }
            else -> {
                emptyArray();
            }
        }
    }

    fun fileRename(path:String, origin: String, change: String) {
        try {
            val file = File(path, origin)
            if (!file.exists()) {
//                throw NoSuchFileException("Source file doesn't exist")
            }

            val dest = File(path, change)
            if (dest.exists()) {
//                throw FileAlreadyExistsException("Destination file already exist")
            }
            val success = file.renameTo(dest)
            if (success) {
                println("Renaming succeeded")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


     fun getFile(fileName: String): File? {
        val file = File(fileName)
        if (!file.exists()) {
            Log.d("csv controller", fileName + " File does not found")
            return null
        }

        return file
    }

//    //파일 디렉토리 옮기기
//    //soure -> dest로
//    fun moveFile(sourcePath: String, destinationPath: String) {
//        val sourceFile = File(sourcePath)
//        val destinationFile = File(destinationPath)
//
//        try {
//            // 파일을 이동합니다.
//            sourceFile.renameTo(destinationFile)
//            Log.d("csv controller", "파일 이동 성공")
//        } catch (e: IOException) {
//            println("파일 이동 실패: ${e.message}")
//        }
//    }

    fun moveFile(sourcePath: String, destinationPath: String) {
        val sourceFile = File(sourcePath)
        val destinationFile = File(destinationPath)

        if (!sourceFile.exists()) {
            Log.e("csv controller", "❌ 원본 파일이 존재하지 않음: $sourcePath")
            return
        }

        // 상위 폴더 없으면 생성
        destinationFile.parentFile?.mkdirs()

        try {
            sourceFile.copyTo(destinationFile, overwrite = true)
            val deleted = sourceFile.delete()

            Log.d("csv controller", "✅ 파일 복사 성공: $sourcePath → $destinationPath")
            if (!deleted) {
                Log.w("csv controller", "⚠️ 원본 파일 삭제 실패")
            }
        } catch (e: IOException) {
            Log.e("csv controller", "❌ 파일 이동 실패: ${e.message}", e)
        }
    }


    fun deleteFilesInDirectory(dirPath: String){
        val dir = File(dirPath)
        if(dir.exists() && dir.isDirectory){
            val childFileList = dir.listFiles()

            if(childFileList != null){
                for(childFile in childFileList){
                    if(childFile.isDirectory){
                        deleteFilesInDirectory(childFile.absolutePath)
                    }else{
                        childFile.delete()
                    }
                }
            }

            dir.delete()
            Log.d("csv controller", "Delete all files successfully")
        }
    }

    fun deleteOldfiles(dir: String, period: Int){
        val directory = File(dir)
        val files: Array<out File>? = directory.listFiles()
        val now = System.currentTimeMillis()

        for (file in files!!) {
            if(file.isDirectory) continue

            val lastModified = file.lastModified()
            val diff = now - lastModified

            if(diff > period){
                file.delete()
            }
        }
    }
}