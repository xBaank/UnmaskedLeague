package unmaskedLeague

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rtmp.Amf0MessagesHandler
import rtmp.packets.RawRtmpPacket

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

fun LeagueProxyClient(host: String, port: Int): LeagueProxyClient {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socketServer = aSocket(selectorManager).tcp().bind()

    return LeagueProxyClient(socketServer, host, port)
}

class LeagueProxyClient internal constructor(
    val serverSocket: ServerSocket,
    private val host: String,
    private val port: Int,
) {
    suspend fun start() = coroutineScope {
        while (isActive) {
            val socket = serverSocket.accept()
            println("Accepted connection from ${socket.remoteAddress} in ${socket.localAddress}")
            launch(Dispatchers.IO) { handle(socket) }
        }
    }

    private suspend fun handle(socket: Socket) = coroutineScope {
        runCatching {
            handleSocket(socket)
        }.onFailure {
            when (it) {
                is ClosedReceiveChannelException -> return@coroutineScope
                is CancellationException -> return@coroutineScope
                else -> throw it
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

        val incomingPartialRawMessages: MutableMap<Byte, RawRtmpPacket> = mutableMapOf()

        handshake(serverReadChannel, clientWriteChannel, clientReadChannel, serverWriteChannel)

        val inputMessagesHandler = Amf0MessagesHandler(
            incomingPartialRawMessages = incomingPartialRawMessages,
            input = clientReadChannel,
            output = serverWriteChannel,
            interceptor = {
                println("REQUEST")
                it.apply(::println)
            }
        )

        val outputMessageHandler = Amf0MessagesHandler(
            incomingPartialRawMessages = incomingPartialRawMessages,
            input = serverReadChannel,
            output = clientWriteChannel,
            interceptor = {
                println("RESPONSE")
                it.apply(::println)
            }
        )

        launch(Dispatchers.IO) {
            inputMessagesHandler.start()
        }

        launch(Dispatchers.IO) {
            outputMessageHandler.start()
        }
    }
}