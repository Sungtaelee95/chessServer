import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.Header
import model.data.MoveInformation
import model.data.PieceColor
import java.io.InputStream
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
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(21)
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    val receivedBytes = buffer.copyOf(bytesRead)
                    val header = receivedBytes[0] // 첫 번째 바이트는 헤더
                    val data = receivedBytes.sliceArray(1 until receivedBytes.size) // 나머지 데이터
                    CoroutineScope(Dispatchers.IO).launch {
                        when (header) {
                            // 슬로우
                            Header.GET_SLOW_CLIENT_COLOR_HEADER.byte -> getSlowColor(inputStream)
                            Header.SEND_MOVE_SLOW_HEADER.byte -> receiveMoveInformation()
                            // 일반
                            Header.GET_TURN_COLOR.byte -> getTurnColor()
                            Header.GET_CLIENT_COLOR.byte -> getClientColor(data)
                            else -> throw CommendException()
                        }
                    }
                }

            }
        } catch (e: Exception) {
            println("${socket.inetAddress}::${socket.port} 통신 오류로 인한 연결 해제: ${e.message}")
        } finally {
            disconnect()
            println("${socket.inetAddress}::${socket.port} 연결 해제 완료")
        }

    }

    private fun getClientColor(data: ByteArray) {
        println("getClientColor()")
        val sizeArray = ByteArray(ProtocolSetting.DATA_LENGTH.value) { data[it] }
        val size = ByteBuffer.wrap(sizeArray).getInt()
        val contentArray = ByteArray(size) { data[it + size] }
        try {
            val sendByteArray = setSendByteArray(Header.GET_CLIENT_COLOR, byteArrayOf(server.getPieceColor().colorByte))
            CoroutineScope(Dispatchers.IO).launch {
                val outputStream = socket.getOutputStream()
                outputStream.write(sendByteArray)
                outputStream.flush()
                println("색상값 전달함.")
            }
        } catch (e: Exception) {
            println(e.message)
            println("컬러 전달 간 오류")
            socket.close()
        }
    }


    private fun getSlowColor(inputStream: InputStream) {
        println("getSlowColor()")

        try {
            val sendColor = server.getPieceColor().colorByte
            val sendBytes = setSendByteArray(Header.GET_SLOW_CLIENT_COLOR_HEADER, byteArrayOf(sendColor))
            socket.getOutputStream().write(sendBytes)
            socket.getOutputStream().flush()
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

    private fun getTurnColor() {
        val pw = PrintWriter(socket.outputStream, true)
        val br = socket.getInputStream().bufferedReader()
        val sizeArray = ByteArray(ProtocolSetting.DATA_LENGTH.value) { br.readLine().toByte() }
        val size = ByteBuffer.wrap(sizeArray).getInt()
        val contentArray = ByteArray(size) { br.readLine().toByte() }


    }

    fun sendMoveInformation(moveInformation: MoveInformation) {
        val sendBytes = setSendByteArray(Header.RECEIVE_MOVE_HEADER, moveInformation.toByteArray())
        CoroutineScope(Dispatchers.IO).launch {
            socket.outputStream.write(sendBytes)
            socket.outputStream.flush()
        }
    }

    private fun disconnect() {
        if (socket.isClosed) return
        socket.close()
        server.removeClient(this)
        println("${socket.inetAddress}:${socket.port} 연결 해제.")
    }

    private fun setSendByteArray(header: Header, content: ByteArray): ByteArray {
        val dataLengthByte = ByteBuffer
            .allocate(ProtocolSetting.DATA_LENGTH.value)
            .putInt(content.size)
            .array()

        val sendBytes = ByteArray(content.size + 5) { index ->
            when (index) {
                0 -> header.byte
                in 1..dataLengthByte.size -> dataLengthByte[index - 1]
                else -> content[index - 5]
            }
        }
        return sendBytes
    }
}


enum class ProtocolSetting(val value: Int) {
    HEAD_LENGTH(1),
    DATA_LENGTH(4),
    POSITION_DATA_LENGTH(4),
    COLOR_DATA_LENGTH(1)
}

class CommendException() : Exception()