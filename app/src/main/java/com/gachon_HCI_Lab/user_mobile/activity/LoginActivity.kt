package com.gachon_HCI_Lab.user_mobile.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gachon_HCI_Lab.user_mobile.common.BTManager
import com.gachon_HCI_Lab.user_mobile.common.CacheManager
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.common.ServerConnection
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityLoginBinding
import java.io.File
import androidx.core.net.toUri

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var isBatteryConfigDone = false
    private var isShowingDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cleanOldSensorData()
    }

    override fun onResume() {
        super.onResume()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        isBatteryConfigDone = pm.isIgnoringBatteryOptimizations(packageName)

        val deviceID = fetchDeviceID()
        setupLoginLogic(deviceID)

        // 1순위: 배터리 설정이 안 되어 있다면 배터리 팝업
        if (!isBatteryConfigDone) {
            if (!isShowingDialog) showBatteryOptimizationDialog()
        }
        // 2순위: 배터리는 됐는데 '사용하지 않는 앱 관리' 안내가 필요할 때 (선택 사항이지만 권장)
        // 이 팝업은 사용자가 직접 꺼야 하므로, 배터리 설정이 완료된 직후 한 번만 띄우는 것이 좋습니다.
    }

    /**
     * [1단계] 배터리 제한 없음 설정 안내
     */
    @SuppressLint("BatteryLife")
    private fun showBatteryOptimizationDialog() {
        isShowingDialog = true
        val message = "안정적인 데이터 수집을 위해 반드시 필요한 설정입니다.\n\n• [확인] 클릭 후 뜨는 팝업에서\n• 반드시 **[허용]**을 선택해 주세요."

        AlertDialog.Builder(this)
            .setTitle("⚠️ 배터리 최적화 제외 설정")
            .setMessage(message)
            .setPositiveButton("확인") { _, _ ->
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

    /**
     * [2단계] 사용하지 않는 앱 관리 토글 안내
     * 배터리 설정이 완료된 후, 로그인 버튼을 누를 때 가이드하거나
     * 혹은 배터리 설정 직후에 이어서 띄워줄 수 있습니다.
     */
    private fun showAppHibernationDialog() {
        isShowingDialog = true
        val message = StringBuilder().apply {
            append("장기간 미사용 시에도 연결을 유지하기 위한 설정입니다.\n\n")
            append("1. [확인] 클릭 후 아래로 스크롤\n")
            append("2. **'사용하지 않는 앱 관리'** 토글을 **해제(OFF)**해 주세요.")
        }.toString()

        AlertDialog.Builder(this)
            .setTitle("⚠️ 권한 유지 설정")
            .setMessage(message)
            .setPositiveButton("확인") { _, _ ->
                isShowingDialog = false
                try {
                    // 상택님이 올려주신 '애플리케이션 정보' 화면으로 바로 보냅니다.
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:$packageName".toUri()
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun fetchDeviceID(): String {
        val connectedDevices = BTManager.connectedDevices(this)
        val connectedDevice = BTManager.getConnectedDevice(this, connectedDevices)
        return BTManager.getUUID(this, connectedDevice)
    }

    private fun cleanOldSensorData() {
        val sensorRootPath = File(CsvController.getDownloadPath(), "sensor_data").absolutePath
    }

    private fun setupLoginLogic(deviceID: String) {
        binding.loginBtn.setOnClickListener {
            if (isBatteryConfigDone) {
                // 배터리 설정은 됐는데, 아직 '사용하지 않는 앱 관리'를 안 껐을 수도 있으므로
                // 여기서 한 번 더 가이드 팝업을 띄워주는 것이 연구 데이터 유실 방지에 좋습니다.
                val inputId = binding.id.text.toString().trim()
                if (inputId.isNotEmpty()) {
                    // 로그인 진행 전, 앱 관리 안내 팝업을 띄우고 사용자가 확인하면 로그인하게 하거나
                    // 아니면 쿨하게 바로 로그인을 시키되 팝업만 한 번 보여줍니다.
                    ServerConnection.login(inputId, deviceID = deviceID, context = this)

                    // 로그인이 성공적으로 요청되면 마지막으로 권한 유지 설정을 권장합니다.
                    showAppHibernationDialog()
                } else {
                    Toast.makeText(this, "아이디를 입력해주세요.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "먼저 배터리 최적화 제외를 허용해주세요.", Toast.LENGTH_SHORT).show()
                showBatteryOptimizationDialog()
            }
        }

        val cache = CacheManager.loadCacheFile(this, "login.txt")
        if (cache != null && isBatteryConfigDone) {
            ServerConnection.login(cache, deviceID = deviceID, context = this)
        }
    }
}