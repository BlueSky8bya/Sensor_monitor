package com.gachon_HCI_Lab.user_mobile.activity

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class SensorChartActivity : AppCompatActivity() {
    private var timer: Timer? = null

    private lateinit var binding: ActivityChartBinding
    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finish()
        }
    }
    private lateinit var ppgGreenChart: LineChart
    private lateinit var heartRateChart: LineChart
    private lateinit var lightChart : LineChart
    private lateinit var accelerometerChart : LineChart
    private lateinit var gyroscopeChart : LineChart
    private lateinit var gravityChart : LineChart

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
    }

    class LineChartXAxisValueFormatter : IndexAxisValueFormatter() {
        override fun getFormattedValue(value: Float): String {
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

        val charts = listOf(ppgGreenChart, heartRateChart, lightChart, accelerometerChart, gyroscopeChart, gravityChart)
        charts.forEach { chart ->
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
        }

        makeBufferChart()
        this.onBackPressedDispatcher.addCallback(this, callback)
    }

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

        for (i in 0 until (len / bin).toInt()) {
            sensorTemp.add(Pair((i * bin).toInt(), 0.0))
            count.add(0)
        }

        val sensorName = dataList.key
        val sensorData = dataList.value

        // [경고 해결] 사용하지 않는 index(_) 처리
        for (data in sensorData) {
            val time = data.time / 1000L
            val axisData = data as OneAxisData
            val dif = kotlin.math.abs(startTime - time)
            val idx = (dif / bin).toInt()

            if (idx >= 0 && idx < sensorTemp.size) {
                sensorTemp[idx] = Pair(sensorTemp[idx].first, sensorTemp[idx].second + axisData.value)
                count[idx] = count[idx] + 1
            }
        }

        for (i in 0 until sensorTemp.size) {
            val yVal = if (count[i] == 0) 0f else (sensorTemp[i].second / count[i]).toFloat()
            bufferQueue.add(Entry(sensorTemp[i].first.toFloat(), yVal))
        }

        when (sensorName) {
            SensorEnum.LIGHT.value -> updateChart(lightChart, bufferQueue, "Light")
            SensorEnum.HEART_RATE.value -> updateChart(heartRateChart, bufferQueue, "HeartRate")
            SensorEnum.PPG_GREEN.value -> updateChart(ppgGreenChart, bufferQueue, "PPG Green")
        }
    }

    private fun summaryThreeAxisData(dataList : MutableMap.MutableEntry<String, List<AbstractSensor>>, bin: Int, len: Long) {
        val startTime = System.currentTimeMillis() / 1000L
        val bufferQueues = listOf(ArrayDeque<Entry>(), ArrayDeque<Entry>(), ArrayDeque<Entry>())
        val sensorTemps = listOf(ArrayList<Pair<Int, Double>>(), ArrayList<Pair<Int, Double>>(), ArrayList<Pair<Int, Double>>())
        val count = ArrayList<Int>()

        for (i in 0 until (len / bin).toInt()) {
            val timePoint = (i * bin).toInt()
            sensorTemps.forEach { it.add(Pair(timePoint, 0.0)) }
            count.add(0)
        }

        val sensorName = dataList.key
        val sensorData = dataList.value

        for (data in sensorData) {
            val time = data.time / 1000L
            val axisData = data as ThreeAxisData
            val dif = kotlin.math.abs(startTime - time)
            val idx = (dif / bin).toInt()

            if (idx >= 0 && idx < count.size) {
                sensorTemps[0][idx] = Pair(sensorTemps[0][idx].first, sensorTemps[0][idx].second + axisData.xValue)
                sensorTemps[1][idx] = Pair(sensorTemps[1][idx].first, sensorTemps[1][idx].second + axisData.yValue)
                sensorTemps[2][idx] = Pair(sensorTemps[2][idx].first, sensorTemps[2][idx].second + axisData.zValue)
                count[idx] = count[idx] + 1
            }
        }

        for (i in 0 until count.size) {
            for (j in 0..2) {
                val yVal = if (count[i] == 0) 0f else (sensorTemps[j][i].second / count[i]).toFloat()
                bufferQueues[j].add(Entry(sensorTemps[j][i].first.toFloat(), yVal))
            }
        }

        val dataset = ArrayList<ILineDataSet>().apply {
            add(makeLineDataSet(bufferQueues[0], "Axis X").apply { color = Color.RED })
            add(makeLineDataSet(bufferQueues[1], "Axis Y").apply { color = Color.GREEN })
            add(makeLineDataSet(bufferQueues[2], "Axis Z").apply { color = Color.BLUE })
        }

        when (sensorName) {
            SensorEnum.ACCELEROMETER.value -> setChartData(dataset, accelerometerChart)
            SensorEnum.GYROSCOPE.value -> setChartData(dataset, gyroscopeChart)
            SensorEnum.GRAVITY.value -> setChartData(dataset, gravityChart)
        }
    }

    private fun updateChart(chart: LineChart, dataList: ArrayDeque<Entry>, label: String) {
        chart.clear()
        val dataset = ArrayList<ILineDataSet>().apply { add(makeLineDataSet(dataList, label)) }
        setChartData(dataset, chart)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun makeBufferChart() {
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    GlobalScope.launch {
                        getSensorData("OneAxis", 60, 10 * 60)
                        getSensorData("ThreeAxis", 60, 10 * 60)
                    }
                }
            }
        }, 0, 10000)
    }

    private fun makeLineDataSet(dataList : ArrayDeque<Entry>, name :String): LineDataSet {
        return LineDataSet(dataList.toList(), name)
    }

    private fun setChartData(dataset : ArrayList<ILineDataSet>, chart : LineChart) {
        chart.data = LineData(dataset)
        chart.invalidate()
    }
}