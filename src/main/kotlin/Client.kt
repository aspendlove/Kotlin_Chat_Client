import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers

class Client(
    val onMessage: (String) -> Void,
    val onDisconnect: () -> Void,
    val onConnect: (String) -> Void,
    val onVerify: (String) -> Void,
    private val host: String,
    private val port: Int,
) {
    private var socket: Socket? = null

    suspend fun connect() {
        val selectorManager = SelectorManager(Dispatchers.IO)
        socket = aSocket(selectorManager).tcp().connect(host, port)
    }

    private suspend fun awaitMessages() {
        try {
            val receiveChannel = socket!!.openReadChannel()
            val receivedBytes = ByteArray(1024)
            receiveChannel.readAvailable(receivedBytes)
        } catch (e: NullPointerException) {
            println("connection not initialized") // TODO switch to log
        }
    }

    fun close() {
        socket?.close()
    }
}
