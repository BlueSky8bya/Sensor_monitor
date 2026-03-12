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
import com.gachon_HCI_Lab.user_mobile.common.CacheManager
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.common.DeviceInfo
import com.gachon_HCI_Lab.user_mobile.common.SocketState
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import com.gachon_HCI_Lab.user_mobile.service.AcceptService
import com.gachon_HCI_Lab.user_mobile.databinding.ActivitySensorBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import org.greenrobot.eventbus.EventBus
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
            sensorController.deleteAll()
            val sensorRootPath = File(CsvController.getDownloadPath(), "sensor_data").absolutePath
            CsvController.deleteFilesInDirectory(sensorRootPath)

            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
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
            val intent = Intent(this, AcceptService::class.java).apply {
                action = ACTION_STOP_LOCATION_SERVICE
            }
            startService(intent)

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1)
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        }
    }

    // [경고 해결] 불필요한 null 체크 제거 및 로직 완성
    private fun isLocationServiceRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        // getRunningServices는 여전히 deprecated 상태이나, 본인 서비스 확인용으로는 사용 가능
        @Suppress("DEPRECATION")
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (AcceptService::class.java.name == service.service.className) {
                if (service.foreground) return true
            }
        }
        return false
    }
}