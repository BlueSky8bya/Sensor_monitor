package com.gachon_HCI_Lab.user_mobile.service

import android.R.attr.tag
import android.content.Context
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.common.*
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AcceptThread(context: Context) : Thread() {
    private lateinit var sensorController: SensorController
    private var reconstructedOneAxisData = StringBuilder()
    private var reconstructedTreeAxisData = StringBuilder()

    init {
        try {
            sensorController = SensorController.getInstance(context)
        } catch (e: Exception) {
            // 초기화 에러 로그 추가
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
                    BluetoothConnect.createBluetoothSocket()
                    val inputStream = BluetoothConnect.createInputStream()

                    // 연결 성공 로그
                    CsvController.writeLog("SOCKET_CONNECTED: 워치와 새로운 연결 수립")
                    EventBus.getDefault().post(ThreadStateEvent(ThreadState.RUN))

                    while (BluetoothConnect.isBluetoothRunning()) {
                        val buffer = createByteArray()
                        val receivedData = getByteArrayFrom(inputStream, buffer)

                        // 수신 데이터가 비어있으면 (블루투스 소켓이 끊긴 신호)
                        if (receivedData.isEmpty()) {
                            CsvController.writeLog("SOCKET_RECEIVE_EMPTY: 데이터 수신 결과가 0입니다. 내부 루프 종료.")
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

                    // 내부 루프를 빠져나왔을 때 (break)
                    CsvController.writeLog("SOCKET_LOOP_BREAK: BluetoothRunning 상태가 아니거나 수신 데이터가 없어 루프를 탈출함")

                } catch (e: Exception) {
                    // 개별 연결 시도의 에러 로그
                    CsvController.writeLog("SOCKET_IO_EXCEPTION: 연결 유지 중 에러 발생 - ${e.message}")
                    // 여기서 break를 하지 않고 다시 위로 올라가서 재연결을 시도하게 할 수도 있습니다.
                    // 만약 무한 재연결을 원치 않으시면 아래 break를 유지하세요.
                    break
                }
            }
        } catch (e: Exception) {
            // 최상위 루프 자체가 터졌을 때 (심각한 메모리 오류 등)
            CsvController.writeLog("CRITICAL_THREAD_ERROR: 최상위 루프 사망 - ${e.message}")
        } finally {
            // [핵심] 어떤 이유로든 스레드가 끝날 때 무조건 실행됨
            CsvController.writeLog("THREAD_TERMINATED: AcceptThread가 완전히 종료되었습니다.")
            EventBus.getDefault().post(ThreadStateEvent(ThreadState.STOP))
        }
    }

    // 데이터 수신 중 발생하는 에러 포착
    private fun getByteArrayFrom(inputStream: InputStream, buffer: ByteArray): ByteArray {
        return try {
            when (val readBytes = inputStream.read(buffer)) {
                -1 -> { // 소켓이 완전히 닫혔을 때
                    CsvController.writeLog("SOCKET_STREAM_END: 워치에서 연결을 닫았습니다.")
                    handleSocketError()
                    BluetoothConnect.stopRunning()
                    ByteArray(0)
                }
                0 -> { // 데이터가 잠시 안 들어올 때 (중요!)
                    Log.d(tag.toString(), "Zero bytes read, skipping...")
                    ByteArray(1) // 빈 배열이 아닌 1바이트라도 반환해서 루프를 유지시킴
                }
                else -> {
                    buffer.copyOf(readBytes)
                }
            }
        } catch (e: IOException) {
            CsvController.writeLog("SOCKET_IO_EXCEPTION: ${e.message}")
            handleSocketError()
            BluetoothConnect.stopRunning()
            ByteArray(0)
        }
    }

    private fun handleSocketError() {
        // [수정] 소켓 에러 발생 시 상태 로그 추가
        CsvController.writeLog("HANDLE_SOCKET_ERROR: 소켓 에러 처리 시작")
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
            CsvController.writeLog("PARSING_ERROR: ${e.message}")
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
        CoroutineScope(Dispatchers.IO).launch {
            sensorController.dataAccept(stepCount)
        }
    }

    private fun saveOneAxisDataToCsv() {
        val oneAxisData = reconstructedOneAxisData.toString()
        if (oneAxisData.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                sensorController.dataAccept(oneAxisData)
            }
        }
    }

    private fun saveThreeDataToCsv() {
        val threeAxisData = reconstructedTreeAxisData.toString()
        if (threeAxisData.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
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