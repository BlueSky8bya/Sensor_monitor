package com.gachon_HCI_Lab.user_mobile.common

data class ThreadStateEvent(val state: ThreadState) {
    val stateName: String = state.name
}
