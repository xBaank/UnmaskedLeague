package unmaskedLeague

import arrow.core.getOrElse
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rtmp.Amf0MessagesHandler
import rtmp.amf0.*
import rtmp.packets.RawRtmpPacket
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
        val completedRawMessages: MutableSharedFlow<RawRtmpPacket> = MutableSharedFlow()
        val outgoingPartialRawMessages: MutableSharedFlow<RawRtmpPacket> = MutableSharedFlow()

        handshake(serverReadChannel, clientWriteChannel, clientReadChannel, serverWriteChannel)

        val inputMessagesHandler = Amf0MessagesHandler(
            incomingPartialRawMessages = incomingPartialRawMessages,
            completedRawMessages = completedRawMessages,
            outgoingPartialRawMessages = outgoingPartialRawMessages,
            input = clientReadChannel,
            output = serverWriteChannel,
            interceptor = ::unmask
        )

        val outputMessageHandler = Amf0MessagesHandler(
            incomingPartialRawMessages = incomingPartialRawMessages,
            completedRawMessages = completedRawMessages,
            outgoingPartialRawMessages = outgoingPartialRawMessages,
            input = serverReadChannel,
            output = clientWriteChannel,
            interceptor = { it }
        )

        launch(Dispatchers.IO) {
            inputMessagesHandler.start()
        }

        launch(Dispatchers.IO) {
            outputMessageHandler.start()
        }
    }

    private fun unmask(nodes: List<Amf0Node>): List<Amf0Node> {
        val body = nodes.firstOrNull { it["body"] != null }?.get("body")

        val isCompressed = body?.get("compressedPayload")?.toAmf0Boolean()?.value ?: return nodes
        val payloadGzip = body["payload"].toAmf0String()?.value ?: return nodes

        val json = if (isCompressed) payloadGzip.base64Ungzip() else payloadGzip
        val payload = json.deserialized().getOrElse { throw it } // Can this come in other formats?

        if (payload["queueId"].asInt().getOrNull() != SOLOQ_ID) return nodes

        val localCellID = payload["championSelectState"]["localPlayerCellId"].asInt().getOrNull()

        payload["championSelectState"]["cells"]["alliedTeam"].asArray().getOrNull()?.forEach {
            if (it["cellId"].asInt().getOrNull() == localCellID) return@forEach
            if (it["nameVisibilityType"].isRight()) it["nameVisibilityType"] = "VISIBLE"
        }

        val serialized = payload.serialized()
        body["payload"] = if (isCompressed) serialized.gzipBase64().toAmf0String() else serialized.toAmf0String()

        return nodes
    }
}