package com.gachon_HCI_Lab.user_mobile.common

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.lang.reflect.Method
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * [BTManager]
 * 블루투스 관련 기능을 담당하는 싱글톤 클래스
 * BluetoothDevice의 모든 데이터들은 접근시에 permission check가 필요하기 때문에
 * 코드양이 늘어날 것을 고려하여 몇몇 기능들은 해당 클래스에 모아둘 예정이다.
 */

class BTManager {
    companion object {
        private val tag = "BluetoothManager"
        val uuidFixed = arrayOf("00001101", "0000", "1000", "8000", "00805F9B34FB")

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
                Log.e(tag, "Permission denied: ${e.message}")
                null
            }
        }

        fun isConnected(device: BluetoothDevice): Any? {
            return try {
                val m: Method = device.javaClass.getMethod("isConnected")
                m.invoke(device)
            } catch (e: Exception) {
                Log.e(tag, "Error checking connection: ${e.message}")
                null
            }
        }

        fun getUUID(context: Context, device: BluetoothDevice?): String {
            if (device == null) return ""

            if (!checkBluetoothPermission(context)) {
                requestBluetoothPermission(context)
                return ""
            }

            return try {
                var dUuid = ""
                for (uuid in device.uuids) {
                    val splitted = uuid.toString().split("-")

                    if (splitted[1].compareTo(uuidFixed[1]) == 0) continue
                    if (splitted[2].compareTo(uuidFixed[2]) == 0) continue
                    if (splitted[3].compareTo(uuidFixed[3]) == 0) continue
                    if (splitted[4].compareTo(uuidFixed[4]) == 0) continue

                    dUuid = uuid.toString()
                    break
                }
                dUuid
            } catch (e: SecurityException) {
                Log.e(tag, "Permission denied: ${e.message}")
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
                    if (isConnected(device) == true) {
                        Log.d(tag, "Connected Device : ${device.name}")
                        return device
                    }
                }
                null
            } catch (e: SecurityException) {
                Log.e(tag, "Permission denied: ${e.message}")
                null
            }
        }

        fun checkAndLogConnection(context: Context, devices: MutableSet<BluetoothDevice>?) {
            if (devices == null) return

            if (!checkBluetoothPermission(context)) {
                requestBluetoothPermission(context)
                return
            }

            try {
                var allDevicesConnected = true
                for (device in devices) {
                    if (isConnected(device) != true) {
                        logError("Device disconnected: ${device.name}")
                        allDevicesConnected = false
                    }
                }

                if (!allDevicesConnected) {
                    // 진동 알림
                    val vibrator = context.getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(2500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vibrator.vibrate(2500)
                    }

                    // Toast 메시지
                    Toast.makeText(context, "Bluetooth 통신을 확인해주세요.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e(tag, "Permission denied: ${e.message}")
            }
        }

        private fun logError(message: String) {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logFile = File(path, "Error_log.txt")
            try {
                val writer = FileWriter(logFile, true)
                writer.appendLine(message)
                writer.close()
            } catch (e: IOException) {
                Log.e(tag, "Error writing log: ${e.message}")
            }
        }

        private fun checkBluetoothPermission(context: Context): Boolean {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }

        private fun requestBluetoothPermission(context: Context) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1
            )
        }
    }
}
