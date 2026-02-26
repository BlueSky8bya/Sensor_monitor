package com.gachon_HCI_Lab.user_mobile.sensor.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData

/**
 * PpgGreen DAO 클래스
 * */
@Dao
interface ThreeAxisDataDao {

    /**
     * 데이터 조회 메소드
     * */
    @Query("SELECT * FROM ThreeAxis_TB WHERE three_id > :cursor ORDER BY three_id ASC")
    fun getAll(cursor: Int): List<ThreeAxisData>

    /**
     * 데이터 insert 메소드
     * */
    @Insert
    suspend fun insertAll(vararg users: ThreeAxisData)

    @Query("DELETE FROM ThreeAxis_TB WHERE three_id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 현재 시간(now)에서 기준(period) 만큼의 수집된 데이터를 조회하는 메소드
     * */
    @Query("SELECT * FROM ThreeAxis_TB WHERE time BETWEEN :now - :period AND :now")
    fun getFromNow(now: Long, period: Long): List<ThreeAxisData>
    @Query("DELETE FROM ThreeAxis_TB")
    fun deleteAll()
}