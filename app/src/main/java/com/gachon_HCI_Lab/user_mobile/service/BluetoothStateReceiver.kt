package com.gachon_HCI_Lab.user_mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import com.gachon_HCI_Lab.user_mobile.common.BTManager

class BluetoothStateReceiver(
    private val context: Context,
    private val devices: MutableSet<BluetoothDevice>
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED == action) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR)
            when (state) {
                BluetoothAdapter.STATE_CONNECTED -> {
                    Log.d("BluetoothStateReceiver", "Bluetooth Connected")
                }
                BluetoothAdapter.STATE_DISCONNECTED -> {
                    Log.d("BluetoothStateReceiver", "Bluetooth Disconnected")
                    BTManager.checkAndLogConnection(this.context, devices)
                }
            }
        }
    }
}