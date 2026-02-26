package com.gachon_HCI_Lab.user_mobile.common

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

object CsvStatistics {

    fun makeMean(context: Context, file : File, name : String = "") {
        val fileReader = CSVReader(InputStreamReader(file.inputStream()))


        val fileList = fileReader.readAll()
        fileList.removeAt(0)

        var valueSum = 0.0
        for (ppg in fileList) {
            valueSum += ppg[1].toFloat()
        }

        val valueMean = valueSum / fileList.size
        var toName : String
        if(name != "")
            toName = name + "_"
        toName = file.name.split("_")[0] + "_mean.csv"
        val toFile = File(CsvController.getExternalPath(context,"sensor/statistics"), toName)
        val csvWriter = CSVWriter(FileWriter(toFile,true))


        try {
            val time = file.name.split("_")[1].split(".")[0]
            csvWriter.writeNext( arrayOf(time, valueMean.toString()))
        } catch (e: Exception) {
            Log.d(ContentValues.TAG, e.toString())
        } finally {
            csvWriter.close()
        }

    }
}