import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
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

    private var networkingScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private var callbackScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private var socket: Socket? = null
    private var socketLock: Mutex = Mutex()

    private var receiveChannel: ByteReadChannel? = null
    private var receiveChannelLock: Mutex = Mutex()

    private var sendChannel: ByteWriteChannel? = null
    private var sendChannelLock: Mutex = Mutex()

    private var connected: Boolean = false

    private var _name: String = "Default"
    private var _room: String = "AAAAA"
    val name: String
        get() {
            return _name
        }
    val room: String
        get() {
            return _room
        }

    val isConnected: Boolean
        get() {
            return (socket != null) && (receiveChannel != null) && (sendChannel != null) && connected
        }

    fun start() {
        if (isConnected) return

        val selectorManager = SelectorManager(Dispatchers.IO)
        runBlocking {
            socket = aSocket(selectorManager).tcp().connect(host, port)
        }
        receiveChannel = socket!!.openReadChannel()
        sendChannel = socket!!.openWriteChannel(autoFlush = true)
        connected = true
        callbackScope.launch { onConnect("$host:$port") }
        networkingScope.launch {
            awaitMessages()
        }

        networkingScope.launch {
            changeRoom(room)
            changeName(name)
        }
    }

    suspend fun close() {
        if (!isConnected) return

        sendMessage("", MessageType.Disconnect)

        networkingScope.cancel()

        receiveChannelLock.withLock {
            receiveChannel?.cancel()
        }
        socketLock.withLock {
            socket?.close()
        }

        connected = false

        callbackScope.launch {
            onDisconnect()
        }
    }

    private suspend fun awaitMessages() {
        if (isConnected) {
            try {
                val messageBuffer: StringBuilder = StringBuilder()
                while (true) {
                    val messages: MutableList<String> = mutableListOf()
                    val receivedBytes = ByteArray(1024)

                    receiveChannelLock.withLock {
                        receiveChannel!!.readAvailable(receivedBytes)
                    }

                    val fullBytes = mutableListOf<Byte>()

                    var termCharsInRow: Int = 0
                    for (byte: Byte in receivedBytes) {
                        if (byte != 0.toByte()) {
                            fullBytes.add(byte)
                            termCharsInRow = 0
                        } else {
                            if (++termCharsInRow == 2) {
                                break
                            } else {
                                fullBytes.add(byte)
                            }
                        }
                    }

                    val currentMessage = String(fullBytes.toByteArray())


                    if (currentMessage == "" + 0.toChar()) {
                        println("Disconnected") // TODO change to log
                        close()
                    }

                    messageBuffer.append(currentMessage)

                    while (messageBuffer.contains(0.toChar())) {
                        val split: List<String> = messageBuffer.split(0.toChar(), limit = 2)
                        messages.add(split[0])
                        messageBuffer.clear()
                        messageBuffer.append(split[1])
                    }

                    processMessages(messages)

                }
            } catch (e: NullPointerException) {
                println("connection not initialized") // TODO switch to log
            }
        }
    }

    private fun processMessages(messages: MutableList<String>) {
        val messageRegex: Regex = Regex("""<Message>(.*?)</Message>""")

        val processedMessages: MutableList<String> = mutableListOf()
        for (message: String in messages) {
            val messageMatches: Sequence<MatchResult> = messageRegex.findAll(message)
            for(match: MatchResult in messageMatches) {
                val extractedMessage: String = match.value.removeSurrounding("<Message>","</Message>")
                processedMessages.add(extractedMessage)
            }
        }

        callbackScope.launch {
            onMessage(processedMessages)
        }
    }

    private suspend fun sendMessage(message: String, type: MessageType) {
        if (!isConnected) return;

        val completedMessage = when (type) {
            MessageType.Message -> {
                "<Message>$message</Message>${0.toChar()}"
            }

            MessageType.Name -> {
                "<Name>$message</Name>${0.toChar()}"
            }

            MessageType.Room -> {
                "<RoomCode>$message</RoomCode>${0.toChar()}"
            }

            MessageType.Disconnect -> {
                "<Disconnect>${0.toChar()}"
            }
        }
        sendChannelLock.withLock {
            sendChannel!!.writeStringUtf8(completedMessage)
        }
    }

    fun sendMessage(message: String) {
        if (!isConnected) return;
        networkingScope.launch { sendMessage(message, MessageType.Message) }
    }

    suspend fun changeName(name: String) {
        if (!isConnected) return;
        synchronized(this._name) { this._name = name }
        networkingScope.launch {
            sendMessage(_name, MessageType.Name)
        }
    }

    suspend fun changeRoom(room: String) {
        if (!isConnected) return;
        synchronized(this._room) { _room = room }
        networkingScope.launch {
            sendMessage(_room, MessageType.Room)
        }
    }
}
