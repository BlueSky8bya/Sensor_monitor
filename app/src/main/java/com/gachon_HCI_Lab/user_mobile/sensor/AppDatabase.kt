package com.gachon_HCI_Lab.user_mobile.sensor

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gachon_HCI_Lab.user_mobile.sensor.dao.OneAxisDataDao
import com.gachon_HCI_Lab.user_mobile.sensor.dao.ThreeAxisDataDao
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData

/**
 * 앱 내 센서 데이터 저장용 로컬 DB 접근용 추상 클래스
 * */
@Database(entities = [OneAxisData::class, ThreeAxisData::class], version = 3)
abstract class AppDatabase : RoomDatabase() {

    abstract fun oneAxisDataDao(): OneAxisDataDao
    abstract fun threeAxisDataDao(): ThreeAxisDataDao

    // 싱글톤 구현
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase? {
            if (INSTANCE == null) {
                synchronized(AppDatabase::class) {
                    INSTANCE = Room.databaseBuilder(
                        context,
                        AppDatabase::class.java,
                        "sensor"
                    ).fallbackToDestructiveMigration()
                        .build()
                }
            }
            INSTANCE!!.openHelper.readableDatabase
            return INSTANCE
        }
    }
}