package com.gachon_HCI_Lab.user_mobile.sensor.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData

/**
 * HeartRate DAO 클래스
 * */
@Dao
interface OneAxisDataDao {

    /**
     * 데이터 조회 메소드
     * */
    @Query("SELECT * FROM OneAxis_TB WHERE one_id > :cursor ORDER BY one_id ASC")
    fun getAll(cursor: Int): List<OneAxisData>

    /**
     * 데이터 insert 메소드
     * */
    @Insert
    fun insertAll(vararg sensor: OneAxisData)

    @Query("DELETE FROM OneAxis_TB WHERE one_id = :id")
    fun delete(id: Long)

    /**
     * 현재 시간(now)에서 기준(period) 만큼의 수집된 데이터를 조회하는 메소드
     * */
    @Query("SELECT * FROM OneAxis_TB WHERE time BETWEEN :now - :period AND :now")
    fun getFromNow(now: Long, period: Long): List<OneAxisData>
    @Query("DELETE FROM OneAxis_TB")
    fun deleteAll()

}