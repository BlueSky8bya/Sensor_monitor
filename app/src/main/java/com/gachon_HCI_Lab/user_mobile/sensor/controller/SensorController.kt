package com.gachon_HCI_Lab.user_mobile.sensor.controller

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.common.RegexManager
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.SensorEnum
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.service.OneAxisDataService
import com.gachon_HCI_Lab.user_mobile.sensor.service.ThreeAxisDataService
import kotlinx.coroutines.*
import kotlin.concurrent.thread

/**
 * 센서데이터 관리하는 클래스
 * */
class SensorController(context: Context) {
    // 1. 미사용 변수(oneAxisList, threeAxisList) 삭제함
    private val oneAxisDataService: OneAxisDataService = OneAxisDataService.getInstance(context)
    private val threeAxisDataService: ThreeAxisDataService = ThreeAxisDataService.getInstance(context)
    private val prefManager: SharePreferenceManager = SharePreferenceManager.getInstance(context)
    private val regexManager: RegexManager = RegexManager.getInstance()

    // [중요] 버퍼 로직을 위한 변수들 (이전 답변에서 추가한 부분)
    private val dataBuffer = mutableListOf<String>()
    private val bufferSize = 1024
    private val bufferTime = 2000L
    private var flushJob: Job? = null

    companion object {
        private var INSTANCE: SensorController? = null

        fun getInstance(context: Context): SensorController { // _context 대신 context로 변경
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SensorController(context).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * 소켓으로부터 수신받은 데이터를 버퍼에 저장하고 플러시하는 메소드
     */
    suspend fun dataAccept(data: String) = coroutineScope {
        // [수정] 클래스 멤버 변수인 dataBuffer에 데이터를 추가합니다.
        dataBuffer.add(data)

        // [수정] 클래스 멤버 변수인 bufferSize와 비교합니다.
        if (dataBuffer.size >= bufferSize) {
            flushBuffer(dataBuffer)
        }

        // [수정] 일정 주기마다 처리하는 타이머 코루틴이 없으면 새로 생성 (flushJob 활용)
        if (flushJob == null || flushJob?.isCompleted == true) {
            flushJob = launch {
                delay(bufferTime) // 클래스의 bufferTime 사용
                if (dataBuffer.isNotEmpty()) {
                    flushBuffer(dataBuffer)
                }
            }
        }
    }

    suspend fun dataAccept(step: Int) = coroutineScope {
        oneAxisDataService.insert(
            OneAxisData.of(step.toDouble(), SensorEnum.STEP_COUNT.value)
        )
        Log.d(TAG, "SAVED: type: ${SensorEnum.STEP_COUNT.value}, data: $step")
    }

    /**
     * 호출 시 로컬 DB에 저장된 센서 데이터를 가져온다
     * sensorName: PpgGreen,HeartRate (현재 코드에선 SensorEnum 사용)
     * List<AbstractSensor>: 센서데이터 리스트
     * */
    private suspend fun dataExport(axisType: String): List<AbstractSensor> =
        withContext(Dispatchers.IO) {
            // 센서의 값을 불러온 후, 그 리스트의 사이즈 값을 커서로 저장
            // 다음 호출시 그 커서부터 다시 데이터 호출
            val abstractSensorSet: List<AbstractSensor> = when (axisType) {
                "OneAxis" -> {
                    oneAxisDataExport()
                }
                "ThreeAxis" -> {
                    threeAxisDataExport()
                }
                else -> throw IllegalArgumentException("Invalid sensor name: $axisType")
            }
            return@withContext abstractSensorSet
        }

    private fun oneAxisDataExport(): List<AbstractSensor> {
        val oneAxisCursor = prefManager.getCursor("oneAxis")
        Log.d(TAG, "Start One_Axis cursor: $oneAxisCursor")
        val oneAxisSet = oneAxisDataService.getAll(oneAxisCursor)
        val oneAxisSetSize = oneAxisSet.size
        prefManager.putCursor("oneAxis", oneAxisCursor + oneAxisSetSize)
        return oneAxisSet
    }

    private fun threeAxisDataExport(): List<AbstractSensor> {
        val threeAxisCursor = prefManager.getCursor("threeAxis")
        Log.d(TAG, "Start Three_Axis cursor: $threeAxisCursor")
        val threeAxisSet = threeAxisDataService.getAll(threeAxisCursor)
        val threeAxisSetSize = threeAxisSet.size
        prefManager.putCursor("threeAxis", threeAxisCursor + threeAxisSetSize)
        return threeAxisSet
    }

    /**
     * 버퍼 내용을 처리하는 메서드
     */
    private suspend fun flushBuffer(buffer: MutableList<String>) {
        // bufferData를 SensorRepository에 작성
        Log.d(this.toString(), buffer.toString())
        writeSensorRepo(buffer)
        buffer.clear() // 버퍼 내용을 비움
    }

    /**
     * 버퍼의 데이터를 알맞은 센서 테이블에 저장하는 메소드
     * */
    private suspend fun writeSensorRepo(bufferList: List<String>) = coroutineScope {
        for (buffer in bufferList) {
            Log.d(TAG, "str: $buffer")
            val oneAxisList = regexManager.oneAxisRegex.findAll(buffer)
            Log.d(TAG, "1_Ax: $oneAxisList")
            val threeAxisList = regexManager.threeAxisRegex.findAll(buffer)
            Log.d(TAG, "3_Ax: $threeAxisList")

            /**
             * 1축 데이터 저장하는 구간
             * 1축 데이터 정규표현식에 맞는 경우 데이터 저장
             * */
            launch {
                try {
                    for (oneAxisPattern in oneAxisList) {
                        val oneAxisVal = oneAxisPattern.value
                        val type =
                            SensorEnum.getValueByType(regexManager.typeRegex.find(oneAxisVal)!!.value.toInt())
                        val resRex = regexManager.oneAxisValueRegex.find(oneAxisVal)
                        val res = resRex?.value
                        val dataMap = regexManager.oneAxisDataExtract(type, res!!)
                        oneAxisDataService.insert(
                            OneAxisData.of(dataMap.time, dataMap.type, dataMap.data)
                        )
                        Log.d(
                            TAG,
                            "SAVED: type: ${dataMap.type} time: ${dataMap.time}, data: ${dataMap.data}"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            /**
             * PpgGreen 저장하는 구간
             * ppgGreen 정규표현식의 맞는 경우 데이터 저장
             * */
            launch {
                try {
                    for (threeAxisPattern in threeAxisList) {
                        val threeAxisVal = threeAxisPattern.value
                        val type = SensorEnum.getValueByType(
                            regexManager.typeRegex.find(threeAxisVal)!!.value.toInt()
                        )
                        val resRex = regexManager.threeAxisValueRegex.find(threeAxisVal)
                        val res = resRex?.value
                        val dataMap = regexManager.threeAxisDataExtract(type, res!!)
                        threeAxisDataService.insert(
                            ThreeAxisData.of(dataMap.time, dataMap.type, dataMap.xData, dataMap.yData, dataMap.zData)
                        )
                        Log.d(
                            TAG,
                            "SAVED: " +
                                    "type: ${dataMap.type} " +
                                    "time: ${dataMap.time}, " +
                                    "data: ${dataMap.xData}, ${dataMap.yData}, ${dataMap.zData}"
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 로컬 DB에서 가져온 센서 데이터를 csv에 저장하고 DB를 비우는 메소드
     */
    suspend fun writeCsv(type: String) = coroutineScope {
        // 1. DB에서 현재까지 쌓인 데이터 추출
        val sensorSet: List<AbstractSensor> = dataExport(type)

        // 만약 가져온 데이터가 없다면 중단
        if (sensorSet.isEmpty()) return@coroutineScope

        // 2. 데이터를 센서 타입별로 분리
        val splittedDatas = splitData(sensorSet)

        // 3. 파일 저장 시도
        for (dataList in splittedDatas) {
            val sensorName = dataList.key
            try {
                CsvController.csvSave(sensorName, dataList.value)
                Log.d(TAG, "$sensorName 데이터 CSV 저장 완료")
            } catch (e: Exception) {
                Log.e(TAG, "CSV 저장 중 오류 발생: ${e.message}")
            }
        }

        // 4. 추출한 데이터를 다시 쓰지 않도록 DB를 즉시 비웁니다.
        withContext(Dispatchers.IO) {
            if (type == "OneAxis") {
                oneAxisDataService.deleteAll()
                // 커서(페이지네이션 기록)도 초기화해주는 것이 안전합니다.
                prefManager.putCursor("oneAxis", 0)
            } else {
                threeAxisDataService.deleteAll()
                prefManager.putCursor("threeAxis", 0)
            }
        }
        Log.d("SensorController", "$type DB Cleared (Success)")
    }

    fun splitData(sensorSet: List<AbstractSensor>): MutableMap<String, List<AbstractSensor>> {
        // key: sensorName, value: sensorData
        val resultMap : MutableMap<String, List<AbstractSensor>> = mutableMapOf()

        for (data in sensorSet){
            val sensorName = data.type
            if(resultMap.containsKey(sensorName)){
                resultMap[sensorName] = resultMap[sensorName]!!.plus(data)
            } else {
                resultMap[sensorName] = listOf(data)
            }
        }

        return resultMap
    }

    /**
     * 지금으로부터 원하는 시간만큼 데이터를 추출하는 메소드. 코루틴 메소드
     * sensorName: PpgGreen,HeartRate (현재 코드에선 SensorEnum 사용)
     * time: unixtime 기준
     */
    suspend fun getDataFromNow(sensorName: String, time: Long): List<AbstractSensor> =
        withContext(Dispatchers.IO) {
            val abstractSensorSet: List<AbstractSensor> = when (sensorName) {
                "OneAxis" -> {
                    val oneAxisDataSet = oneAxisDataService.getFromNow(time)
                    oneAxisDataSet
                }
                "ThreeAxis" -> {
                    val threeAxisDataSet = threeAxisDataService.getFromNow(time)
                    threeAxisDataSet
                }
                else -> throw IllegalArgumentException("Invalid sensor name: $sensorName")
            }
            Log.d("GET DATA FROM NOW", abstractSensorSet.size.toString())
            return@withContext abstractSensorSet
        }

    /**
     * RoomDB에 저장된 센서 데이터 모두 삭제
     */
    fun deleteAll(){
        thread(start = true) {
            oneAxisDataService.deleteAll()
            threeAxisDataService.deleteAll()
        }
    }


    /**
     * sharedPreference 싱글톤 객체
     * 데이터 조회 및 수집시(페이징 활용) 필요한 키 값 저장을 위해 사용
     */
    /**
     * sharedPreference 싱글톤 객체
     */
    class SharePreferenceManager private constructor() {
        companion object {
            private lateinit var pref: SharedPreferences
            private lateinit var editor: SharedPreferences.Editor
            private var instance: SharePreferenceManager? = null

            fun getInstance(context: Context): SharePreferenceManager {
                if (instance == null) {
                    instance = SharePreferenceManager()
                    initialize(context)
                }
                return instance!!
            }

            private fun initialize(context: Context) {
                // [해결] 메모리 누수 방지를 위해 applicationContext 사용
                pref = context.applicationContext.getSharedPreferences("pref", Activity.MODE_PRIVATE)
                editor = pref.edit()
            }
        }

        // [해결] static field에 context를 저장하지 않도록 클래스 내부의 context 변수 삭제

        fun getCursor(sensorName: String): Int {
            return pref.getInt(sensorName + "Cursor", 0)
        }

        fun putCursor(sensorName: String, lastCursor: Int) {
            editor.putInt(sensorName + "Cursor", lastCursor)
            editor.apply()
        }
    }
}