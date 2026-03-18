package com.gachon_HCI_Lab.user_mobile.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.gachon_HCI_Lab.user_mobile.R
import com.gachon_HCI_Lab.user_mobile.activity.SensorActivity
import com.gachon_HCI_Lab.user_mobile.common.BTManager
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.common.DeviceInfo
import com.gachon_HCI_Lab.user_mobile.common.ServerConnection
import com.gachon_HCI_Lab.user_mobile.common.SocketState
import com.gachon_HCI_Lab.user_mobile.common.SocketStateEvent
import com.gachon_HCI_Lab.user_mobile.common.ThreadState
import com.gachon_HCI_Lab.user_mobile.common.ThreadStateEvent
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import com.gachon_HCI_Lab.user_mobile.sensor.model.SensorEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

/**
 * 포그라운드 서비스
 * 웨어러블을 통해 수집된 데이터를 폰을 통해 수집 및 서버 전송
 */
class AcceptService : Service() {
    private var writeCount = 0
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var acceptThread: AcceptThread
    private val sensorController: SensorController = SensorController.getInstance(this@AcceptService)
    private var timer: Timer? = null
    private lateinit var bluetoothStateReceiver: BluetoothStateReceiver

    private val tag = "AcceptService"
    private var isConnected: Boolean = false

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!isBluetoothSupport(bluetoothAdapter)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (!bluetoothAdapter.isEnabled) {
                Toast.makeText(this, "블루투스를 활성화해주세요", Toast.LENGTH_SHORT).show()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        setForeground()
        startForeground()

        val connectedDevices = BTManager.connectedDevices(this)
        bluetoothStateReceiver = BluetoothStateReceiver(this, connectedDevices ?: mutableSetOf())
        val filter = IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
        if (::acceptThread.isInitialized) acceptThread.clear()
        timer?.cancel()
        timer = null
        
        EventBus.getDefault().post(SocketStateEvent(SocketState.CLOSE))
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForeground() {
        BluetoothConnect.createBluetoothAdapter(bluetoothAdapter)
        acceptThread = AcceptThread(this)
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
        acceptThread.start()
    }

    private fun setForeground() {
        val channelId = "com.user_mobile.AcceptService"
        val channelName = "Sensor Data Service"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, SensorActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Asan Service")
            .setContentText("센서 데이터 감지 중입니다")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun isBluetoothSupport(adapter: BluetoothAdapter?): Boolean {
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth를 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun csvWrite(time: Long) {
        timer?.cancel()
        timer = null
        // var count = 0 // 여기서 삭제
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    if (isConnected) {
                        sensorController.writeCsv("OneAxis")
                        sensorController.writeCsv("ThreeAxis")

                        writeCount++ // 멤버 변수 사용
                        if (writeCount >= 6) {
                            sendCSV()
                            writeCount = 0
                        }
                    }
                }
            }
        }, time, time)
    }

    /**
     * 30분 단위 파일 병합 및 서버 전송
     */
    @SuppressLint("DefaultLocale")
    private fun sendCSV() {
        val downloadBasePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

        for (sensor in SensorEnum.values()) {
            val sensorType = sensor.value.split("_").getOrNull(0) ?: continue
            val sensorDir = File("$downloadBasePath/sensor_data/$sensorType")
            val sendedDir = File(sensorDir, "sended")
            if (!sendedDir.exists()) sendedDir.mkdirs()
            if (!sensorDir.exists()) continue

            // 1. 병합 대상 파편 파일들 필터링
            val allFiles = sensorDir.listFiles()?.filter { file ->
                file.name.endsWith(".csv") && 
                file.name.contains(sensor.value) && 
                !file.name.matches(Regex("^\\d{8}_\\d{4}_.*")) // 이미 병합된 파일은 제외
            } ?: continue

            // 2. 30분 단위 그룹화
            val groupedFiles = allFiles.groupBy { file ->
                val timestamp = file.name.split("_").getOrNull(1)?.split(".")?.get(0)?.toLongOrNull() ?: 0L
                val cal = Calendar.getInstance().apply { time = Date(timestamp * 1000) }
                val block = if (cal.get(Calendar.MINUTE) < 30) "00" else "30"
                String.format("%04d%02d%02d_%02d%s",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, 
                    cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), block)
            }

            for ((blockTime, files) in groupedFiles) {
                if (files.isEmpty()) continue

                val mergedFileName = "${blockTime}_${sensor.value}.csv"
                val mergedFile = File(sensorDir, mergedFileName)
                val tempFile = File(sensorDir, "${mergedFileName}.tmp")

                try {
                    // 3. 파일 병합 수행
                    if (!mergedFile.exists()) {
                        tempFile.bufferedWriter().use { writer ->
                            val sortedFiles = files.sortedBy { it.name }
                            for (file in sortedFiles) {
                                file.bufferedReader().use { reader ->
                                    reader.lineSequence().forEachIndexed { index, line ->
                                        if (index == 0 && file != sortedFiles.first()) return@forEachIndexed
                                        writer.write(line)
                                        writer.newLine()
                                    }
                                }
                            }
                        }
                        
                        // [공간 절약 핵심 1] 병합 성공 직후 원본 파편들 즉시 삭제
                        if (tempFile.renameTo(mergedFile)) {
                            files.forEach { if (it.exists()) it.delete() }
                            Log.d(tag, "병합 완료: 원본 파편 파일 ${files.size}개 삭제됨")
                        } else {
                            if (tempFile.exists()) tempFile.delete()
                            continue 
                        }
                    }

                    // 4. 서버 전송
                    val epochTime = try {
                        SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).parse(blockTime)?.time?.div(1000) ?: 0L
                    } catch (_: Exception) { 0L }

                    ServerConnection.postFile(mergedFile, DeviceInfo._uID, DeviceInfo._battery, epochTime.toString()) { isSuccess ->
                        if (isSuccess) {
                            // [공간 절약 핵심 2] 전송 성공 시 병합본을 sended로 이동 후 원본 경로에서 완벽 제거
                            val destFile = File(sendedDir, mergedFileName)
                            
                            // 이동 시도, 실패 시 복사 후 삭제(확실한 제거)
                            val moved = mergedFile.renameTo(destFile)
                            if (!moved) {
                                try {
                                    mergedFile.copyTo(destFile, overwrite = true)
                                    mergedFile.delete()
                                    Log.d(tag, "병합 파일 강제 이동 및 삭제 완료")
                                } catch (e: Exception) {
                                    Log.e(tag, "파일 정리 오류: ${e.message}")
                                }
                            } else {
                                Log.d(tag, "병합 파일 sended 폴더 이동 완료")
                            }
                        }
                        recordBatteryLevel(DeviceInfo._battery.toIntOrNull() ?: 0)
                    }

                } catch (e: Exception) {
                    Log.e(tag, "파일 처리 중 예외 발생: ${e.message}")
                    if (tempFile.exists()) tempFile.delete()
                }
            }
        }
    }

    private fun recordBatteryLevel(batteryLevel: Int) {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logFile = File(path, "battery.txt")
        try {
            FileWriter(logFile, true).use { it.appendLine("Battery Level: $batteryLevel% at ${Date()}") }

            if (batteryLevel <= 10) {
                // UI 작업은 메인 스레드에서 실행되도록 보장
                Handler(Looper.getMainLooper()).post {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(VIBRATOR_SERVICE) as Vibrator
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(2500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(2500)
                    }
                    
                    Toast.makeText(this, "배터리가 부족합니다. 충전해주세요.", Toast.LENGTH_SHORT).show()
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    Toast.makeText(this, "워치를 재착용하고 측정을 재시작 해주세요.", Toast.LENGTH_LONG).show()
                }, 90 * 60 * 1000)
            }
        } catch (e: IOException) {
            Log.e(tag, "Battery log error: ${e.message}")
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun listenSocketState(event: ThreadStateEvent) {
        isConnected = when (event.state) {
            ThreadState.RUN -> {
                CsvController.writeLog("CONNECTED: 워치와 소켓 연결 성공") // 로그 기록
                csvWrite(1000 * 60 * 5)
                EventBus.getDefault().post(SocketStateEvent(SocketState.CONNECT))
                true
            }
            else -> {
                val reason = if (event.state == ThreadState.STOP) "정상 종료" else "비정상 끊김"
                CsvController.writeLog("DISCONNECTED: 연결 끊김 ($reason)") // 로그 기록
                timer?.cancel()
                timer = null
                EventBus.getDefault().post(SocketStateEvent(SocketState.NONE))
                false
            }
        }
    }
}