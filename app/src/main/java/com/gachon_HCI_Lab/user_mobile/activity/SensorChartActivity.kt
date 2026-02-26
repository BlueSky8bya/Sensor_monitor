package com.gachon_HCI_Lab.user_mobile.activity

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityChartBinding
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.SensorEnum
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

//TODO
//선택이나 줌 안되게 막아야함
class SensorChartActivity : AppCompatActivity() {
    private var timer: Timer? = null

    private lateinit var binding: ActivityChartBinding
    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // 뒤로가기 클릭 시 종료
            finish()
        }
    }
    private lateinit var ppgGreenChart: LineChart
    private lateinit var heartRateChart: LineChart
    private lateinit var lightChart : LineChart
    private lateinit var accelerometerChart : LineChart
    private lateinit var gyroscopeChart : LineChart
    private lateinit var gravityChart : LineChart


    protected override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
    }

    class LineChartXAxisValueFormatter : IndexAxisValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            // Show time in local version
            val unixTime = System.currentTimeMillis() / 1000L - value.toLong()
            val timestamp = Date(unixTime * 1000L)
            val dateTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return dateTimeFormat.format(timestamp)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ppgGreenChart = binding.chartPpgGreen
        heartRateChart = binding.chartHeart
        lightChart = binding.chartLight
        accelerometerChart = binding.chartAccelerometer
        gyroscopeChart = binding.chartGyroscope
        gravityChart = binding.chartGravity

        //차트 x라벨 하단에 위치
        ppgGreenChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        heartRateChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lightChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        accelerometerChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        gyroscopeChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        gravityChart.xAxis.position = XAxis.XAxisPosition.BOTTOM

        ppgGreenChart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
        heartRateChart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
        lightChart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
        accelerometerChart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
        gyroscopeChart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
        gravityChart.xAxis.valueFormatter = LineChartXAxisValueFormatter()

        makeBufferChart()

        this.onBackPressedDispatcher.addCallback(this, callback)
    }


    /**
    sensorName : 요약 센서명
    bin : 데이터 사이의 간격(초)
    len : 총 데이터의 시간 길이(초)
     */
//    private suspend fun getSensorDatas(axisName: String, bin: Int, len: Long): ArrayDeque<Entry> {
//        startTime = System.currentTimeMillis() / 1000L
//
//        val bufferQueue = ArrayDeque<Entry>()
//        val sensorTemp = ArrayList<Pair<Int, Float>>()
//        val count = ArrayList<Int>()
//
//        for (i in 0 until (len / (bin)).toInt()) {
////            sensorTemp.add(Pair(startTime - (i * bin), 0f))
////            sensorTemp.add(Pair(i.toLong(), 0f))
//            sensorTemp.add(Pair((i * bin), 0f))
//            count.add(0)
//        }
//
//        SensorController.getInstance(this@SensorChartActivity).getDataFromNow(axisName, len * 1000)
//            .let {
//
//                for ((iter, data) in it.withIndex()) {
//                    val time = data.time / 1000L
////                    val value = data.value
//
//                    val dif = kotlin.math.abs(startTime - time)
//                    val idx = (dif / bin).toInt()
//
//                    if (idx < 0 ||idx >= sensorTemp.size) {
//                        continue
//                    }
//
////                    sensorTemp[idx] = Pair(sensorTemp[idx].first, sensorTemp[idx].second + value)
//    //                  sensorTemp[idx] = Pair(idx.toLong(), sensorTemp[idx]!!.second + value)
//                    count[idx] = count[idx] + 1
//
//                }
//            }
//
//        for (i in 0 until sensorTemp.size) {
//            if (count[i] == 0) {
//                bufferQueue.add(
//                    Entry(
//                        sensorTemp[i].first.toFloat(),
//                        0f
//                    )
//                )
//            }
//            else{
//                bufferQueue.add(
//                    Entry(
//                        sensorTemp[i].first.toFloat(),
//                        sensorTemp[i].second / count[i]
//                    )
//                )
//            }
//
//        }
//        return bufferQueue
//
//    }

    private suspend fun getSensorData(axisName: String, bin: Int, len: Long): ArrayDeque<Entry> {
        SensorController.getInstance(this@SensorChartActivity).getDataFromNow(axisName, len * 1000)
            .let {
                val splittedData = SensorController.getInstance(this).splitData(it)

                for (dataList in splittedData){
                    if(axisName == "OneAxis")
                        summaryOneAxisData(dataList, bin, len)
                    else if(axisName == "ThreeAxis")
                        summaryThreeAxisData(dataList, bin, len)
                }

                }

        return ArrayDeque()
    }

    private fun summaryOneAxisData(dataList : MutableMap.MutableEntry<String, List<AbstractSensor>>, bin: Int, len: Long) {
        val startTime = System.currentTimeMillis() / 1000L

        val bufferQueue = ArrayDeque<Entry>()
        val sensorTemp = ArrayList<Pair<Int, Double>>()
        val count = ArrayList<Int>()

        for (i in 0 until (len / (bin)).toInt()) {
//            sensorTemp.add(Pair(startTime - (i * bin), 0f))
//            sensorTemp.add(Pair(i.toLong(), 0f))
            sensorTemp.add(Pair((i * bin), 0.0))
            count.add(0)
        }

        val sensorName = dataList.key
        val sensorData = dataList.value

        for ((iter, data) in sensorData.withIndex()) {
            val time = data.time / 1000L
            val axisData = data as OneAxisData

            val dif = kotlin.math.abs(startTime - time)
            val idx = (dif / bin).toInt()

            if (idx < 0 ||idx >= sensorTemp.size) {
                continue
            }

            sensorTemp[idx] = Pair(sensorTemp[idx].first, sensorTemp[idx].second + axisData.value)
            count[idx] = count[idx] + 1
        }

        for (i in 0 until sensorTemp.size) {
            if (count[i] == 0) {
                bufferQueue.add(
                    Entry(
                        sensorTemp[i].first.toFloat(),
                        0f
                    )
                )
            }
            else{
                bufferQueue.add(
                    Entry(
                        sensorTemp[i].first.toFloat(),
                        (sensorTemp[i].second / count[i]).toFloat()
                    )
                )
            }
        }

        when (sensorName) {
            SensorEnum.LIGHT.value -> {
                lightChart.clear()
                val dataset = ArrayList<ILineDataSet>()
                dataset.add(makeLineDataSet(bufferQueue, "Light"))
                setChartData(dataset, lightChart)
            }
            SensorEnum.HEART_RATE.value -> {
                heartRateChart.clear()
                val dataset = ArrayList<ILineDataSet>()
                dataset.add(makeLineDataSet(bufferQueue, "hearRate"))
                setChartData(dataset, heartRateChart)
            }
            SensorEnum.PPG_GREEN.value -> {
                ppgGreenChart.clear()
                val dataset = ArrayList<ILineDataSet>()
                dataset.add(makeLineDataSet(bufferQueue, "ppg green"))
                setChartData(dataset, ppgGreenChart)
            }
        }
    }

    private fun summaryThreeAxisData(dataList : MutableMap.MutableEntry<String, List<AbstractSensor>>, bin: Int, len: Long) {
        val startTime = System.currentTimeMillis() / 1000L

        val bufferQueueX = ArrayDeque<Entry>()
        val bufferQueueY = ArrayDeque<Entry>()
        val bufferQueueZ = ArrayDeque<Entry>()

        val sensorTempX = ArrayList<Pair<Int, Double>>()
        val sensorTempY = ArrayList<Pair<Int, Double>>()
        val sensorTempZ = ArrayList<Pair<Int, Double>>()
        val count = ArrayList<Int>()

        for (i in 0 until (len / (bin)).toInt()) {
//            sensorTemp.add(Pair(startTime - (i * bin), 0f))
//            sensorTemp.add(Pair(i.toLong(), 0f))
            sensorTempX.add(Pair((i * bin), 0.0))
            sensorTempY.add(Pair((i * bin), 0.0))
            sensorTempZ.add(Pair((i * bin), 0.0))
            count.add(0)
        }


        val sensorName = dataList.key
        val sensorData = dataList.value

        for ((iter, data) in sensorData.withIndex()) {
            val time = data.time / 1000L
            val axisData = data as ThreeAxisData

            val dif = kotlin.math.abs(startTime - time)
            val idx = (dif / bin).toInt()

            if (idx < 0 ||idx >= sensorTempX.size) {
                continue
            }

            sensorTempX[idx] = Pair(sensorTempX[idx].first, sensorTempX[idx].second + axisData.xValue)
            sensorTempY[idx] = Pair(sensorTempY[idx].first, sensorTempY[idx].second + axisData.yValue)
            sensorTempZ[idx] = Pair(sensorTempZ[idx].first, sensorTempZ[idx].second + axisData.zValue)


            count[idx] = count[idx] + 1
        }

        for (i in 0 until sensorTempX.size) {
            if (count[i] == 0) {
                bufferQueueX.add(
                    Entry(
                        sensorTempX[i].first.toFloat(),
                        0f
                    )
                )
                bufferQueueY.add(
                    Entry(
                        sensorTempY[i].first.toFloat(),
                        0f
                    )
                )
                bufferQueueX.add(
                    Entry(
                        sensorTempZ[i].first.toFloat(),
                        0f
                    )
                )
            }
            else{
                bufferQueueX.add(
                    Entry(
                        sensorTempX[i].first.toFloat(),
                        (sensorTempX[i].second / count[i]).toFloat()
                    )
                )
                bufferQueueY.add(
                    Entry(
                        sensorTempY[i].first.toFloat(),
                        (sensorTempY[i].second / count[i]).toFloat()
                    )
                )
                bufferQueueZ.add(
                    Entry(
                        sensorTempZ[i].first.toFloat(),
                        (sensorTempZ[i].second / count[i]).toFloat()
                    )
                )
            }
        }

        val dataset = ArrayList<ILineDataSet>()
        val lineX = makeLineDataSet(bufferQueueX, "Axis X")
        val lineY = makeLineDataSet(bufferQueueY, "Axis Y")
        val lineZ = makeLineDataSet(bufferQueueZ, "Axis Z")

        lineX.color = Color.RED
        lineY.color = Color.GREEN
        lineZ.color = Color.BLUE

        dataset.add(lineX)
        dataset.add(lineY)
        dataset.add(lineZ)

        when (sensorName) {
            SensorEnum.ACCELEROMETER.value -> {
                accelerometerChart.clear()
                setChartData(dataset, accelerometerChart)

            }
            SensorEnum.GYROSCOPE.value -> {
                gyroscopeChart.clear()
                setChartData(dataset, gyroscopeChart)
            }
            SensorEnum.GRAVITY.value -> {
                gravityChart.clear()
                setChartData(dataset, gravityChart)
            }
        }
    }

    private fun makeBufferChart() {
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                Log.d("Chart Activity", "Timer called!")
                runOnUiThread {
                    GlobalScope.launch {
                        ppgGreenChart.clear()
                        heartRateChart.clear()

                        getSensorData("OneAxis", 60, 10 * 60)
                        getSensorData("ThreeAxis", 60, 10 * 60)
                    }
                }
            }
        }, 0, 10000)
    }

    private fun makeLineDataSet(dataList : ArrayDeque<Entry>, name :String): LineDataSet {
        val linedataset = LineDataSet(dataList, name)

        return linedataset
    }

    private fun setChartData(dataset : ArrayList<ILineDataSet>, chart : LineChart) {
        val data = LineData(dataset)
        chart.data = data
        chart.invalidate()
    }


//    private fun makeStatisticsChart() {
//        ppgGreenChart.clear()
//        heartRateChart.clear()
//
//        val ppgFile = CsvController.getFile(
//            CsvController.getExternalPath(
//                this,
//                "sensor"
//            ) + "/statistics/PpgGreen_mean.csv"
//        )
//        val heartFile = CsvController.getFile(
//            CsvController.getExternalPath(
//                this,
//                "sensor"
//            ) + "/statistics/HeartRate_mean.csv"
//        )
//
//        if (ppgFile == null || heartFile == null) {
//            Log.d(TAG, "No statistics file.")
//            return
//        }
//
//        val ppgReader = CSVReader(InputStreamReader(ppgFile?.inputStream()))
//        val heartReader = CSVReader(InputStreamReader(heartFile?.inputStream()))
//
//        val ppgList = ppgReader.readAll()
//        val heartList = heartReader.readAll()
//
//        val ppgGreenQueue = ArrayDeque<Entry>()
//        val heartQueue = ArrayDeque<Entry>()
//
//        var i = 0
//        for (ppg in ppgList) {
//            i++
//            val time = ppg[0].toFloat()
//            val data = ppg[1].toFloat()
//            ppgGreenQueue.add(Entry(i.toFloat(), data))
////            ppgGreenQueue.add(Entry(time, data))
//
//        }
//
//        i = 0
//        for (heart in heartList) {
//            i++
//            val time = heart[0].toFloat()
//            val data = heart[1].toFloat()
//            heartQueue.add(Entry(i.toFloat(), data))
////            ppgGreenQueue.add(Entry(time, data))
//        }
//
//        val ppgGreenDataSet = LineDataSet(ppgGreenQueue.toList(), "ppgGreen")
//        val heartDataSet = LineDataSet(heartQueue.toList(), "heartRate")
//
//        val ppgGreenData = LineData(ppgGreenDataSet)
//        val heartData = LineData(heartDataSet)
//
//        ppgGreenChart.data = ppgGreenData
//        heartRateChart.data = heartData
//
//        ppgGreenChart.invalidate()
//        heartRateChart.invalidate()
//    }
}

