import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.CookieHandler


class Client(
    val onMessage: (List<String>) -> Unit,
    val onDisconnect: () -> Unit,
    val onConnect: (String) -> Unit,
    val onVerify: (String) -> Unit,
    private val host: String,
    private val port: Int,
) {
    enum class MessageType {
        Message, Name, Room, Disconnect
    }

    private var networkingScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var callbackScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private var socket: Socket? = null
    private var socketLock: Mutex = Mutex()

    private var connected: Boolean = false

    private var receiveChannel: ByteReadChannel? = null
    private var receiveChannelLock: Mutex = Mutex()

    var name: String = "Default"
    var room: String = "AAAAA"

    fun start() {
        if(connected) return

        val selectorManager = SelectorManager(Dispatchers.IO)
        runBlocking {
            socket = aSocket(selectorManager).tcp().connect(host, port)
        }
        connected = true
        receiveChannel = socket!!.openReadChannel()
        callbackScope.launch { onConnect("$host:$port") }
        networkingScope.launch {
            awaitMessages()
        }
    }

    suspend fun close() {
        if (!connected) return

        networkingScope.cancel()

        try {
            socketLock.withLock {
                socket?.close()
            }
        } catch (_: NullPointerException) { } // we don't need to handle the case where it is already disconnected

        connected = false

        onDisconnect()
    }

    private suspend fun awaitMessages() = coroutineScope {
        if (connected) {
            try {
                while (true) {
                    val messages: MutableList<String> = mutableListOf<String>()

                    val messageBuffer: StringBuilder = StringBuilder()
                    val receivedBytes = ByteArray(1024)

                    receiveChannelLock.withLock {
                        receiveChannel!!.readFully(receivedBytes)
//                        receiveChannel!!.readAvailable(receivedBytes) // TODO not receiving bytes
                    }

                    val currentMessage = receivedBytes.decodeToString()

                    if (currentMessage == "") {
                        continue
                    }

                    messageBuffer.append(currentMessage)

                    while (messageBuffer.contains(0.toChar())) {
                        val split: List<String> = messageBuffer.split(0.toChar(), limit = 2)
                        messages.add(split[0])
                        messageBuffer.clear()
                        messageBuffer.append(split[1])
                    }

                    callbackScope.launch {
                        onMessage(messages)
                    }
                }
            } catch (e: NullPointerException) {
                println("connection not initialized") // TODO switch to log
            }
        }
    }

    private suspend fun sendMessage(message: String, type: MessageType) {
        if (!connected) return;
    }

    fun sendMessage(message: String) {
        if (!connected) return;
        networkingScope.launch { sendMessage(message, MessageType.Message) }
    }

    suspend fun changeName(name: String) {
        if (!connected) return;
        synchronized(this.name) { this.name = name }
        networkingScope.launch {
            sendMessage(name, MessageType.Name)
        }
    }

    suspend fun changeRoom(room: String) {
        if (!connected) return;
        synchronized(this.room) { this.room = room }
        networkingScope.launch {
            sendMessage(room, MessageType.Room)
        }
    }
}
