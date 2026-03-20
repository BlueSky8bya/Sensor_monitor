package com.gachon_HCI_Lab.user_mobile.service

import android.content.Context
import com.gachon_HCI_Lab.user_mobile.common.*
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

class AcceptThread(context: Context) : Thread() {
    @Volatile var lastReadTime: Long = System.currentTimeMillis()
    private lateinit var sensorController: SensorController
    private var reconstructedOneAxisData = StringBuilder()
    private var reconstructedTreeAxisData = StringBuilder()

    // [추가] 코루틴 폭발을 막기 위한 단일 스레드 교통경찰 (DB 쓰기는 여기서만 순차적으로 실행)
    private val dbDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val dbScope = CoroutineScope(dbDispatcher)

    init {
        try {
            sensorController = SensorController.getInstance(context)
        } catch (e: Exception) {
            CsvController.writeLog("INIT_ERROR: ${e.message}")
            EventBus.getDefault().post(ThreadStateEvent(ThreadState.STOP))
            e.printStackTrace()
        }
    }

    override fun run() {
        try {
            CsvController.writeLog("THREAD_START: 수신 스레드 루프 진입")
            BluetoothConnect.createSeverSocket()

            while (true) {
                try {
                    CsvController.writeLog("SOCKET_WAIT: 워치의 연결을 기다리는 중...")
                    BluetoothConnect.createBluetoothSocket()
                    val inputStream = BluetoothConnect.createInputStream()

                    CsvController.writeLog("SOCKET_CONNECTED: 워치와 새로운 연결 수립")
                    EventBus.getDefault().post(ThreadStateEvent(ThreadState.RUN))

                    while (BluetoothConnect.isBluetoothRunning()) {
                        val buffer = createByteArray()

                        // [추가] 읽기 직전에 생존 신고
                        lastReadTime = System.currentTimeMillis()

                        val receivedData = getByteArrayFrom(inputStream, buffer)

                        // [추가] 1000번에 1번꼴로 생존 로그 남기기 (너무 많으면 파일 터짐 방지)
                        if (Math.random() < 0.001) {
                            CsvController.writeLog("HEARTBEAT: 수신 스레드 멈추지 않고 계속 데이터 읽는 중...")
                        }

                        if (receivedData.isEmpty()) {
                            // [추적 1] Empty 반환으로 루프 탈출
                            CsvController.writeLog("SOCKET_BREAK: receivedData가 비어있음. (소켓 끊김 감지)")
                            break
                        }

                        val byteBuffer = createByteBufferFrom(receivedData)
                        updateStringBuffer()
                        saveBatteryDataFrom(byteBuffer)
                        saveStepCountDataFrom(byteBuffer)
                        saveSensorDataToString(byteBuffer)
                        saveOneAxisDataToCsv()
                        saveThreeDataToCsv()
                    }

                    // [추적 2] 내부 루프 탈출 사유 기록
                    if (!BluetoothConnect.isBluetoothRunning()) {
                        CsvController.writeLog("SOCKET_BREAK: isBluetoothRunning이 false로 변경되어 루프 탈출")
                    }

                } catch (e: Exception) {
                    // [추적 3] accept() 대기 중이거나 다른 예외 발생
                    CsvController.writeLog("SOCKET_OUTER_EXCEPTION: ${e.javaClass.simpleName} - ${e.message}")
                    EventBus.getDefault().post(SocketStateEvent(SocketState.CLOSE))
                    break // 여기서 무한루프를 깨볼게요 (에러 나면 차라리 죽고 다시 시작하게)
                }
            }
        } catch (e: Exception) {
            CsvController.writeLog("CRITICAL_THREAD_ERROR: 최상위 루프 사망 - ${e.message}")
        } finally {
            CsvController.writeLog("THREAD_TERMINATED: AcceptThread가 완전히 종료되었습니다.")
            EventBus.getDefault().post(ThreadStateEvent(ThreadState.STOP))
        }
    }

    private fun getByteArrayFrom(inputStream: InputStream, buffer: ByteArray): ByteArray {
        return try {
            when (val readBytes = inputStream.read(buffer)) {
                -1 -> {
                    CsvController.writeLog("SOCKET_STREAM_END: 워치에서 연결을 끊었음 (EOF)")
                    handleSocketError()
                    BluetoothConnect.stopRunning()
                    ByteArray(0)
                }
                0 -> {
                    ByteArray(1)
                }
                else -> {
                    buffer.copyOf(readBytes)
                }
            }
        } catch (e: IOException) {
            // [추적 4] read() 도중 파이프가 끊어짐
            CsvController.writeLog("SOCKET_IO_EXCEPTION (read 중): ${e.message}")
            handleSocketError()
            BluetoothConnect.stopRunning()
            ByteArray(0)
        }
    }

    private fun handleSocketError() {
        CsvController.writeLog("HANDLE_SOCKET_ERROR: 에러 처리 진행 (상태 CLOSE 변경)")
        EventBus.getDefault().post(SocketStateEvent(SocketState.CLOSE))
        EventBus.getDefault().post(ThreadStateEvent(ThreadState.STOP))
        clear()
    }

    private fun createByteArray(): ByteArray = ByteArray(968)

    private fun createByteBufferFrom(receivedData: ByteArray): ByteBuffer {
        val byteBuffer = ByteBuffer.wrap(receivedData)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        return byteBuffer
    }

    private fun saveSensorDataToString(byteBuffer: ByteBuffer) {
        while (byteBuffer.position() < byteBuffer.limit()) {
            if (!validateBufferSize(byteBuffer)) break
            saveEachSensorDataToString(byteBuffer)
        }
    }

    private fun saveEachSensorDataToString(byteBuffer: ByteBuffer) {
        try {
            val dataType = byteBuffer.int
            if (!validateSensorDataType(dataType)) return
            val timestamp = byteBuffer.long
            addOneAxisData(byteBuffer, dataType, timestamp)
            addThreeAxisData(byteBuffer, dataType, timestamp)
        } catch (e: Exception) {
            // 여기서 파싱 에러가 나면 조용히 삼키지 말고 남기기
            CsvController.writeLog("PARSING_ERROR: 버퍼 파싱 중 오류 - ${e.message}")
        }
    }

    private fun saveBatteryDataFrom(byteBuffer: ByteBuffer) {
        if (byteBuffer.remaining() < 4) return
        val battery = byteBuffer.int
        DeviceInfo.setBattery(battery.toString())
    }

    private fun saveStepCountDataFrom(byteBuffer: ByteBuffer) {
        if (byteBuffer.remaining() < 4) return
        val stepCount = byteBuffer.int
        dbScope.launch { // [수정됨] 무한 생성 대신 단일 스레드 대기열로 보냄
            sensorController.dataAccept(stepCount)
        }
    }

    private fun saveOneAxisDataToCsv() {
        val oneAxisData = reconstructedOneAxisData.toString()
        if (oneAxisData.isNotEmpty()) {
            dbScope.launch { // [수정됨]
                sensorController.dataAccept(oneAxisData)
            }
        }
    }

    private fun saveThreeDataToCsv() {
        val threeAxisData = reconstructedTreeAxisData.toString()
        if (threeAxisData.isNotEmpty()) {
            dbScope.launch { // [수정됨]
                sensorController.dataAccept(threeAxisData)
            }
        }
    }

    private fun updateStringBuffer() {
        reconstructedOneAxisData = StringBuilder()
        reconstructedTreeAxisData = StringBuilder()
    }

    private fun validateOneAxisDataType(dataType: Int) = dataType in listOf(5, 18, 21, 30)
    private fun validateSensorDataType(dataType: Int) = dataType != 0
    private fun validateBufferSize(byteBuffer: ByteBuffer) = (byteBuffer.limit() - byteBuffer.position()) >= 16
    private fun validateBufferSizeForOneAxisSensor(byteBuffer: ByteBuffer) = (byteBuffer.limit() - byteBuffer.position()) >= 4
    private fun validateBufferSizeForThreeAxisSensor(byteBuffer: ByteBuffer) = (byteBuffer.limit() - byteBuffer.position()) >= 12

    private fun addOneAxisData(byteBuffer: ByteBuffer, dataType: Int, timestamp: Long) {
        if (!validateOneAxisDataType(dataType)) return
        if (!validateBufferSizeForOneAxisSensor(byteBuffer)) return
        val data = byteBuffer.float
        addOneAxisDataToString(dataType, timestamp, data)
    }

    private fun addThreeAxisData(byteBuffer: ByteBuffer, dataType: Int, timestamp: Long) {
        if (validateOneAxisDataType(dataType)) return
        if (!validateBufferSizeForThreeAxisSensor(byteBuffer)) return
        val xAxisData = byteBuffer.float
        val yAxisData = byteBuffer.float
        val zAxisData = byteBuffer.float
        addThreeAxisDataToString(dataType, timestamp, xAxisData, yAxisData, zAxisData)
    }

    private fun addOneAxisDataToString(dataType: Int, timestamp: Long, data: Float) {
        reconstructedOneAxisData.append(dataType).append("|").append(timestamp).append("|").append(data).append(":")
    }

    private fun addThreeAxisDataToString(dataType: Int, timestamp: Long, x: Float, y: Float, z: Float) {
        reconstructedTreeAxisData.append(dataType).append("|").append(timestamp).append("|").append(x).append("|").append(y).append("|").append(z).append(":")
    }

    fun clear() {
        updateStringBuffer()
    }
}