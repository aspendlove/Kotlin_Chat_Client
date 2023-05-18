/**
 * Creates and connects a client object. This class is the bare minimum to implement a Client object
 *
 * @param args
 */
fun main(args: Array<String>) {
    val client = Client(
        { messages: List<String> -> onMessage(messages) },
        { onDisconnect() },
        { hostAndPort: String -> onConnect(hostAndPort) },
        { message: String -> onVerify(message) },
        "127.0.0.1",
        7071,
        "Default",
        "AAAAA"
    )

    client.start()

    while (true) { // keep the main thread running
    }
}


/**
 * Handle received messages
 *
 * @param messages
 */
fun onMessage(messages: List<String>) {
    for (message: String in messages) {
        println(message)
    }
    println()
}


/**
 * Handle disconnects from the server
 *
 */
fun onDisconnect() {
    // TODO log disconnections
}

/**
 * Handle successful connections
 *
 * @param hostAndPort
 */
fun onConnect(hostAndPort: String) {
    // TODO log connections
}

/**
 * Handle receiving sent messages back from the server
 *
 * @param received
 */
fun onVerify(received: String) {
    // TODO handle verifications
}
