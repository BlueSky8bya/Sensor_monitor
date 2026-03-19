package com.gachon_HCI_Lab.user_mobile.common

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.lang.reflect.Method

class BTManager {
    companion object {
        private const val TAG = "BluetoothManager"

        fun isBluetoothEnabled(context: Context): Boolean {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            return bluetoothManager.adapter?.isEnabled ?: false
        }

        fun connectedDevices(context: Context): MutableSet<BluetoothDevice>? {
            if (!isBluetoothEnabled(context)) {
                Log.e(TAG, "Bluetooth is disabled.")
                return null
            }

            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            if (!checkBluetoothPermission(context)) {
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
                // [수정] 통합 로그 시스템 사용
                CsvController.writeLog("BT_ERROR: CheckConnection - No paired devices found.")
                return
            }

            if (!checkBluetoothPermission(context)) return

            try {
                var allDevicesConnected = true
                for (device in devices) {
                    if (!isConnected(device)) {
                        // [수정] 통합 로그 시스템 사용
                        CsvController.writeLog("BT_DISCONNECTED: ${device.name}")
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

        // [삭제] logError 함수는 더 이상 필요 없으므로 제거했습니다.

        private fun checkBluetoothPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun getUUID(context: Context, device: BluetoothDevice?): String {
            if (device == null) return ""
            if (!checkBluetoothPermission(context)) return ""

            return try {
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