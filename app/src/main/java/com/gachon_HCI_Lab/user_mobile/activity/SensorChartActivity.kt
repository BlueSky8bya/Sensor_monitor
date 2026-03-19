package com.gachon_HCI_Lab.user_mobile.activity

import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.gachon_HCI_Lab.user_mobile.sensor.controller.SensorController
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityChartBinding
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.AxisBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        override fun handleOnBackPressed() { finish() }
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

    // [핵심 수정] 스마트 라벨링 X축 포매터 (시가 바뀔 때만 전체 표시)
    class SmartTimeAxisFormatter : ValueFormatter() {
        private val fullFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val shortFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        private val hourFormat = SimpleDateFormat("HH", Locale.getDefault())

        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            // 현재 그릴 라벨의 Unix 시간 계산
            val unixTime = System.currentTimeMillis() / 1000L - value.toLong()
            val currentTimestamp = Date(unixTime * 1000L)

            // 축의 엔트리 정보가 없으면 기본 풀 포맷 반환
            if (axis == null || axis.mEntries.isEmpty()) {
                return fullFormat.format(currentTimestamp)
            }

            // 현재 라벨이 축의 몇 번째 인덱스인지 확인
            val entryIndex = axis.mEntries.indexOfFirst { it == value }

            // [조건 1] 가장 첫 번째(왼쪽) 라벨은 무조건 풀 포맷(HH:mm:ss)
            if (entryIndex <= 0) {
                return fullFormat.format(currentTimestamp)
            }

            // [조건 2] 이전 라벨의 시간과 비교하여 "시(Hour)"가 달라졌는지 확인
            val prevValue = axis.mEntries[entryIndex - 1]
            val prevUnixTime = System.currentTimeMillis() / 1000L - prevValue.toLong()
            val prevTimestamp = Date(prevUnixTime * 1000L)

            val currentHour = hourFormat.format(currentTimestamp)
            val prevHour = hourFormat.format(prevTimestamp)

            // 시(Hour)가 달라졌다면(예: 18:59:50 -> 19:00:10) 풀 포맷 반환
            if (currentHour != prevHour) {
                return fullFormat.format(currentTimestamp)
            }

            // [조건 3] 위 조건에 안 걸리면 짧은 포맷(mm:ss) 반환
            return shortFormat.format(currentTimestamp)
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

        val charts = listOf(
            ppgGreenChart, heartRateChart, lightChart,
            accelerometerChart, gyroscopeChart, gravityChart
        )

        charts.forEach { chart ->
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM

            // [수정] 위에서 만든 스마트 포매터 적용
            chart.xAxis.valueFormatter = SmartTimeAxisFormatter()

            chart.description.isEnabled = false

            chart.setNoDataText("데이터 수신 대기 중...")
            chart.setNoDataTextColor(Color.GRAY)

            chart.xAxis.labelRotationAngle = 0f
            chart.setExtraOffsets(0f, 0f, 0f, 0f)
            chart.xAxis.yOffset = 5f

            // 7등분 유지 (겹침 방지)
            chart.xAxis.setLabelCount(7, true)
            chart.xAxis.setAvoidFirstLastClipping(false)

            chart.xAxis.textColor = Color.DKGRAY
            chart.xAxis.textSize = 9f

            chart.data?.dataSets?.forEach { dataSet ->
                if (dataSet is LineDataSet) {
                    dataSet.setDrawCircles(true)
                    dataSet.circleRadius = 1.5f
                }
            }
        }
        makeBufferChart()
        this.onBackPressedDispatcher.addCallback(this, callback)
    }

    private suspend fun getSensorData(axisName: String, bin: Int, len: Long) {
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
    }

    private fun summaryOneAxisData(dataList : MutableMap.MutableEntry<String, List<AbstractSensor>>, bin: Int, len: Long) {
        val startTime = System.currentTimeMillis() / 1000L
        val bufferQueue = ArrayDeque<Entry>()
        val sensorTemp = ArrayList<Pair<Int, Double>>()
        val count = ArrayList<Int>()

        val size = (len / bin).toInt()
        for (i in 0 until size) {
            sensorTemp.add(Pair((i * bin), 0.0))
            count.add(0)
        }

        val sensorName = dataList.key
        val sensorData = dataList.value

        for (data in sensorData) {
            val time = data.time / 1000L
            val dif = startTime - time
            val idx = (dif / bin).toInt()

            if (idx in 0 until size) {
                val axisData = data as OneAxisData
                sensorTemp[idx] = Pair(sensorTemp[idx].first, sensorTemp[idx].second + axisData.value)
                count[idx] = count[idx] + 1
            }
        }

        for (i in 0 until size) {
            if (count[i] > 0) {
                val yVal = (sensorTemp[i].second / count[i]).toFloat()
                bufferQueue.add(Entry(sensorTemp[i].first.toFloat(), yVal))
            }
        }

        if (bufferQueue.isNotEmpty()) {
            runOnUiThread {
                when {
                    sensorName.contains("Light", true) -> updateChart(lightChart, bufferQueue, "Light [lx]")
                    sensorName.contains("Heart", true) -> updateChart(heartRateChart, bufferQueue, "HeartRate [bpm]")
                    sensorName.contains("Ppg", true) -> updateChart(ppgGreenChart, bufferQueue, "PPG Green [a.u.]")
                }
            }
        }
    }

    private fun summaryThreeAxisData(dataList : MutableMap.MutableEntry<String, List<AbstractSensor>>, bin: Int, len: Long) {
        val startTime = System.currentTimeMillis() / 1000L
        val size = (len / bin).toInt()
        val bufferQueues = listOf(ArrayDeque(), ArrayDeque(), ArrayDeque<Entry>())
        val sensorTemps = listOf(ArrayList(), ArrayList(), ArrayList<Pair<Int, Double>>())
        val count = ArrayList<Int>()

        for (i in 0 until size) {
            val timePoint = (i * bin)
            sensorTemps.forEach { it.add(Pair(timePoint, 0.0)) }
            count.add(0)
        }

        val sensorName = dataList.key
        val sensorData = dataList.value

        for (data in sensorData) {
            val time = data.time / 1000L
            val dif = startTime - time
            val idx = (dif / bin).toInt()

            if (idx in 0 until size) {
                val axisData = data as ThreeAxisData
                sensorTemps[0][idx] = Pair(sensorTemps[0][idx].first, sensorTemps[0][idx].second + axisData.xValue)
                sensorTemps[1][idx] = Pair(sensorTemps[1][idx].first, sensorTemps[1][idx].second + axisData.yValue)
                sensorTemps[2][idx] = Pair(sensorTemps[2][idx].first, sensorTemps[2][idx].second + axisData.zValue)
                count[idx] = count[idx] + 1
            }
        }

        for (i in 0 until size) {
            if (count[i] > 0) {
                for (j in 0..2) {
                    val yVal = (sensorTemps[j][i].second / count[i]).toFloat()
                    bufferQueues[j].add(Entry(sensorTemps[j][i].first.toFloat(), yVal))
                }
            }
        }

        if (bufferQueues[0].isNotEmpty()) {
            // 센서 종류에 따라 단위를 결정
            val unit = when {
                sensorName.contains("Accelerometer", true) -> "[m/s²]"
                sensorName.contains("Gyroscope", true) -> "[rad/s]"
                sensorName.contains("Gravity", true) -> "[m/s²]"
                else -> ""
            }

            // X, Y, Z 라벨 뒤에 단위
            val dataset = ArrayList<ILineDataSet>().apply {
                add(makeLineDataSet(bufferQueues[0], "X $unit").apply { color = Color.RED; setDrawCircles(true); circleRadius = 1.5f })
                add(makeLineDataSet(bufferQueues[1], "Y $unit").apply { color = Color.GREEN; setDrawCircles(true); circleRadius = 1.5f })
                add(makeLineDataSet(bufferQueues[2], "Z $unit").apply { color = Color.BLUE; setDrawCircles(true); circleRadius = 1.5f })
            }

            runOnUiThread {
                when {
                    sensorName.contains("Accelerometer", true) -> setChartData(dataset, accelerometerChart)
                    sensorName.contains("Gyroscope", true) -> setChartData(dataset, gyroscopeChart)
                    sensorName.contains("Gravity", true) -> setChartData(dataset, gravityChart)
                }
            }
        }
    }

    private fun updateChart(chart: LineChart, dataList: ArrayDeque<Entry>, label: String) {
        val dataset = ArrayList<ILineDataSet>().apply {
            add(makeLineDataSet(dataList, label).apply { setDrawCircles(true); circleRadius = 1.5f })
        }
        setChartData(dataset, chart)
    }

    private fun makeBufferChart() {
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                CoroutineScope(Dispatchers.IO).launch {
                    getSensorData("OneAxis", 10, 10 * 60)
                    getSensorData("ThreeAxis", 10, 10 * 60)
                }
            }
        }, 0, 10000)
    }

    private fun makeLineDataSet(dataList : ArrayDeque<Entry>, name :String): LineDataSet {
        return LineDataSet(dataList.toList(), name).apply {
            lineWidth = 1.5f
            setDrawValues(false)
            setDrawCircles(true)
        }
    }

    private fun setChartData(dataset : ArrayList<ILineDataSet>, chart : LineChart) {
        chart.data = LineData(dataset)
        chart.notifyDataSetChanged()
        chart.invalidate()
    }
}