import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO add message verification and do not call onMessage if a verification is performed
// TODO add file logging
// TODO start unit tests


/**
 * The Client class handles connecting to a server, messages, and callbacks.
 * It implements the standard API set by Design.md.
 * This class is designed to be used aside a GUI or CLI interface that
 * implements the callbacks that Client uses. It is asynchronous by nature,
 * and handles network operations and callbacks on separate coroutine scopes.
 * Send however, is blocking in order to ensure that a connection is made before
 * other methods are called. Performing network operations without first calling
 * connect, will cause the methods to return without performing any operations
 *
 * @property onMessage a callback that is sent a list of messages received from the server
 * @property onDisconnect a callback to be called whenever the client disconnects from the server
 * @property onConnect a callback to be called whenever a successful connection is made. It provides a single string
 * in the form of host:port
 * @property onVerify a callback to be called whenever a sent message is received back from the server. This event
 * signifies that the server has received the message successfully. It is designed to be used alongside an external
 * storage of unverified messages so that the received message can be checked with those that have not been verified.
 * @property host the hostname or IP of the server
 * @property port the port the server listens on
 * @property _name the username of the client
 * @property _room the room the client belongs to
 * @constructor See class documentation
 */
class Client(
    private val onMessage: (List<String>) -> Unit,
    private val onDisconnect: () -> Unit,
    private val onConnect: (String) -> Unit,
    private val onVerify: (String) -> Unit,
    private val host: String,
    private val port: Int,
    private var _name: String,
    private var _room: String
) {
    /**
     * This enum defines the type of message that sendMessage() will send
     * The values correspond to message types in the API
     */
    enum class MessageType {
        Message,
        Name,
        Room,
        Disconnect
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

    /**
     * Gets the current username
     */
    val name: String
        get() {
            return _name
        }

    /**
     * Gets the current room code
     */
    val room: String
        get() {
            return _room
        }

    /**
     * Verifies that the client is connected to the server. True after connect() is called, and False again when close()
     * is called
     */
    val isConnected: Boolean
        get() {
            // since socket, receiveChannel, and sendChannel are nullable, isConnected can be used
            // to verify that any operations will not return a NullPointerException
            return (socket != null) && (receiveChannel != null) && (sendChannel != null) && connected
        }


    /**
     * Start performs two operations
     *  1. Connects to the server with the host and port from the constructor
     *  2. Asynchronously begins waiting for messages from the server
     *
     *  It is by default non-blocking in order to ensure that a connection is made before other methods
     *  are called
     */
    fun start() {
        if (isConnected) return

        val selectorManager = SelectorManager(Dispatchers.IO)
        runBlocking {
            socket = aSocket(selectorManager).tcp().connect(host, port)
        }
        // At this point socket will have been created, unless the above connection code throws an exception,
        // in which case we want to fail anyway
        receiveChannel = socket!!.openReadChannel()
        sendChannel = socket!!.openWriteChannel(autoFlush = true)
        connected = true // only set connection to true after all socket related objects have been initialized
        callbackScope.launch { onConnect("$host:$port") }
        networkingScope.launch { awaitMessages() }

        // Send two initial messages to the server to set the name and room
        networkingScope.launch {
            changeRoom(room)
            changeName(name)
        }
    }

    /**
     * Cancel all running network operations, close the connection to the server, and call the onDisconnect callback
     *
     */
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

    /**
     * Constantly await messages from the server, and handle them when they are received
     *
     */
    private suspend fun awaitMessages() {
        if (isConnected) {
            try {
                val messageBuffer: StringBuilder = StringBuilder() // any incomplete messages will be stored here
                while (true) {
                    val messages: MutableList<String> = mutableListOf() // there may be multiple messages at once
                    val receivedBytes = ByteArray(1024)

                    receiveChannelLock.withLock {
                        receiveChannel!!.readAvailable(receivedBytes)
                    }

                    val fullBytes = mutableListOf<Byte>()

                    // the byte array is always of size 1024, and we want to ignore any default bytes
                    // however, we must keep any single default 0 value bytes, because they represent a null char
                    var termCharsInRow = 0
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


                    if (currentMessage == "" + 0.toChar()) { // this means that we received an empty message
                        println("Disconnected") // TODO switch to log
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

    /**
     * Verify messages, and extract the underlying message from the <Message></Message> tags
     * This only handles message tags, because that is the only message that should be received
     * from a server following the API
     *
     * @param messages a list of messages received from the server
     */
    private fun processMessages(messages: MutableList<String>) {
        val messageRegex = Regex("""<Message>(.*?)</Message>""")

        val processedMessages: MutableList<String> = mutableListOf()
        for (message: String in messages) {
            val messageMatches: Sequence<MatchResult> = messageRegex.findAll(message)
            for (match: MatchResult in messageMatches) {
                val extractedMessage: String = match.value.removeSurrounding("<Message>", "</Message>")
                processedMessages.add(extractedMessage)
            }
        }

        callbackScope.launch {
            onMessage(processedMessages)
        }
    }

    /**
     * Send a message of the passed type. This should only be called internally,
     * because the only type of message that should be sent by an implementing
     * class should be of type message.
     *
     * @param message
     * @param type
     */
    private suspend fun sendMessage(message: String, type: MessageType) {
        if (!isConnected) return

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

    /**
     * Send a message string. The message tags and null chars are handled internally,
     * and the message string should only contain the actual message
     *
     * @param message the string to be sent to the server
     */
    fun sendMessage(message: String) {
        if (!isConnected) return
        networkingScope.launch { sendMessage(message, MessageType.Message) }
    }

    /**
     * Update the username internally and with the server
     *
     * @param name
     */
    suspend fun changeName(name: String) {
        if (!isConnected) return
        synchronized(this._name) { this._name = name }
        networkingScope.launch {
            sendMessage(_name, MessageType.Name)
        }
    }

    /**
     * Update the room code internally and with the server
     *
     * @param room
     */
    suspend fun changeRoom(room: String) {
        if (!isConnected) return
        synchronized(this._room) { _room = room }
        networkingScope.launch {
            sendMessage(_room, MessageType.Room)
        }
    }
}
