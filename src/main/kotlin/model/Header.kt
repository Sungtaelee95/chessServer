package model

enum class Header(val byte: Byte) {
    // 지연
    GET_SLOW_CLIENT_COLOR_HEADER(0xC1.toByte()),
    SEND_MOVE_SLOW_HEADER(0xC2.toByte()),

    // 일반
    GET_TURN_COLOR_HEADER(0xC4.toByte()),
    GET_CLIENT_COLOR_HEADER(0xC5.toByte()),
    SEND_MOVE_INFORMATION_HEADER(0xC6.toByte()),
    GET_OTHER_CLIENT_MOVE_INFORMATION(0xC7.toByte())
}
