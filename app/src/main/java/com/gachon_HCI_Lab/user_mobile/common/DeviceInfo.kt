package com.gachon_HCI_Lab.user_mobile.common

/**
 * 디바이스 정보를 저장하는 싱글톤 클래스
 * _deviceID : 디바이스 고유 ID 워치와 블루투스 소켓 연결시 가져온다.
 * _uID : 사용자 고유 ID 로그인시 사용자가 입력한다.
 * _battery : 디바이스 배터리 잔량 워치와 블루투스 소켓 연결시 가져온다.
 */
class DeviceInfo private constructor() {
    companion object{
        lateinit var _dID : String
        lateinit var _uID : String
        lateinit var _battery : String

        /**
         * 디바이스 정보를 초기화하는 함수
         * _deviceID : 디바이스 고유 ID
         * _uID : 사용자 고유 ID 로그인시 사용자가 입력한다.
         * _battery : 디바이스 배터리 잔량
         */
        fun init(deviceID : String = "", uID : String = "", battery: String = "100") {
            _dID = deviceID
            _uID = uID
            _battery = battery
        }

        /**
         * 정보 setter 함수 (변수가 모두 public으로 선언되어있기 떄문에 사용하지 않아도 됨)
         */
        fun setDeviceID(deviceID: String) {
            _dID = deviceID
        }

        fun setUID(uID: String) {
            _uID = uID
        }

        fun setBattery(battery: String) {
            _battery = battery
        }
    }
}