package model.data

import java.io.Serializable

data class MoveInformation(
    val oriNode: Node,
    val newNode: Node
) : Serializable