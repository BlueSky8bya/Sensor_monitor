package com.gachon_HCI_Lab.user_mobile.sensor.service

import android.content.Context
import com.gachon_HCI_Lab.user_mobile.sensor.AppDatabase
import com.gachon_HCI_Lab.user_mobile.sensor.dao.ThreeAxisDataDao
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData

class ThreeAxisDataService(context: Context) {

    private val threeAxisDataDao: ThreeAxisDataDao
    private val db: AppDatabase

    init {
        db = AppDatabase.getInstance(context)!!
        threeAxisDataDao = db.threeAxisDataDao()
    }

    // 싱글톤 구현
    companion object {

        @Volatile
        private var INSTANCE: ThreeAxisDataService? = null
        fun getInstance(_context: Context): ThreeAxisDataService {
            return INSTANCE ?: synchronized(ThreeAxisDataService::class) {
                val instance = ThreeAxisDataService(_context)
                INSTANCE = instance
                instance
            }
        }
    }

    suspend fun insert(data: ThreeAxisData) {
        threeAxisDataDao.insertAll(data)
    }

    fun getAll(cursor: Int): List<AbstractSensor> {
        return threeAxisDataDao.getAll(cursor)
    }

    fun getFromNow(period: Long): List<AbstractSensor> {
        val now = System.currentTimeMillis()
        return threeAxisDataDao.getFromNow(now, period)
    }

    fun deleteAll() {
        threeAxisDataDao.deleteAll()
    }

//    fun delete(id: Long) {
//        ppgGreenDao.delete(id)
//    }
}