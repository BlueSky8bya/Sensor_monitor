package com.gachon_HCI_Lab.user_mobile.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gachon_HCI_Lab.user_mobile.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MainActivity의 UI를 설정하는 부분 (기본적으로는 화면이 비어있을 수 있음)
        setContentView(R.layout.activity_main)

        // 로그인 화면으로 이동
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)

        // MainActivity는 더 이상 필요 없으므로 종료
        finish()
    }
}
