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

        /**
         * 블루투스 어댑터가 활성화되어 있는지 가볍게 체크
         * 배터리 최적화 시, 블루투스가 꺼져있다면 무거운 스캔 로직을 아예 시작하지 않도록 방어합니다.
         */
        fun isBluetoothEnabled(context: Context): Boolean {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return bluetoothManager.adapter?.isEnabled ?: false
        }

        fun connectedDevices(context: Context): MutableSet<BluetoothDevice>? {
            // [보강] 블루투스가 꺼져 있는 경우 미리 차단
            if (!isBluetoothEnabled(context)) {
                Log.e(TAG, "Bluetooth is disabled.")
                return null
            }

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

        fun checkAndLogConnection(context: Context, devices: MutableSet<BluetoothDevice>?) {
            if (devices == null) {
                // [보강] 연결된 기기 리스트가 아예 없을 때 로그
                logError("CheckConnection: No paired devices found.")
                return
            }

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
                    triggerVibration(context)
                    Toast.makeText(context, "Bluetooth 통신을 확인해주세요.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}")
            }
        }

        /**
         * 진동 로직을 별도 함수로 분리하여 가독성 향상 (API 31+ 대응)
         */
        private fun triggerVibration(context: Context) {
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
                true
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

        fun getUUID(context: Context, device: BluetoothDevice?): String {
            if (device == null) return ""
            if (!checkBluetoothPermission(context)) return ""

            return try {
                // [보강] uuids가 null인 경우에 대한 안전한 처리
                device.uuids?.firstOrNull()?.toString() ?: ""
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}")
                ""
            }
        }

        fun getConnectedDevice(context: Context, devices: MutableSet<BluetoothDevice>?): BluetoothDevice? {
            if (devices == null) return null
            if (!checkBluetoothPermission(context)) return null

            return try {
                devices.firstOrNull { isConnected(it) }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied: ${e.message}")
                null
            }
        }
    }
}