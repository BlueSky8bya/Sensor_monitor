package com.gachon_HCI_Lab.user_mobile.activity

import android.os.Bundle
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gachon_HCI_Lab.user_mobile.adapter.TextAdapter
import com.gachon_HCI_Lab.user_mobile.common.CsvController
import com.gachon_HCI_Lab.user_mobile.R
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityCsvPopupBinding
import java.io.File

class CsvPopupActivity: AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TextAdapter
    private lateinit var itemList: MutableList<String>

    private lateinit var binding: ActivityCsvPopupBinding
    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // 뒤로가기 클릭 시 종료
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCsvPopupBinding.inflate(layoutInflater)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener {
            finish()
        }

        // 리사이클러 뷰 관련 코드
        recyclerView = findViewById(R.id.csv_recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        itemList = mutableListOf()

        adapter = TextAdapter(itemList)
        recyclerView.adapter = adapter

        // CSV 파일 개수 가져오기
        val csvFolderPath = CsvController.getExternalPath(applicationContext)// CSV 파일들이 있는 폴더 경로 설정
        val csvFolder = File(csvFolderPath)
        if (csvFolder.exists() && csvFolder.isDirectory) {
            val csvFiles = csvFolder.listFiles { file ->
                file.extension.equals("csv", ignoreCase = true) // 확장자가 .csv인 파일들만 가져옴
            }
            csvFiles?.let {
                itemList.clear()
                itemList.addAll(csvFiles.map { file ->
                    val fileSize = file.length() // 파일 크기
                    val fileName = "${file.name}   (${fileSize} bytes)" // 파일명과 크기 조합하여 표시
                    fileName
                })
            }
        }
        this.onBackPressedDispatcher.addCallback(this, callback)
    }

}