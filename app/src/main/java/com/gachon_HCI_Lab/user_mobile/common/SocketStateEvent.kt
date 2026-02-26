package com.gachon_HCI_Lab.user_mobile.common

import com.gachon_HCI_Lab.user_mobile.common.SocketState

data class SocketStateEvent(val state: SocketState) {
    val stateName: String = state.name
}