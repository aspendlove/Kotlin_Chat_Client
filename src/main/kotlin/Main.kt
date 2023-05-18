import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    start()
}

fun start()= runBlocking {
//    val selectorManager = SelectorManager(Dispatchers.IO)
//    val socket = aSocket(selectorManager).tcp().connect("127.0.0.1", 7070)
//    print(socket)
//    val sendChannel = socket.openWriteChannel(autoFlush = true)
//    sendChannel.writeStringUtf8("<Message>Hello World!</Message>" + 0.toChar())
//    socket.close()
////    var client = Client()

    val client: Client = Client(
        { messages: List<String> -> onMessage(messages) },
        { onDisconnect() },
        { hostAndPort: String -> onConnect(hostAndPort) },
        { message: String -> onVerify(message) },
        "127.0.0.1",
        7070
    )
    launch {
        client.start()
    }
}

fun onMessage(messages: List<String>) {
    for(message:String in messages) {
        println(message)
    }
}

fun onDisconnect() {

}

fun onConnect(hostAndPort: String) {

}

fun onVerify(received: String) {

}
