package model.data

import java.nio.ByteBuffer

data class Node(
    val row: Int,
    val col: Int,
) {
    fun getByteArray(): ByteArray {
        val rByte = ByteBuffer
            .allocate(ProtocolSetting.POSITION_DATA_LENGTH.value)
            .putInt(row)
            .array()
        val cByte = ByteBuffer
            .allocate(ProtocolSetting.POSITION_DATA_LENGTH.value)
            .putInt(col)
            .array()

        return rByte + cByte
    }
}