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
import com.gachon_HCI_Lab.user_mobile.common.SocketStateEvent
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import com.gachon_HCI_Lab.user_mobile.service.AcceptService
import com.gachon_HCI_Lab.user_mobile.databinding.ActivitySensorBinding
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe


class SensorActivity() : AppCompatActivity() {
    private lateinit var serviceIntent : Intent
    private lateinit var sensorController: SensorController
    private lateinit var binding: ActivitySensorBinding

    private val ACTION_START_LOCATION_SERVICE = "startLocationService"
    private val ACTION_STOP_LOCATION_SERVICE = "stopLocationService"

    /**
     * 뒤로가기 연속으로 누르면 앱 종료
     **/
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

        //로그인 페이지에서 온 id들 가져오기
        DeviceInfo.init(intent.getStringExtra("DeviceID").toString(),
            intent.getStringExtra("ID").toString())

//        setContentView(R.layout.activity_sensor)
        binding = ActivitySensorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sensorController = SensorController.getInstance(this)

        // 권한 허가
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 1)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH), 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TedPermission.create()
                .setPermissionListener(object: PermissionListener {
                    override fun onPermissionGranted() {
                        Toast.makeText(this@SensorActivity,
                            "알림 권한 허가",
                            Toast.LENGTH_SHORT).show()
                    }

                    override fun onPermissionDenied(deniedPermissions: MutableList<String>?) {
                        Toast.makeText(this@SensorActivity,
                            "알림 권한 허가",
                            Toast.LENGTH_SHORT).show()
                    }
                })
                .setDeniedMessage("알림 권한 허가가 필요합니다")
                .setPermissions(Manifest.permission.POST_NOTIFICATIONS)
                .check()
        }

        // 화면
        binding.stateLabel.text = SocketState.NONE.toString()
        binding.BtnStart.setOnClickListener {
            startLocationService()
        }
        binding.BtnStop.setOnClickListener {
            stopLocationService()
        }
//        binding.BtnCsvCheck.setOnClickListener {
//            val intent = Intent(this, CsvPopupActivity::class.java)
//            startActivity(intent)
//        }
        binding.BtnToChartActivity.setOnClickListener {
            val intent = Intent(this, SensorChartActivity::class.java)
            startActivity(intent)
        }

        //로그아웃 버튼
        binding.BtnLogout.setOnClickListener{
            CacheManager.deleteCacheFile(this,"login.txt")

            // RoomDB초기화 하는 코드 추가해야함
            // 로그아웃한 경우는 이용자가 변경된 경우이므로 내부 정보를 모두 초기화 해야함
            
            //RoomDB 전체 삭제 오류가 있음
            SensorController.getInstance(this).deleteAll()
            CsvController.deleteFilesInDirectory(CsvController.getExternalPath(this))

            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        this.onBackPressedDispatcher.addCallback(this, callback)
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
            val intent = Intent(this, AcceptService::class.java)
            intent.action = ACTION_START_LOCATION_SERVICE
            this.startService(intent)
            Toast.makeText(this, "Service started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopLocationService() {
        if (isLocationServiceRunning()) {
            val intent = Intent(this, AcceptService::class.java)
            intent.action = ACTION_STOP_LOCATION_SERVICE
            this.stopService(intent)

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(1) // cancel(알림 특정 id)
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationServiceRunning(): Boolean {
        val activityManager = this.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (activityManager != null) {
            for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
                if (AcceptService::class.java.name == service.service.className) {
                    if (service.foreground) {
                        return true
                    }
                }
            }
        }
        return false
    }

    @Subscribe
    fun listenSocketState(event: SocketStateEvent) {
        val state = event.state.name
        runOnUiThread {
            binding.stateLabel.text = state
        }
    }

//    @Subscribe
//    fun listenThreadState(event: ThreadStateEvent) {
//        if (ThreadState.STOP == event.state) {
//            Log.d("스레드 상태 감지", "서비스 종료")
//            val intent = Intent(this, AcceptService::class.java)
//            stopService(intent)
//            EventBus.getDefault().post(SocketStateEvent(SocketState.NONE))
//        }
//    }

}
