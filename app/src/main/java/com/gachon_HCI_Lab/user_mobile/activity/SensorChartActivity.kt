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
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.gachon_HCI_Lab.user_mobile.databinding.ActivityChartBinding
import com.gachon_HCI_Lab.user_mobile.sensor.model.AbstractSensor
import com.gachon_HCI_Lab.user_mobile.sensor.model.OneAxisData
import com.gachon_HCI_Lab.user_mobile.sensor.model.ThreeAxisData
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
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

        // 1. 차트 초기화 (XML 순서에 맞춰 Gravity를 하단으로)
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
            chart.xAxis.valueFormatter = LineChartXAxisValueFormatter()
            chart.description.isEnabled = false

            chart.setNoDataText("데이터 수신 대기 중...")
            chart.setNoDataTextColor(Color.GRAY)

            // --- [디자인 수정 적용] ---

            // 1. X축 레이블 각도 회전 (공백 명확히 유지)
            chart.xAxis.labelRotationAngle = -45f

            // 2. 레이블 출력 갯수 제한
            chart.xAxis.setLabelCount(6, false)

            // 3. 레이블 잘림 방지 및 간격 조정
            chart.xAxis.setAvoidFirstLastClipping(true)
            chart.xAxis.yOffset = 10f

            // 4. 차트 하단 여백 추가 (회전된 텍스트 공간 확보)
            chart.setExtraOffsets(0f, 0f, 0f, 30f)

            // 5. 텍스트 디자인 (sp 대신 Float 값 사용)
            chart.xAxis.textColor = Color.DKGRAY
            chart.xAxis.textSize = 10f // 10sp 대신 10f로 수정

            // 6. 데이터가 이미 있을 경우 (Gap 처리 유지)
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
            // [데이터 투명성] 데이터가 수집된 구간만 Entry 추가 (0f 강제 주입 제거)
            if (count[i] > 0) {
                val yVal = (sensorTemp[i].second / count[i]).toFloat()
                bufferQueue.add(Entry(sensorTemp[i].first.toFloat(), yVal))
            }
        }

        if (bufferQueue.isNotEmpty()) {
            runOnUiThread {
                when {
                    sensorName.contains("Light", true) -> updateChart(lightChart, bufferQueue, "Light")
                    sensorName.contains("Heart", true) -> updateChart(heartRateChart, bufferQueue, "HeartRate")
                    sensorName.contains("Ppg", true) -> updateChart(ppgGreenChart, bufferQueue, "PPG Green")
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
            // [데이터 투명성] 데이터가 존재하는 구간만 차트에 추가
            if (count[i] > 0) {
                for (j in 0..2) {
                    val yVal = (sensorTemps[j][i].second / count[i]).toFloat()
                    bufferQueues[j].add(Entry(sensorTemps[j][i].first.toFloat(), yVal))
                }
            }
        }

        if (bufferQueues[0].isNotEmpty()) {
            val dataset = ArrayList<ILineDataSet>().apply {
                add(makeLineDataSet(bufferQueues[0], "X").apply { color = Color.RED; setDrawCircles(true); circleRadius = 1.5f })
                add(makeLineDataSet(bufferQueues[1], "Y").apply { color = Color.GREEN; setDrawCircles(true); circleRadius = 1.5f })
                add(makeLineDataSet(bufferQueues[2], "Z").apply { color = Color.BLUE; setDrawCircles(true); circleRadius = 1.5f })
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
        // toList()를 사용하여 실시간 데이터 스냅샷을 생성
        return LineDataSet(dataList.toList(), name).apply {
            lineWidth = 1.5f
            setDrawValues(false)
            setDrawCircles(true) // 데이터 포인트를 명확히 시각화
        }
    }

    private fun setChartData(dataset : ArrayList<ILineDataSet>, chart : LineChart) {
        chart.data = LineData(dataset)
        chart.notifyDataSetChanged()
        chart.invalidate()
    }
}