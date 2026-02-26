package com.gachon_HCI_Lab.user_mobile.sensor.model

data class ThreeAxisSensorDto(
    var type: String,
    var time: Long,
    var xData: Double,
    var yData: Double,
    var zData: Double
)
