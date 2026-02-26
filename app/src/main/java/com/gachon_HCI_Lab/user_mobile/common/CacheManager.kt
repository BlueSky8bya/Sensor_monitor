package com.gachon_HCI_Lab.user_mobile.common

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * [CacheManager]
 * 캐시 파일을 저장하고 로드하는 클래스
 * 로그인 캐시 정보에 저장 및 로드에 사용한다.
 */
class CacheManager {
    companion object {
        val CACHE_DIRECTORY = "cache/"

        /**
         * 캐시 파일을 저장하는 함수
         * data : 저장할 데이터(유저 아이디)
         * fileName : 저장할 파일 이름
         */
        fun saveCacheFile(context: Context, data: String, fileName: String) {
            val cacheDir = File(context.cacheDir, CACHE_DIRECTORY)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val cacheFile = File(cacheDir, fileName)
            if (!cacheFile.exists()) {
                cacheFile.createNewFile()
            }

            val fos = FileOutputStream(cacheFile)
            fos.write(data.toByteArray())
            fos.close()
        }

        /**
         * 캐시 파일을 로드하는 함수
         * fileName : 로드할 파일 이름
         * return data : 로드한 데이터(유저 아이디)
         */
        fun loadCacheFile(context: Context, fileName: String): String? {
            val cacheDir = File(context.cacheDir, CACHE_DIRECTORY)
            if (!cacheDir.exists()) {
                return null
            }

            val cacheFile = File(cacheDir, fileName)
            if (!cacheFile.exists()) {
                return null
            }

            val fis = FileInputStream(cacheFile)
            val data = fis.readBytes().toString(Charsets.UTF_8)
            fis.close()

            return data
        }

        /**
         * 캐시 파일을 삭제하는 함수
         * fileName : 삭제할 파일 이름
         */
        fun deleteCacheFile(context: Context, fileName: String) {
            val cacheDir = File(context.cacheDir, CACHE_DIRECTORY)

            val cacheFile = File(cacheDir, fileName)
            if (!cacheFile.exists()) {
                Log.d("CacheManager", "deleteCacheFile: file not exists")
                return
            }

            cacheFile.delete()
            Log.d("CacheManager", "deleteCacheFile: delete complete")
        }


    }
}