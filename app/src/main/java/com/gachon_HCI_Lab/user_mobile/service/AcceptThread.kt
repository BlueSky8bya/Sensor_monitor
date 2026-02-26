package com.gachon_HCI_Lab.user_mobile.service

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

/**
 * 서비스에서 소켓 연결을 담당하는 클래스
 * */
class AcceptThread(context: Context) : Thread() {
    private lateinit var sensorController: SensorController
    private var reconstructedOneAxisData = StringBuilder()
    private var reconstructedTreeAxisData = StringBuilder()

    init {
        try {
            sensorController = SensorController.getInstance(context)
        } catch (e: Exception) {
            EventBus.getDefault().post(ThreadStateEvent(ThreadState.STOP))
            e.printStackTrace()
        }
    }

    override fun run() {
        BluetoothConnect.createSeverSocket()
        while(true) {
            BluetoothConnect.createBluetoothSocket()
            val inputStream = BluetoothConnect.createInputStream()
            EventBus.getDefault().post(ThreadStateEvent(ThreadState.RUN))
            while (BluetoothConnect.isBluetoothRunning()) {
                val buffer = createByteArray()
                val receivedData = getByteArrayFrom(inputStream, buffer)
                if (receivedData.size == 0) break
                val byteBuffer = createByteBufferFrom(receivedData)
                updateStringBuffer()
                saveBatteryDataFrom(byteBuffer)
                saveStepCountDataFrom(byteBuffer)
                saveSensorDataToString(byteBuffer)
                saveOneAxisDataToCsv()
                saveThreeDataToCsv()
            }
        }
    }

    fun clear() {
        updateStringBuffer()
    }

    private fun validateOneAxisDataType(dataType: Int): Boolean {
        if (dataType == 5 || dataType == 18 || dataType == 21 || dataType == 30)
            return true
        return false
    }

    private fun validateBufferSize(byteBuffer: ByteBuffer): Boolean {
        if ((byteBuffer.limit() - byteBuffer.position()) < 16)
            return false
        return true
    }

    private fun validateBufferSizeForOneAxisSensor(byteBuffer: ByteBuffer): Boolean {
        if ((byteBuffer.limit() - byteBuffer.position()) < 4)
            return false
        return true
    }

    private fun validateBufferSizeForThreeAxisSensor(byteBuffer: ByteBuffer): Boolean {
        if ((byteBuffer.limit() - byteBuffer.position()) < 12)
            return false
        return true
    }

    private fun validateSensorDataType(dataType: Int): Boolean {
        if (dataType == 0)
            return false
        return true
    }

    private fun createByteArray(): ByteArray {
        return ByteArray(968)
    }

    private fun createByteBufferFrom(receivedData: ByteArray): ByteBuffer {
        val byteBuffer = ByteBuffer.wrap(receivedData)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        return byteBuffer
    }

    private fun getByteArrayFrom(inputStream: InputStream, buffer: ByteArray): ByteArray {
        val receivedData: ByteArray
        try {
            receivedData = buffer.copyOf(inputStream.read(buffer))
        } catch (e: IOException) {
            handleSocketError()
            BluetoothConnect.stopRunning()
            return ByteArray(0)
        }
        return receivedData
    }

    private fun saveSensorDataToString(byteBuffer: ByteBuffer) {
        while (byteBuffer.position() < byteBuffer.limit()) {
            if (!validateBufferSize(byteBuffer))
                break
            saveEachSensorDataToString(byteBuffer)
        }
    }

    private fun saveEachSensorDataToString(byteBuffer: ByteBuffer) {
        val dataType = byteBuffer.int
        if (!validateSensorDataType(dataType)) return
        val timestamp = byteBuffer.long
        addOneAxisData(byteBuffer, dataType, timestamp)
        addThreeAxisData(byteBuffer, dataType, timestamp)
    }

    private fun saveBatteryDataFrom(byteBuffer: ByteBuffer) {
        val battery = byteBuffer.int
        DeviceInfo.setBattery(battery.toString())
        Log.d("Battery", battery.toString())
    }

    private fun saveStepCountDataFrom(byteBuffer: ByteBuffer){
        val stepCount = byteBuffer.int
        Log.d("stepCount", stepCount.toString())
        CoroutineScope(Dispatchers.IO).launch {
            sensorController.dataAccept(stepCount)
        }
    }

    private fun saveOneAxisDataToCsv() {
        val oneAxisData = reconstructedOneAxisData.toString()
        Log.d("OneAxisData", oneAxisData)
        CoroutineScope(Dispatchers.IO).launch {
            sensorController.dataAccept(oneAxisData)
        }
    }

    private fun saveThreeDataToCsv() {
        val threeAxisData = reconstructedTreeAxisData.toString()
        Log.d("ThreeAxisData", threeAxisData)
        CoroutineScope(Dispatchers.IO).launch {
            sensorController.dataAccept(threeAxisData)
        }
    }

    private fun updateStringBuffer() {
        reconstructedOneAxisData = StringBuilder()
        reconstructedTreeAxisData = StringBuilder()
    }

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

    private fun addOneAxisDataToString(
        dataType: Int,
        timestamp: Long,
        data: Float
    ) {
        reconstructedOneAxisData.append(dataType).append("|")
        reconstructedOneAxisData.append(timestamp).append("|")
        reconstructedOneAxisData.append(data).append(":")
    }

    private fun addThreeAxisDataToString(
        dataType: Int,
        timestamp: Long,
        xAxisData: Float,
        yAxisData: Float,
        zAxisData: Float
    ) {
        reconstructedTreeAxisData.append(dataType).append("|")
        reconstructedTreeAxisData.append(timestamp).append("|")
        reconstructedTreeAxisData.append(xAxisData).append("|")
        reconstructedTreeAxisData.append(yAxisData).append("|")
        reconstructedTreeAxisData.append(zAxisData).append(":")
    }

    private fun handleSocketError() {
        EventBus.getDefault().post(SocketStateEvent(SocketState.CLOSE))
        EventBus.getDefault().post(ThreadStateEvent(ThreadState.STOP))
        clear()
    }
}
