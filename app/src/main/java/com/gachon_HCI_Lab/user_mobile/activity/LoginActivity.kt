package com.gachon_HCI_Lab.user_mobile.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gachon_HCI_Lab.user_mobile.common.BTManager
import com.gachon_HCI_Lab.user_mobile.common.ServerConnection
import com.gachon_HCI_Lab.user_mobile.common.CacheManager
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.sensor.AppDatabase
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //==== 초기화가 필요한 인스턴스
        db = AppDatabase.getInstance(applicationContext)!!
        //====

        // 배터리 최적화 해제 요청
        requestIgnoreBatteryOptimization()

        // 오래된 센서 데이터 삭제
        CsvController.getExternalPath(this, "Sensor").let {
            CsvController.deleteOldfiles(it, 60 * 60 * 24 * 1000)
        }
        CsvController.getExternalPath(this, "Sensor/Sended").let {
            CsvController.deleteOldfiles(it, 60 * 60 * 24 * 1000)
        }

        val deviceID = BTManager.getUUID(this, BTManager.getConnectedDevice(this, BTManager.connectedDevices(this)))
        Log.d("LoginActivity", "deviceID: $deviceID")

        val cache = CacheManager.loadCacheFile(this, "login.txt")
        if (cache != null) {
            ServerConnection.login(cache, deviceID = deviceID, context = this)
        } else {
            binding.loginBtn.setOnClickListener {
                ServerConnection.login(binding.id.text.toString(), deviceID = deviceID, context = this)
            }
        }
    }

    // 배터리 최적화 해제 요청 함수
    private fun requestIgnoreBatteryOptimization() {
        val packageName = applicationContext.packageName
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent().apply {
                    action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "배터리 최적화 해제 요청 실패", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
}
