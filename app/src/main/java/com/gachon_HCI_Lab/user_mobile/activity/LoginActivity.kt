package com.gachon_HCI_Lab.user_mobile.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gachon_HCI_Lab.user_mobile.common.BTManager
import com.gachon_HCI_Lab.user_mobile.common.CacheManager
import com.gachon_HCI_Lab.user_mobile.common.ServerConnection
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityLoginBinding
import androidx.core.net.toUri
import androidx.core.content.edit

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var isShowingDialog = false
    private val PREFS_NAME = "AppSetupPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        if (isShowingDialog) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isBatteryOptimized = pm.isIgnoringBatteryOptimizations(packageName)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isHibernationGuideShown = prefs.getBoolean("hibernation_shown", false)

        // [순차적 가이드 로직] - 앞 단계가 완료되지 않으면 절대 뒤로 넘어가지 않음
        when {
            // [1단계] 배터리 최적화 제외 설정
            !isBatteryOptimized -> {
                showBatteryOptimizationDialog()
            }
            // [2단계] 사용하지 않는 앱 관리 (1회만 유도)
            !isHibernationGuideShown -> {
                showAppHibernationDialog()
            }
            // [3단계] 필수 시스템 권한 (위치, 블루투스 등) 일괄 요청
            !hasRequiredPermissions() -> {
                requestRequiredPermissions()
            }
            // [4단계] 모든 세팅 완료 -> 기기 ID 획득 및 로그인 활성화
            else -> {
                val deviceID = fetchDeviceID()
                setupLoginLogic(deviceID)
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
    }

    @SuppressLint("BatteryLife")
    private fun showBatteryOptimizationDialog() {
        isShowingDialog = true
        AlertDialog.Builder(this)
            .setTitle("연결 무중단 설정")
            .setMessage("정교한 데이터 수집을 위해 시스템의 배터리 최적화 제외가 필요합니다.\n\n이어지는 시스템 안내 팝업에서 반드시 '허용'을 눌러주세요.")
            .setPositiveButton("설정하기") { _, _ ->
                isShowingDialog = false
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:$packageName".toUri()
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:$packageName".toUri()
                    }
                    startActivity(intent)
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showAppHibernationDialog() {
        isShowingDialog = true
        AlertDialog.Builder(this)
            .setTitle("장기 수집 안정화")
            .setMessage("앱을 열지 않는 시간에도 권한이 취소되지 않도록 설정합니다.\n\n화면 하단의 '사용하지 않는 앱 관리' 항목을 비활성화(OFF)해 주세요.")
            .setPositiveButton("이동하기") { _, _ ->
                isShowingDialog = false

                // [핵심] 설정창으로 보냈다는 기록을 남김 (다시 안 띄우기 위해)
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit { putBoolean("hibernation_shown", true) }

                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)

                // [삭제됨] 여기서 겹침을 유발하던 ServerConnection.login 삭제!
            }
            .setCancelable(false)
            .show()
    }

    private fun setupLoginLogic(deviceID: String) {
        binding.loginBtn.setOnClickListener {
            val inputId = binding.id.text.toString().trim()
            if (inputId.isNotEmpty()) {
                ServerConnection.login(inputId, deviceID = deviceID, context = this)
            } else {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // [안전] 1, 2, 3단계가 모두 끝나서야 비로소 자동 로그인이 확인됨
        val cache = CacheManager.loadCacheFile(this, "login.txt")
        if (cache != null && !isShowingDialog) {
            ServerConnection.login(cache, deviceID = deviceID, context = this)
        }
    }

    private fun fetchDeviceID(): String {
        val connectedDevices = BTManager.connectedDevices(this)
        val connectedDevice = BTManager.getConnectedDevice(this, connectedDevices)
        return BTManager.getUUID(this, connectedDevice)
    }
}