package com.gachon_HCI_Lab.user_mobile.common

import android.content.Context
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisSensorDto
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisSensorDto

/**
 * 정규표현식 클래스
 * 소켓을 통해 수신한 데이터 수집을 위해 사용 
 * */
class RegexManager private constructor(context: Context) {

    // 싱글톤 구현
    companion object {
        @Volatile
        private var INSTANCE: RegexManager? = null
        fun getInstance(_context: Context): RegexManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RegexManager(_context).also { INSTANCE = it }
            }
        }
    }

    // 타입 추출 정규표현식
    val typeRegex = "^(\\d+)".toRegex()

    // 1축 데이터 정규표현식
    val oneAxisRegex = "\\d+\\|\\d{12,}\\|(-?\\d+(\\.\\d+)?):".toRegex()

    // 3축 데이터 정규표현식
    val threeAxisRegex = "\\d+\\|\\d{12,}\\|(-?\\d+(\\.\\d+)?)\\|(-?\\d+(\\.\\d+)?)\\|(-?\\d+(\\.\\d+)?):".toRegex()

    // 1축 데이터 value 정규표현식
    val oneAxisValueRegex = "\\d{12,}\\|(-?\\d+(\\.\\d+)?)".toRegex()

    // 3축 데이터 value 정규표현식
    val threeAxisValueRegex = "\\d{12,}\\|(-?\\d+(\\.\\d+)?)\\|(-?\\d+(\\.\\d+)?)\\|(-?\\d+(\\.\\d+)?)".toRegex()

    /**
     * 데이터 추출 시 사용 메소드
     * */
    fun oneAxisDataExtract(type: String, res: String): OneAxisSensorDto {
        val str = res.split("|")
        val time = str[0].toLong()
        val data = str[1].toDouble()

        return OneAxisSensorDto(type, time, data)
    }

    fun threeAxisDataExtract(type: String, res: String): ThreeAxisSensorDto {
        val str = res.split("|")
        val time = str[0].toLong()
        val xData = str[1].toDouble()
        val yData = str[2].toDouble()
        val zData = str[3].toDouble()

        return ThreeAxisSensorDto(type, time, xData, yData, zData)
    }

}