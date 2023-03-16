package unmaskedLeague

import arrow.core.getOrElse
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rtmp.Amf0MessagesHandler
import rtmp.amf0.*
import simpleJson.*

private const val SOLOQ_ID = 420

private suspend fun handshake(
    serverReadChannel: ByteReadChannel,
    clientWriteChannel: ByteWriteChannel,
    clientReadChannel: ByteReadChannel,
    serverWriteChannel: ByteWriteChannel
) {
    //TODO Could we use the same handshake for all connections so we don't alloocate 1536 bytes for each connection?
    val c0 = serverReadChannel.readByte()
    clientWriteChannel.writeByte(c0)
    val c1 = ByteArray(1536)
    serverReadChannel.readFully(c1, 0, c1.size)
    clientWriteChannel.writeFully(c1, 0, c1.size)

    val s0 = clientReadChannel.readByte()
    serverWriteChannel.writeByte(s0)
    val s1 = ByteArray(1536)
    clientReadChannel.readFully(s1, 0, s1.size)
    serverWriteChannel.writeFully(s1, 0, s1.size)

    if (s0 != c0) throw IllegalStateException("c0 and s0 are not equal")

    val s0Echo = ByteArray(1536)
    clientReadChannel.readFully(s0Echo, 0, s0Echo.size)
    serverWriteChannel.writeFully(s0Echo, 0, s0Echo.size)

    val c1Echo = ByteArray(1536)
    serverReadChannel.readFully(c1Echo, 0, c1Echo.size)
    clientWriteChannel.writeFully(c1Echo, 0, c1Echo.size)
}

fun LeagueProxyClient(proxyPort: Int, host: String, port: Int): LeagueProxyClient {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socketServer = aSocket(selectorManager).tcp().bind(port = proxyPort)

    return LeagueProxyClient(socketServer, host, port)
}

class LeagueProxyClient internal constructor(
    private val serverSocket: ServerSocket,
    private val host: String,
    private val port: Int
) {
    suspend fun start() = coroutineScope {
        while (isActive) {
            val socket = serverSocket.accept()
            println("Accepted connection from ${socket.remoteAddress}")
            launch(Dispatchers.IO) {
                runCatching {
                    handleSocket(socket)
                }.onFailure {
                    println("Error handling socket: ${socket.remoteAddress}")
                    it.printStack()
                }
            }
        }
    }

    private suspend fun handleSocket(socket: Socket) = coroutineScope {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val clientSocket = aSocket(selectorManager).tcp().connect(host, port).tls(Dispatchers.IO)

        val serverReadChannel = socket.openReadChannel()
        val serverWriteChannel = socket.openWriteChannel(autoFlush = true)
        val clientReadChannel = clientSocket.openReadChannel()
        val clientWriteChannel = clientSocket.openWriteChannel(autoFlush = true)

        handshake(serverReadChannel, clientWriteChannel, clientReadChannel, serverWriteChannel)

        val messagesHandler = Amf0MessagesHandler(clientReadChannel, serverWriteChannel, ::unmask)

        launch(Dispatchers.IO) {
            messagesHandler.start()
        }

        launch(Dispatchers.IO) {
            val lolClientByteArray = ByteArray(1024)
            while (isActive) {
                val bytes = serverReadChannel.readAvailable(lolClientByteArray)
                println("Received $bytes bytes from the client")

                if (bytes == -1) {
                    println("Socket ${socket.remoteAddress} closed connection")
                    socket.close()
                    return@launch
                }

                clientWriteChannel.writeFully(lolClientByteArray, 0, bytes)
            }
        }

    }

    private fun unmask(nodes: List<Amf0Node>): List<Amf0Node> {
        val body = nodes.firstOrNull { it["body"] != null }?.get("body")

        val isCompressed = body?.get("compressedPayload")?.toAmf0Boolean()?.value ?: return nodes
        val payloadGzip = body["payload"].toAmf0String()?.value ?: return nodes

        println("Unmasking payload")
        println("Compressed: $isCompressed")

        val json = if (isCompressed) payloadGzip.base64Ungzip() else payloadGzip
        val payload = json.deserialize().getOrElse { throw it } // Can this come in other formats?

        if (payload["queueId"].asInt().getOrNull() != SOLOQ_ID) return nodes

        payload["championSelectState"]["cells"]["alliedTeam"].asArray().getOrNull()?.forEach {
            if (it["nameVisibilityType"].isRight()) it["nameVisibilityType"] = "UNHIDDEN"
        }

        val serialized = payload.serialize()
        body["payload"] = if (isCompressed) serialized.gzipBase64().toAmf0String() else serialized.toAmf0String()

        return nodes
    }
}