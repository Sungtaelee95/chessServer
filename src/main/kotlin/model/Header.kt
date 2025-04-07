package model

enum class Header(val byte: Byte) {
    GET_SLOW_CLIENT_COLOR_HEADER(0xC1.toByte()),
    SEND_MOVE_SLOW_HEADER(0xC2.toByte()),
    RECEIVE_MOVE_HEADER(0xC3.toByte()),
    GET_TURN_COLOR(0xC4.toByte()),
    GET_CLIENT_COLOR(0xC5.toByte())
}
