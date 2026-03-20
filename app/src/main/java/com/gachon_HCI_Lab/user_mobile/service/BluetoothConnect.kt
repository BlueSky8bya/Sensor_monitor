package com.gachon_HCI_Lab.user_mobile.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.common.SocketState
import com.gachon_HCI_Lab.user_mobile.common.SocketStateEvent
import com.gachon_HCI_Lab.user_mobile.common.ThreadState
import com.gachon_HCI_Lab.user_mobile.common.ThreadStateEvent
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.io.InputStream
import java.util.*

object BluetoothConnect {
    private lateinit var serverSocket: BluetoothServerSocket
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var socket: BluetoothSocket
    private lateinit var inputStream: InputStream
    private var isRunning: Boolean = true

    private val SOCKET_NAME = "server"
    private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    fun createBluetoothAdapter(bluetoothAdapter: BluetoothAdapter){
        this.bluetoothAdapter = bluetoothAdapter
    }

    @SuppressLint("MissingPermission")
    fun createSeverSocket() {
        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SOCKET_NAME, MY_UUID)
    }

    @Throws(IOException::class)
    fun createBluetoothSocket() {
        try {
            isRunning = true
            socket = serverSocket.accept()
            Log.d("socketConnect", socket.toString())
            Thread.sleep(300)
        } catch (e: IOException) {
            EventBus.getDefault().post(ThreadStateEvent(ThreadState.STOP))
            throw IOException()
        }
    }

    fun createInputStream(): InputStream {
        inputStream = socket.inputStream
        EventBus.getDefault().post(SocketStateEvent(SocketState.CONNECT))
        isRunning = true
        return inputStream
    }

    fun isBluetoothRunning(): Boolean {
        return isRunning
    }

    fun stopRunning(){
        // [추적 1] 누군가 통신 중지 명령을 내림
        CsvController.writeLog("BLUETOOTH_CONNECT: 누군가 stopRunning()을 호출했습니다!")

        // [추적 2] 범인(호출자)의 위치를 역추적해서 로그에 남김
        val callerTrace = Exception().stackTrace.take(3).joinToString(" <- ") { it.methodName }
        CsvController.writeLog("STOP_CALLER: $callerTrace")

        isRunning = false;
    }

    fun isConnected(): Boolean {
        if (!::socket.isInitialized) {
            return false
        }
        return socket.isConnected
    }
}
