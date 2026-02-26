package com.gachon_HCI_Lab.user_mobile

import android.content.Context
import org.junit.Test

import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@RunWith(MockitoJUnitRunner::class)
class ExampleUnitTest {

    @Mock
    lateinit var context: Context
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun regex_test() {
        val trueString: String = "0|1234509093636:0.01-0|1234509093636:1.0-0|1234509093636:2.0-"
        val pgString: String = "0|1234509093636:0.0-1|1234509093636:-14232311.02-0|1234509093636:1.0-1|1234509093636:0.0-"

        val hrRegex = "0\\|\\d{12,}:(-?\\d+(\\.\\d+)?)-".toRegex()
        val pgRegex = "1\\|\\d{12,}:(-?\\d+(\\.\\d+)?)-".toRegex()
        val valueRegex = "\\d{12,}:(-?\\d+(\\.\\d+)?)".toRegex()

        val hrList = hrRegex.findAll(trueString)
        val pgList = pgRegex.findAll(pgString)

        for (hrPattern in hrList) {
            val hrVal = hrPattern.value
            val resRex = valueRegex.find(hrVal)
            val res = resRex?.value
            if (res != null) {
                val str = res.split(":")
                val time = str[0]
                val data = str[1]

                println("$time, $data")
            }
        }

        for (pgPattern in pgList) {
            val pgVal = pgPattern.value
            val resRex = valueRegex.find(pgVal)
            val res = resRex?.value

            if (res != null) {
                val str = res.split(":")
                val time = str[0]
                val data = str[1]
                println("$time, $data")
            }
        }

//            val time = hrRes[0].trim()
//            val data = hrRes[1].trim().toFloat()
//            println("time: $time, data: $data")
//            assertEquals(arrayOf(time, data), arrayOf(1234509093636, 0.0))


//            for (str in value) {
//                print(str)
//            }
//
//            println(hrRes?.value)
//

//        val hrRes = valueRegex.find(hrList.toString()).toString().split(":")
//        val time = hrRes[0].trim()
//        val data = hrRes[1].trim().toFloat()
//
//        assertEquals(arrayOf(time, data), arrayOf(1234509093636, 0.0))
    }
}