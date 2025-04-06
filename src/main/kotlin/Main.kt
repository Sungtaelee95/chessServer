import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.Header
import model.data.MoveInformation
import model.data.PieceColor
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

fun main() {
    Server().open()
}

class Server() {
    private lateinit var _serverSocket: ServerSocket
    private val _clients = HashSet<Client>()

    fun open() {
        _serverSocket = ServerSocket(33769)
        while (true) {
            val clientSocket = _serverSocket.accept()
            clientSocket.outputStream
            println("${clientSocket.inetAddress} 연결")
            val client = Client(clientSocket, this)
            _clients.add(client)
            CoroutineScope(Dispatchers.IO).launch {
                client.receiveClientMessage()
            }
        }
    }

    fun removeClient(client: Client) {
        _clients.remove(client)
    }

    fun otherClientSendMoveInformation(client: Client, information: MoveInformation) {
        _clients.forEach { otherClient ->
            if (otherClient != client) {
                client.sendMoveInformation(information)
            }
        }
    }

    fun getPieceColor() = if (_clients.size == 1) PieceColor.WHITE else PieceColor.BLACK
}

class Client(
    private val socket: Socket,
    private val server: Server
) {

    fun receiveClientMessage() {
        try {
            while (true) {
                val br = socket.inputStream.bufferedReader()
                val header = br.readLine() ?: break
                when (header.toByte()) {
                    Header.GET_SLOW_COLOR.byte -> getSlowColor()
                    Header.SEND_MOVE_SLOW_HEADER.byte -> receiveMoveInformation()
                    else -> throw CommendException()
                }
            }
        } catch (e: Exception) {
            println("${socket.inetAddress}::${socket.port} 통신 오류로 인한 연결 해제: ${e.message}")
        } finally {
            disconnect()
            println("${socket.inetAddress}::${socket.port} 연결 해제 완료")
        }

    }

    private fun getSlowColor() {
        val pw = PrintWriter(socket.outputStream, true)
        val br = socket.getInputStream().bufferedReader()
        val sizeArray = ByteArray(ProtocolSetting.DATA_LENGTH.value) { br.readLine().toByte() }
        val size = ByteBuffer.wrap(sizeArray).getInt()
        val contentArray = ByteArray(size) { br.readLine().toByte() }
        try {
            pw.println(server.getPieceColor().colorByte)
            println("색상값 전달함.")
        } catch (e: Exception) {
            println(e.message)
            println("컬러 전달 간 오류")
            socket.close()
        }
    }

    private fun receiveMoveInformation() {
        try {
            val br = socket.getInputStream().bufferedReader()
            val sizeArray = ByteArray(ProtocolSetting.DATA_LENGTH.value) { br.readLine().toByte() }
            val size = ByteBuffer.wrap(sizeArray).int
            val inputData = ByteArray(size) { br.readLine().toByte() }
            val moveInformation = MoveInformation.fromByteArray(inputData)
            println("${socket.port}가 ${moveInformation.oriNode.row},${moveInformation.oriNode.col}에서 ${moveInformation.newNode.row},${moveInformation.newNode.col}로 이동")
            server.otherClientSendMoveInformation(this, moveInformation)
        } catch (e: Exception) {
            println("움직임 로직 간 예외 발생 ${e.message}")
            disconnect()
        }
    }

    fun sendMoveInformation(moveInformation: MoveInformation) {
        val bytes = moveInformation.toByteArray()
        val dataLengthByte = ByteBuffer.allocate(ProtocolSetting.DATA_LENGTH.value).putInt(bytes.size).array()
        val sendBytes = ByteArray(bytes.size + 5) { index ->
            when (index) {
                0 -> Header.RECEIVE_MOVE_HEADER.byte
                in 1..dataLengthByte.size -> dataLengthByte[index - 1]
                else -> bytes[index - 5]
            }
        }
        val pw = PrintWriter(socket.outputStream, true)
        pw.println(sendBytes)
    }

    private fun disconnect() {
        if (socket.isClosed) return
        socket.close()
        server.removeClient(this)
        println("${socket.inetAddress}:${socket.port} 연결 해제.")
    }

}


enum class ProtocolSetting(val value: Int) {
    HEAD_LENGTH(1),
    DATA_LENGTH(4),
    POSITION_DATA_LENGTH(4),
    COLOR_DATA_LENGTH(1)
}

class CommendException() : Exception()