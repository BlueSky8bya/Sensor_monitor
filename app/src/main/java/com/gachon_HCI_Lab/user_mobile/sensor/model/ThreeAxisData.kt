package com.gachon_HCI_Lab.user_mobile.sensor.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ThreeAxis_TB")
data class ThreeAxisData (
    @ColumnInfo override val time: Long,
    @ColumnInfo override val type: String,
    @ColumnInfo(name = "x_value") val xValue: Double,
    @ColumnInfo(name = "y_value") val yValue: Double,
    @ColumnInfo(name = "z_value") val zValue: Double
): AbstractSensor() {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "three_id")
    var id: Long = 0

    companion object {
        fun of(time: Long, type: String, xValue: Double, yValue: Double, zValue: Double): ThreeAxisData {
            return ThreeAxisData(time, type, xValue, yValue, zValue)
        }
    }
}
