import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.Header
import model.data.MoveInformation
import model.data.PieceColor
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

fun main() {
    Server().open()
}


class Server() {
    private lateinit var _serverSocket: ServerSocket
    private var _turnColor = PieceColor.WHITE
    private val _clients = mutableListOf<Client>()

    fun open() {
        _serverSocket = ServerSocket(33769)
        while (true) {
            val clientSocket = _serverSocket.accept()
            clientSocket.outputStream
            println("${clientSocket.inetAddress} ${clientSocket.port}  연결")
            val client = Client(clientSocket, this)
            _clients.add(client)
            CoroutineScope(Dispatchers.IO).launch {
                client.receiveClientMessage()
            }
        }
    }

    fun removeClient(client: Client) {
        _turnColor = PieceColor.WHITE
        _clients.remove(client)
    }

    fun otherClientSendMoveInformation(client: Client, information: MoveInformation) {
        println("${_clients.size}명의 클라이언트 접속 중......")
        for (otherClient in _clients) {
            // 내가 왜.... otherClient.sendOtherClientMoveInformation(information) 말고
            // client.sendOtherClientMoveInformation(information)를 해놓고 몰랐을까....ㅅ..
            if (otherClient != client) otherClient.sendOtherClientMoveInformation(information)
        }
    }

    fun getTurnColor() = _turnColor

    fun changeTurnColor() {
        println("changeTurnColor")
        if (_turnColor == PieceColor.WHITE) {
            _turnColor = PieceColor.BLACK
            return
        }
        _turnColor = PieceColor.WHITE
    }

    fun getPieceColor() = if (_clients.size == 1) PieceColor.WHITE else PieceColor.BLACK
}

class Client(
    private val socket: Socket,
    private val server: Server,
) {

    fun receiveClientMessage() {
        try {
            while (true) {
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(21)
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    println("-------------------")
                    val receivedBytes = buffer.copyOf(bytesRead)
                    val header = receivedBytes[0] // 첫 번째 바이트는 헤더
                    val data = receivedBytes.sliceArray(1 until receivedBytes.size) // 나머지 데이터
                    when (header) {
                        // 슬로우
//                            Header.GET_SLOW_CLIENT_COLOR_HEADER.byte -> getSlowColor(inputStream)
//                            Header.SEND_MOVE_SLOW_HEADER.byte -> receiveMoveInformation()
                        // 일반
                        Header.GET_TURN_COLOR_HEADER.byte -> getTurnColor()
                        Header.GET_CLIENT_COLOR_HEADER.byte -> getClientColor(data)
                        Header.SEND_MOVE_INFORMATION_HEADER.byte -> receiveMoveInformation(data)
                        else -> throw CommendException()
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
            val sendByteArray =
                setSendByteArray(Header.GET_CLIENT_COLOR_HEADER, byteArrayOf(server.getPieceColor().colorByte))
            val outputStream = socket.getOutputStream()
            outputStream.write(sendByteArray)
            outputStream.flush()
            println("클라이언트 색상값 전달함.")
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

    private fun receiveMoveInformation(data: ByteArray) {
        println("sendMoveInformation")
        try {
            server.changeTurnColor()
            val sizeArray = ByteArray(ProtocolSetting.DATA_LENGTH.value) { data[it] }
            val size = ByteBuffer.wrap(sizeArray).getInt()
            val inputData = ByteArray(size) { data[it + ProtocolSetting.DATA_LENGTH.value] }
            val moveInformation = MoveInformation.fromByteArray(inputData)
            println("${socket.port}가 ${moveInformation.oriNode.row},${moveInformation.oriNode.col}에서 ${moveInformation.newNode.row},${moveInformation.newNode.col}로 이동")
            server.otherClientSendMoveInformation(this@Client, moveInformation)
        } catch (e: Exception) {
            println("움직임 로직 간 예외 발생 ${e.message}")
            disconnect()
        }
    }

    private fun getTurnColor() {
        println("getTurnColor()")
        val turnColor = server.getTurnColor()
        val sendData = setSendByteArray(Header.GET_TURN_COLOR_HEADER, byteArrayOf(turnColor.colorByte))
        socket.outputStream.write(sendData)
        socket.outputStream.flush()
        println("현재 턴 색상 전달")
    }

    fun sendOtherClientMoveInformation(moveInformation: MoveInformation) {
        println("sendOtherClientMoveInformation")
        val sendBytes =
            setSendByteArray(Header.GET_OTHER_CLIENT_MOVE_INFORMATION, moveInformation.toByteArray())
        println("${socket.port} 로 전달 : ${sendBytes.size}개의 바이트 배열")
        socket.outputStream.write(sendBytes)
        socket.outputStream.flush()
    }

    private fun disconnect() {
        socket.close()
        server.removeClient(this)
        println("${socket.inetAddress}:${socket.port} 연결 해제.")
    }

    private fun setSendByteArray(header: Header, content: ByteArray): ByteArray {
        println("setSendByteArray")
        val dataLengthByte = ByteBuffer
            .allocate(ProtocolSetting.DATA_LENGTH.value)
            .putInt(content.size)
            .array()

        val sendBytes = ByteArray(content.size + 5) { index ->
            when (index) {
                0 -> header.byte
                in 1..ProtocolSetting.DATA_LENGTH.value -> dataLengthByte[index - 1]
                else -> content[index - (ProtocolSetting.HEAD_LENGTH.value + ProtocolSetting.DATA_LENGTH.value)]
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