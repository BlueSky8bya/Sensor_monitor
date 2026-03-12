package com.gachon_HCI_Lab.user_mobile.common

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.lang.reflect.Method

class BTManager {
    companion object {
        private const val TAG = "BluetoothManager"

        fun connectedDevices(context: Context): MutableSet<BluetoothDevice>? {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (!checkBluetoothPermission(context)) {
                requestBluetoothPermission(context)
                return null
            }

            return try {
                bluetoothAdapter.bondedDevices
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}")
                null
            }
        }

        fun isConnected(device: BluetoothDevice): Boolean {
            return try {
                val m: Method = device.javaClass.getMethod("isConnected")
                m.invoke(device) as Boolean
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection: ${e.message}")
                false
            }
        }

        // [경고 해결] VibratorManager 사용 및 진동 알림 로직 개선
        fun checkAndLogConnection(context: Context, devices: MutableSet<BluetoothDevice>?) {
            if (devices == null) return

            if (!checkBluetoothPermission(context)) {
                requestBluetoothPermission(context)
                return
            }

            try {
                var allDevicesConnected = true
                for (device in devices) {
                    if (!isConnected(device)) {
                        logError("Device disconnected: ${device.name}")
                        allDevicesConnected = false
                    }
                }

                if (!allDevicesConnected) {
                    // 진동 서비스 획득 (API 31+ 대응)
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(2500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(2500)
                    }

                    Toast.makeText(context, "Bluetooth 통신을 확인해주세요.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}")
            }
        }

        private fun logError(message: String) {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logFile = File(path, "Error_log.txt")
            try {
                FileWriter(logFile, true).use { writer ->
                    writer.appendLine("${System.currentTimeMillis()}: $message")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing log: ${e.message}")
            }
        }

        private fun checkBluetoothPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true // 낮은 버전에서는 BLUETOOTH 권한이 일반 권한임
            }
        }

        private fun requestBluetoothPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    1
                )
            }
        }

        // BTManager.kt 의 companion object 내부에 추가
        fun getUUID(context: Context, device: BluetoothDevice?): String {
            if (device == null) return ""

            if (!checkBluetoothPermission(context)) {
                requestBluetoothPermission(context)
                return ""
            }

            return try {
                var dUuid = ""
                if (device.uuids != null) {
                    for (uuid in device.uuids) {
                        // uuidFixed를 안 쓰기로 했으므로 바로 문자열로 반환하거나 특정 로직 수행
                        dUuid = uuid.toString()
                        break
                    }
                }
                dUuid
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}")
                ""
            }
        }

        fun getConnectedDevice(context: Context, devices: MutableSet<BluetoothDevice>?): BluetoothDevice? {
            if (devices == null) return null

            if (!checkBluetoothPermission(context)) {
                requestBluetoothPermission(context)
                return null
            }

            return try {
                for (device in devices) {
                    if (isConnected(device)) {
                        Log.d(TAG, "Connected Device : ${device.name}")
                        return device
                    }
                }
                null
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}")
                null
            }
        }
    }
}