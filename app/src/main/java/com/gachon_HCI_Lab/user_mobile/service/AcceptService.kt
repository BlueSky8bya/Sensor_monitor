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
import android.os.IBinder
import android.os.PowerManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Timer
import java.util.TimerTask

/**
 * 포그라운드 서비스
 * 웨어러블을 통해 수집된 데이터를 폰을 통해 수집 및 서버 전송
 */
class AcceptService : Service() {
    companion object {
        private const val INTERVAL_MS = 1000L * 60 * 5 // 5분 주기
    }
    private var wakeLock: PowerManager.WakeLock? = null
    private val tag = "AcceptService"
    private var isConnected: Boolean = false

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var acceptThread: AcceptThread
    private val sensorController: SensorController = SensorController.getInstance(this@AcceptService)

    private var timer: Timer? = null
    private var mergeTimer: Timer? = null
    private var watchdogTimer: Timer? = null
    private lateinit var bluetoothStateReceiver: BluetoothStateReceiver

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        CsvController.writeLog("[SYS] onTrimMemory (Level: $level)")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        CsvController.writeLog("[SYS] onLowMemory (OOM Risk)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UserMobile::SensorWakeLock")
        wakeLock?.acquire()

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

        CsvController.writeLog("[SVC] onStartCommand (flags: $flags, startId: $startId)")

        setForeground()
        startForeground()

        val connectedDevices = BTManager.connectedDevices(this)
        bluetoothStateReceiver = BluetoothStateReceiver(this, connectedDevices ?: mutableSetOf())
        val filter = IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        return START_STICKY
    }

    override fun onDestroy() {
        CsvController.writeLog("[SVC] onDestroy")

        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
        if (::acceptThread.isInitialized) acceptThread.clear()

        timer?.cancel()
        timer = null
        mergeTimer?.cancel()
        mergeTimer = null
        watchdogTimer?.cancel()
        watchdogTimer = null

        EventBus.getDefault().post(SocketStateEvent(SocketState.CLOSE))
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        CsvController.writeLog("[SVC] onTaskRemoved")
        super.onTaskRemoved(rootIntent)
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
        timer = Timer()
        CsvController.writeLog("[TIMER] csvWrite (5분 주기) 시작")

        timer?.schedule(object : TimerTask() {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    if (isConnected) {
                        CsvController.writeLog("[TIMER] csvWrite 실행: DB -> CSV 분할 저장")
                        sensorController.writeCsv("OneAxis")
                        sensorController.writeCsv("ThreeAxis")
                    } else {
                        CsvController.writeLog("[TIMER] csvWrite 스킵 (Not Connected)")
                    }
                }
            }
        }, time, time)
    }

    @SuppressLint("DefaultLocale")
    private suspend fun sendCSV() {
        val downloadBasePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        CsvController.writeLog("[MERGE] sendCSV() 진입: 30분 단위 병합 및 전송")

        val calendar = java.util.Calendar.getInstance()
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val fixedMinute = if (minute >= 30) "30" else "00"
        val dateFormat = java.text.SimpleDateFormat("yyMMdd", java.util.Locale.getDefault())
        val hourFormat = java.text.SimpleDateFormat("HH", java.util.Locale.getDefault())

        val dateStr = dateFormat.format(calendar.time)
        val hourStr = hourFormat.format(calendar.time)
        val timeStamp = "${hourStr}${fixedMinute}" // 예: 1600, 1830

        for (sensor in com.gachon_HCI_Lab.user_mobile.sensor.model.SensorEnum.values()) {
            val sensorType = sensor.value.split("_").getOrNull(0) ?: continue
            val sensorDir = java.io.File("$downloadBasePath/sensor_data/$sensorType")
            val sendedDir = java.io.File(sensorDir, "sended")
            if (!sendedDir.exists()) sendedDir.mkdirs()

            val allFiles = sensorDir.listFiles()?.filter { file ->
                file.name.endsWith(".csv") &&
                        file.name.contains(sensor.value) &&
                        !file.name.contains("merged") &&
                        file.name.contains(Regex("\\d{6}_\\d+"))
            } ?: continue

            if (allFiles.isNotEmpty()) {
                val sortedFiles = allFiles.sortedBy { it.name }
                val mergedFileName = "${sensor.value}_${dateStr}_${timeStamp}.csv"
                val mergedFile = java.io.File(sensorDir, mergedFileName)

                CsvController.writeLog("[MERGE] ${sensor.value}: 조각 파일 ${sortedFiles.size}개 병합 -> $mergedFileName")

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
                            kotlinx.coroutines.delay(10)
                        }
                    }

                    val destFile = java.io.File(sendedDir, mergedFileName)

                    if (mergedFile.renameTo(destFile)) {
                        CsvController.writeLog("[MERGE] ${sensor.value}: 병합 완료 및 sended 이동")

                        val epochTime = System.currentTimeMillis() / 1000L
                        val userID = com.gachon_HCI_Lab.user_mobile.common.DeviceInfo._uID
                        val battery = com.gachon_HCI_Lab.user_mobile.common.DeviceInfo._battery

                        CsvController.writeLog("[UPLOAD] 전송 시도: $mergedFileName (UID: $userID, BAT: $battery)")

                        com.gachon_HCI_Lab.user_mobile.common.ServerConnection.postFile(destFile, userID, battery, epochTime.toString()) { isSuccess ->
                            if (isSuccess) {
                                CsvController.writeLog("[UPLOAD] 전송 성공: $mergedFileName (조각 파일 ${sortedFiles.size}개 삭제)")
                                sortedFiles.forEach { it.delete() }
                            } else {
                                CsvController.writeLog("[UPLOAD] 전송 실패: $mergedFileName (조각 파일 보존)")
                            }
                        }
                    } else {
                        CsvController.writeLog("[MERGE] 이동 실패: $mergedFileName (병합본 삭제)")
                        if (mergedFile.exists()) mergedFile.delete()
                    }

                    kotlinx.coroutines.delay(300)
                } catch (e: Exception) {
                    CsvController.writeLog("[MERGE] Exception (${sensor.value}): ${e.message}")
                    if (mergedFile.exists()) mergedFile.delete()
                }
            } else {
                Log.d(tag, "[MERGE] ${sensor.value}: 병합할 조각 파일 없음")
            }
        }
    }

    private fun startMergeTimer() {
        mergeTimer?.cancel()
        mergeTimer = Timer()
        val checkPeriod = 1000L * 60 // 1분마다 체크
        CsvController.writeLog("[TIMER] MergeTimer (1분 주기 감시) 시작")

        mergeTimer?.schedule(object : TimerTask() {
            override fun run() {
                val calendar = java.util.Calendar.getInstance()
                val minute = calendar.get(java.util.Calendar.MINUTE)

                if (isConnected && (minute == 0 || minute == 30)) {
                    CsvController.writeLog("[TIMER] MergeTimer 트리거 (${minute}분): sendCSV() 호출")
                    CoroutineScope(Dispatchers.Default).launch {
                        sendCSV()
                    }
                }
            }
        }, checkPeriod, checkPeriod)
    }

    private fun startWatchdogTimer() {
        watchdogTimer?.cancel()
        watchdogTimer = Timer()
        watchdogTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (isConnected && ::acceptThread.isInitialized) {
                    val idleTime = System.currentTimeMillis() - acceptThread.lastReadTime
                    if (idleTime > 15000) {
                        CsvController.writeLog("[WATCHDOG] 데이터 수신 지연 감지 (>15초)")
                    }
                }
            }
        }, 10000, 10000)
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun listenSocketState(event: ThreadStateEvent) {
        isConnected = when (event.state) {
            ThreadState.RUN -> {
                CsvController.writeLog("[BT_STATE] CONNECTED (타이머 시작)")
                csvWrite(INTERVAL_MS)
                startMergeTimer()
                startWatchdogTimer()
                EventBus.getDefault().postSticky(SocketStateEvent(SocketState.CONNECT))
                true
            }
            else -> {
                CsvController.writeLog("[BT_STATE] DISCONNECTED (상태: ${event.state}, 모든 타이머 취소)")
                timer?.cancel()
                timer = null
                watchdogTimer?.cancel()
                watchdogTimer = null
                mergeTimer?.cancel()
                mergeTimer = null

                EventBus.getDefault().postSticky(SocketStateEvent(SocketState.CLOSE))
                false
            }
        }
    }
}