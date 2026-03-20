package com.gachon_HCI_Lab.user_mobile.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gachon_HCI_Lab.user_mobile.common.BTManager
import com.gachon_HCI_Lab.user_mobile.common.CsvController // [추가] 파일 로그 쓰기용

@Suppress("DEPRECATION")
class BluetoothStateReceiver(
    private val context: Context,
    private val devices: MutableSet<BluetoothDevice>
) : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action

        // 어떤 기기에서 발생한 이벤트인지 확인 (OS 버전에 따라 null일 수 있으므로 안전하게 처리)
        val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val deviceName = device?.name ?: "Unknown Device"

        // [최종 덫 1] OS가 리시버를 호출할 때마다 무조건 파일에 기록
        CsvController.writeLog("BT_RECEIVER: 브로드캐스트 수신 -> Action: $action, 대상 기기: $deviceName")

        if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED == action) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR)
            val prevState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, BluetoothAdapter.ERROR)

            // [최종 덫 2] 블루투스 상태가 정확히 어떻게 변했는지 문자열로 변환해서 기록
            val stateStr = stateToString(state)
            val prevStateStr = stateToString(prevState)

            CsvController.writeLog("BT_CONNECTION_CHANGE: 상태 변경 감지됨 ($prevStateStr -> $stateStr)")

            when (state) {
                BluetoothAdapter.STATE_CONNECTED -> {
                    CsvController.writeLog("BT_HARDWARE: 기기($deviceName) 물리적 연결 완료!")
                }
                BluetoothAdapter.STATE_DISCONNECTED -> {
                    // [가장 중요한 덫] 안드로이드 OS 시스템이 물리적 연결이 끊어졌음을 땅땅땅 확정 지은 순간
                    CsvController.writeLog("BT_HARDWARE_DROP: 안드로이드 OS가 기기($deviceName)와의 물리적 연결 끊김을 감지했습니다!")
                    BTManager.checkAndLogConnection(this.context, devices)
                }
                BluetoothAdapter.STATE_CONNECTING -> {
                    CsvController.writeLog("BT_HARDWARE: 기기 연결 시도 중...")
                }
                BluetoothAdapter.STATE_DISCONNECTING -> {
                    CsvController.writeLog("BT_HARDWARE: 기기 연결 해제 중...")
                }
                else -> {
                    CsvController.writeLog("BT_HARDWARE: 알 수 없는 상태 코드 ($state)")
                }
            }
        }
    }

    // 상태 코드를 보기 쉬운 문자열로 변환해주는 헬퍼 함수
    private fun stateToString(state: Int): String {
        return when (state) {
            BluetoothAdapter.STATE_DISCONNECTED -> "DISCONNECTED"
            BluetoothAdapter.STATE_CONNECTING -> "CONNECTING"
            BluetoothAdapter.STATE_CONNECTED -> "CONNECTED"
            BluetoothAdapter.STATE_DISCONNECTING -> "DISCONNECTING"
            else -> "UNKNOWN($state)"
        }
    }
}