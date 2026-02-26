package com.gachon_HCI_Lab.user_mobile.sensor.model

enum class SensorEnum(val value: String, val type: Int) {
    // 1축 데이터
    LIGHT("Light", 5),
    STEP_COUNT("StepCount", 18),
    HEART_RATE("HeartRate", 21),
    PPG_GREEN("PpgGreen", 30),

    // 3축 데이터
    ACCELEROMETER("Accelerometer", 1),
    GYROSCOPE("Gyroscope", 4),
    GRAVITY("Gravity", 9);

    companion object {
        fun getValueByType(type: Int): String {
            val sensor = values().find { it.type == type }
            return sensor!!.value
        }

        fun isOneAxisData(type: Int): Boolean {
            return type in listOf(1, 4, 9)
        }

        fun isThreeAxisData(type: Int): Boolean {
            return type in listOf(5, 18, 21, 30)
        }
    }

}