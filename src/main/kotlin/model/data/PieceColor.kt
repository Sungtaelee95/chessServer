package model.data

enum class PieceColor(
    val value: String,
    val colorName: String,
) {
    WHITE("\u001B[37m", "흰색"),
    BLACK("\u001B[30m", "검은색"),
}
