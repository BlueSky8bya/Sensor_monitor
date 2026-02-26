package com.gachon_HCI_Lab.user_mobile.sensor.model

// 센서 추상 클래스, 각 센서들은 이 클래스를 상속받아야 한다
abstract class AbstractSensor {
    abstract val time: Long
    abstract val type: String
}