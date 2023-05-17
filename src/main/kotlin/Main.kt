import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers

suspend fun main(args: Array<String>) {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socket = aSocket(selectorManager).tcp().connect("127.0.0.1", 7070)
    print(socket)
    val sendChannel = socket.openWriteChannel(autoFlush = true)
    sendChannel.writeStringUtf8("<Message>Hello World!</Message>" + 0.toChar())
    socket.close()
//    var client = Client()
}
