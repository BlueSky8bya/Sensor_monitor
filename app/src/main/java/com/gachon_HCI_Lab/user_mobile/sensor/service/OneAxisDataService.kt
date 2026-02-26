package com.gachon_HCI_Lab.user_mobile.sensor.service

import android.content.Context
import com.gachon_HCI_Lab.user_mobile.sensor.AppDatabase
import com.gachon_HCI_Lab.user_mobile.sensor.dao.OneAxisDataDao
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData

class OneAxisDataService(context: Context) {

    private val oneAxisDataDao: OneAxisDataDao
    private val db: AppDatabase

    init {
        db = AppDatabase.getInstance(context)!!
        oneAxisDataDao = db.oneAxisDataDao()
    }

    // 싱글톤 구현
    companion object {

        @Volatile
        private var INSTANCE: OneAxisDataService? = null
        fun getInstance(_context: Context): OneAxisDataService {
            return INSTANCE ?: synchronized(OneAxisDataService::class) {
                val instance = OneAxisDataService(_context)
                INSTANCE = instance
                instance
            }
        }
    }

    fun insert(data: OneAxisData) {
        oneAxisDataDao.insertAll(data)
    }

    fun getAll(cursor: Int): List<AbstractSensor> {
        return oneAxisDataDao.getAll(cursor)
    }

    fun getFromNow(period: Long): List<AbstractSensor> {
        val now = System.currentTimeMillis()
        return oneAxisDataDao.getFromNow(now, period)
    }

    fun delete(id: Long) {
        oneAxisDataDao.delete(id)
    }

    fun deleteAll() {
        oneAxisDataDao.deleteAll()
    }
}