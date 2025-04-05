package model.data

enum class PieceColor(
    val value: String,
    val colorName: String,
    val colorByte: Byte
) {
    WHITE("\u001B[37m", "흰색", 0x21.toByte()),
    BLACK("\u001B[30m", "검은색", 0x22.toByte()),
}
