import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


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

    private val connectionJobs = mutableListOf<Job>()

    private var socket: Socket? = null
    private var socketLock: Mutex = Mutex()

    private var connected: Boolean = false

    private var receiveChannel: ByteReadChannel? = null
    private var receiveChannelLock: Mutex = Mutex()

    suspend fun connect() = runBlocking {
        if(!connected) {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val job = launch {
                socket = aSocket(selectorManager).tcp().connect(host, port)
            }
            job.join()
            connected = true
            receiveChannel = socket!!.openReadChannel()
            launch {
                onConnect("$host:$port")
            }
            connectionJobs.add(launch {
                awaitMessages()
            })
        }
    }

    suspend fun close() {
        if(!connected) return

        for(job: Job in connectionJobs) {
            job.cancelAndJoin()
        }

        try {
            socketLock.withLock {
                socket?.close()
            }
        } catch(_: NullPointerException){} // we don't need to handle the case where it is already disconnected

        connected = false

        onDisconnect()
    }

    private suspend fun awaitMessages() = coroutineScope {
        if(connected) {
            try {
                while (true) {
                    val messages: MutableList<String> = mutableListOf<String>()

                    val messageBuffer: StringBuilder = StringBuilder()
                    val receivedBytes = ByteArray(1024)

                    receiveChannelLock.withLock {
                        receiveChannel!!.readAvailable(receivedBytes) // TODO not receiving bytes
                    }

                    val currentMessage = receivedBytes.decodeToString()

                    if (currentMessage == "") {
                        continue
                    }

                    messageBuffer.append(currentMessage)

                    while (messageBuffer.contains(0.toChar())) {
                        val split: List<String> = messageBuffer.split(0.toChar(), limit=2)
                        messages.add(split[0])
                        messageBuffer.clear()
                        messageBuffer.append(split[1])
                    }

                    launch {
                        onMessage(messages)
                    }
                }
            } catch (e: NullPointerException) {
                println("connection not initialized") // TODO switch to log
            }
        }
    }

    private suspend fun sendMessage(message: String, type: MessageType) {
        if(!connected) return;
    }

    fun sendMessage(message: String) {
        if(!connected) return;
    }

    public fun changeName() {
        if(!connected) return;
    }

    public fun changeRoom() {
        if(!connected) return;
    }
}
