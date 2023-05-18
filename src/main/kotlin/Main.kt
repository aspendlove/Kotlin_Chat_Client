fun main(args: Array<String>) {
    val client = Client(
        { messages: List<String> -> onMessage(messages) },
        { onDisconnect() },
        { hostAndPort: String -> onConnect(hostAndPort) },
        { message: String -> onVerify(message) },
        "127.0.0.1",
        7071
    )

    client.start()

    while(true){
    }
}

fun onMessage(messages: List<String>) {
    for(message:String in messages) {
        println(message)
    }
    println()
}

fun onDisconnect() {

}

fun onConnect(hostAndPort: String) {

}

fun onVerify(received: String) {

}
