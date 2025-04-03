import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.data.MoveInformation
import model.data.PieceColor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ServerSocket
import java.net.Socket

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
                launch {
                    client.receiveClientMessage()
                }
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
    fun sendMoveInformation(moveInformation: MoveInformation) {
        val oos = ObjectOutputStream(socket.outputStream)
        oos.writeObject(moveInformation)
        oos.flush()
    }

    fun receiveClientMessage() {
        try {
            while (true) {
                val br = socket.inputStream.bufferedReader()
                val bw = socket.outputStream.bufferedWriter()
                val commend = br.readLine() ?: break
                CoroutineScope(Dispatchers.IO).launch {
                    when (commend.toInt().toByte()) {
                        MOVE_SLOW_HEADER -> receiveMoveInformation(br)
                        COLOR_SLOW_HEADER -> receivePieceColor(bw)
                    }
                }
            }
        } catch (e: Exception) {
            println("${socket.inetAddress}::${socket.port} 오류로 인한 연결 해제: ${e.message}")
            disconnect()
        } finally {
            println("${socket.inetAddress}::${socket.port} 종료 요청으로 인한 연결 해제")
            disconnect()
        }

    }

    private fun receivePieceColor(bw: BufferedWriter) {
        try {
            bw.appendLine(server.getPieceColor().value)
            bw.flush()
        } catch (e: Exception) {
            println(e.message)
            println("컬러 전달 간 오류")
            socket.close()
        }
    }

    private fun receiveMoveInformation(br: BufferedReader) {
        try {
            val size = br.readLine().toInt()
            val inputData = ByteArray(size) { br.readLine().toInt().toByte() }
            val bais = ByteArrayInputStream(inputData)
            val ois = ObjectInputStream(bais)
            val moveInformation = ois.readObject() as MoveInformation
            println(moveInformation.toString())
            server.otherClientSendMoveInformation(this, moveInformation)
        } catch (e: Exception) {
            println("움직임 로직 간 예외 발생 ${e.message}")
            disconnect()
        }
    }

    private fun disconnect() {
        socket.close()
        server.removeClient(this)
        println("${socket.inetAddress}:${socket.port} 연결 해제.")
    }

    companion object {
        private const val MOVE_HEADER = 0x15.toByte()
        private const val COLOR_HEADER = 0X16.toByte()
        private const val MOVE_SLOW_HEADER = 0x17.toByte()
        private const val COLOR_SLOW_HEADER = 0X18.toByte()
    }
}