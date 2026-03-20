package com.gachon_HCI_Lab.user_mobile.activity

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // 색상 변경을 위해 추가
import com.gachon_HCI_Lab.user_mobile.R // R 리소스 추가
import com.gachon_HCI_Lab.user_mobile.common.CacheManager
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.common.DeviceInfo
import com.gachon_HCI_Lab.user_mobile.common.SocketState
import com.gachon_HCI_Lab.user_mobile.common.SocketStateEvent
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import com.gachon_HCI_Lab.user_mobile.service.AcceptService
import com.gachon_HCI_Lab.user_mobile.databinding.ActivitySensorBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe // 추가
import org.greenrobot.eventbus.ThreadMode // 추가
import java.io.File

class SensorActivity : AppCompatActivity() {
    private lateinit var sensorController: SensorController
    private lateinit var binding: ActivitySensorBinding

    companion object {
        private const val ACTION_START_LOCATION_SERVICE = "startLocationService"
        private const val ACTION_STOP_LOCATION_SERVICE = "stopLocationService"
    }

    private val callback = object : OnBackPressedCallback(true) {
        var backPressedTime: Long = 0
        override fun handleOnBackPressed() {
            if (System.currentTimeMillis() - backPressedTime >= 2000) {
                backPressedTime = System.currentTimeMillis()
                Toast.makeText(this@SensorActivity, "한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
            } else {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DeviceInfo.init(
            intent.getStringExtra("DeviceID").toString(),
            intent.getStringExtra("ID").toString()
        )

        binding = ActivitySensorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sensorController = SensorController.getInstance(this)

        // Bluetooth 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 1)
        } else {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH), 1)
        }

        // 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TedPermission.create()
                .setPermissionListener(object : PermissionListener {
                    override fun onPermissionGranted() {}
                    override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                        Toast.makeText(this@SensorActivity, "알림 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                })
                .setDeniedMessage("알림 권한 허가가 필요합니다")
                .setPermissions(Manifest.permission.POST_NOTIFICATIONS)
                .check()
        }

        binding.stateLabel.text = SocketState.NONE.toString()
        binding.BtnStart.setOnClickListener { startLocationService() }
        binding.BtnStop.setOnClickListener { stopLocationService() }
        binding.BtnToChartActivity.setOnClickListener {
            startActivity(Intent(this, SensorChartActivity::class.java))
        }

        binding.BtnLogout.setOnClickListener {
            CacheManager.deleteCacheFile(this, "login.txt")

            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    // ── [수정 1] EventBus 주석 해제 (우체부 다시 고용) ──
    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    private fun startLocationService() {
        if (!isLocationServiceRunning()) {
            val intent = Intent(this, AcceptService::class.java).apply {
                action = ACTION_START_LOCATION_SERVICE
            }
            startService(intent)
            Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationService() {
        if (isLocationServiceRunning()) {
            // [수정 1] startService 대신 확실한 stopService() 호출
            val stopIntent = Intent(this, AcceptService::class.java)
            stopService(stopIntent)

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1)
            Toast.makeText(this, "수집 종료: 앱을 완전히 닫습니다.", Toast.LENGTH_SHORT).show()
        }

        // [수정 2] 앱을 백그라운드 프로세스까지 완전히 날려버리는 강력한 종료 로직
        finishAffinity() // 현재 쌓여있는 모든 화면(Activity)을 메모리에서 지움
        kotlin.system.exitProcess(0) // 안드로이드 시스템에서 이 앱의 프로세스 자체를 강제 킬(Kill)
    }

    private fun isLocationServiceRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (AcceptService::class.java.name == service.service.className) {
                if (service.foreground) return true
            }
        }
        return false
    }

    // SocketStateEvent로 받도록 파라미터 변경 ──
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSocketStateChanged(event: SocketStateEvent) {
        // 봉투 안에서 실제 상태(CONNECT, NONE)를 꺼냅니다
        val currentState = event.state

        // [추적 3] 화면 상태가 언제, 무엇으로 바뀌는지 기록
        CsvController.writeLog("UI_STATE_CHANGE: 화면 상태 변경 요청 들어옴 -> $currentState")

        // 1. 글씨 변경
        binding.stateLabel.text = currentState.toString()

        // 2. 색상 변경
        when (currentState) {
            SocketState.CONNECT -> {
                binding.stateLabel.setTextColor(ContextCompat.getColor(this, R.color.status_success))
            }
            SocketState.NONE -> {
                binding.stateLabel.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
            }
            else -> {
                binding.stateLabel.setTextColor(ContextCompat.getColor(this, R.color.status_info))
            }
        }
    }
}