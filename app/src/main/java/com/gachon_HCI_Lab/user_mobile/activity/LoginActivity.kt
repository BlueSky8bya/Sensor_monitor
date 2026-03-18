package com.gachon_HCI_Lab.user_mobile.activity

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gachon_HCI_Lab.user_mobile.common.BTManager
import com.gachon_HCI_Lab.user_mobile.common.ServerConnection
import com.gachon_HCI_Lab.user_mobile.common.CacheManager
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityLoginBinding
import java.io.File

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 배터리 최적화 상태 체크 및 안내 팝업
        checkBatteryOptimization()

        // 2. 오래된 센서 데이터 정리
        cleanOldSensorData()

        // 3. 기기 식별값 가져오기 (연결된 기기 확인 로직 포함)
        val deviceID = fetchDeviceID()

        // 4. 로그인 로직 설정
        setupLoginLogic(deviceID)
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            showBatteryGuidanceDialog()
        }
    }

    private fun showBatteryGuidanceDialog() {
        AlertDialog.Builder(this)
            .setTitle("배터리 최적화 제외 설정 안내")
            .setMessage("실시간 센서 데이터 수집이 끊기지 않도록 배터리 최적화 제외 설정이 필요합니다.\n\n확인을 누르시면 설정 화면으로 이동합니다.")
            .setPositiveButton("설정하러 가기") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("LoginActivity", "배터리 설정 화면 이동 실패", e)
                }
            }
            .setNegativeButton("나중에 하기", null)
            .setCancelable(false)
            .show()
    }

    private fun fetchDeviceID(): String {
        val connectedDevices = BTManager.connectedDevices(this)
        val connectedDevice = BTManager.getConnectedDevice(this, connectedDevices)
        val deviceID = BTManager.getUUID(this, connectedDevice)
        Log.d("LoginActivity", "deviceID: $deviceID")
        return deviceID
    }

    private fun cleanOldSensorData() {
        val sensorRootPath = File(CsvController.getDownloadPath(), "sensor_data").absolutePath
        // CsvController.deleteFilesInDirectory(sensorRootPath)
    }

    private fun setupLoginLogic(deviceID: String) {
        // 1. 수동 로그인 버튼 기능은 무조건 먼저 달아줍니다.
        binding.loginBtn.setOnClickListener {
            val inputId = binding.id.text.toString().trim()
            if (inputId.isNotEmpty()) {
                ServerConnection.login(inputId, deviceID = deviceID, context = this)
            } else {
                Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. 그 다음, 로그인 캐시가 있다면 자동 로그인을 찔러봅니다.
        val cache = CacheManager.loadCacheFile(this, "login.txt")
        if (cache != null) {
            ServerConnection.login(cache, deviceID = deviceID, context = this)
        }
    }
}