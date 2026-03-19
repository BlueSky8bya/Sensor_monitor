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
import com.gachon_HCI_Lab.user_mobile.common.SocketState
import com.gachon_HCI_Lab.user_mobile.common.SocketStateEvent
import com.gachon_HCI_Lab.user_mobile.common.ThreadState
import com.gachon_HCI_Lab.user_mobile.common.ThreadStateEvent
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import com.gachon_HCI_Lab.user_mobile.sensor.model.SensorEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Date
import java.util.Timer
import java.util.TimerTask

/**
 * 포그라운드 서비스
 * 웨어러블을 통해 수집된 데이터를 폰을 통해 수집 및 서버 전송
 */
class AcceptService : Service() {
    companion object {
        /**
         * [설정 변수] 상수는 companion object 안에 const val로 선언하는 것이 정석입니다.
         */
        //private val INTERVAL_MS = 1000L * 10 // 10초 주기 (테스트용)
        private const val INTERVAL_MS = 1000L * 60 * 5 // 5분 주기

        // MERGE_COUNT는 이제 시간 기준이므로 사용하지 않지만 구조 유지를 위해 둠
        private const val MERGE_COUNT = 1
    }
    private val tag = "AcceptService"
    private var isConnected: Boolean = false

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var acceptThread: AcceptThread
    private val sensorController: SensorController = SensorController.getInstance(this@AcceptService)
    private var timer: Timer? = null
    private lateinit var bluetoothStateReceiver: BluetoothStateReceiver

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

    /**
     * 5분마다 DB 데이터를 추출하여 CSV 파편 파일을 생성
     */
    private fun csvWrite(time: Long) {
        timer?.cancel()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    if (isConnected) {
                        Log.d(tag, "5분 주기 데이터 추출 및 CSV 쓰기 시작")
                        sensorController.writeCsv("OneAxis")
                        sensorController.writeCsv("ThreeAxis")
                    }
                }
            }
        }, time, time)
    }

    /**
     * 파일 병합 및 서버 전송 (Suspend 함수)
     */
    @SuppressLint("DefaultLocale")
    private suspend fun sendCSV() {
        val downloadBasePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        CsvController.writeLog("DEBUG: sendCSV 병합 프로세스 시작")

        for (sensor in SensorEnum.values()) {
            val sensorType = sensor.value.split("_").getOrNull(0) ?: continue
            val sensorDir = File("$downloadBasePath/sensor_data/$sensorType")
            val sendedDir = File(sensorDir, "sended")
            if (!sendedDir.exists()) sendedDir.mkdirs()

            // 1. 파편 파일 필터링
            val allFiles = sensorDir.listFiles()?.filter { file ->
                file.name.endsWith(".csv") &&
                        file.name.contains(sensor.value) &&
                        !file.name.contains("M_") &&
                        file.name.contains(Regex("\\d{8}_\\d+"))
            } ?: continue

            // 2. 파일이 하나라도 있으면 병합 시도 (시간 기준이므로 개수 제한 해제)
            if (allFiles.isNotEmpty()) {
                val sortedFiles = allFiles.sortedBy { it.name }
                val targetTime = sortedFiles.last().name.substringAfterLast("_").substringBefore(".csv")
                val mergedFileName = "${sensor.value}_${targetTime}_merged.csv"
                val mergedFile = File(sensorDir, mergedFileName)

                try {
                    mergedFile.bufferedWriter().use { writer ->
                        for (file in sortedFiles) {
                            file.bufferedReader().use { reader ->
                                reader.lineSequence().forEachIndexed { index, line ->
                                    if (index == 0 && file != sortedFiles.first()) return@forEachIndexed
                                    writer.write(line)
                                    writer.newLine()
                                }
                            }
                            delay(10) // I/O 부하 분산
                        }
                    }

                    // 원본 삭제 및 이동
                    sortedFiles.forEach { it.delete() }
                    val destFile = File(sendedDir, mergedFileName)
                    if (mergedFile.renameTo(destFile)) {
                        CsvController.writeLog("SUCCESS: $mergedFileName 병합 및 이동 완료")
                    }
                    delay(300) // 센서 간 간격
                } catch (e: Exception) {
                    CsvController.writeLog("ERROR: 병합 실패 - ${e.message}")
                }
            }
        }
    }

    /**
     * 매 분마다 시스템 시계를 확인하여 00분 또는 30분에 병합 실행
     */
    private fun startMergeTimer() {
        val mergeTimer = Timer()
        val checkPeriod = 1000L * 60 // 1분마다 체크

        mergeTimer.schedule(object : TimerTask() {
            override fun run() {
                val calendar = java.util.Calendar.getInstance()
                val minute = calendar.get(java.util.Calendar.MINUTE)

                // [핵심 수정] 실제 환경에 맞게 정각(00) 또는 30분일 때만 병합
                if (isConnected && (minute == 0 || minute == 30)) {
                    CoroutineScope(Dispatchers.Default).launch {
                        CsvController.writeLog("SYSTEM_EVENT: 정기 병합 시간 도달 (${minute}분)")
                        sendCSV()
                    }
                }
            }
        }, checkPeriod, checkPeriod)
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
                CsvController.writeLog("CONNECTED: 연결 성공")
                csvWrite(INTERVAL_MS)
                startMergeTimer() // 타이머 시작
                EventBus.getDefault().post(SocketStateEvent(SocketState.CONNECT))
                true
            }
            else -> {
                timer?.cancel()
                timer = null
                false
            }
        }
    }
}