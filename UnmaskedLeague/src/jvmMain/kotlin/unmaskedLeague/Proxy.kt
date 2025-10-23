package unmaskedLeague

import arrow.core.getOrElse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import rtmp.Amf0MessagesHandler
import rtmp.amf0.*
import simpleJson.*

private const val SOLOQ_ID = 420

private suspend fun handshake(
    serverReadChannel: ByteReadChannel,
    clientWriteChannel: ByteWriteChannel,
    clientReadChannel: ByteReadChannel,
    serverWriteChannel: ByteWriteChannel,
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

suspend fun LeagueProxyClient(host: String, port: Int): LeagueProxyClient {
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socketServer = aSocket(selectorManager).tcp().bind()

    return LeagueProxyClient(socketServer, host, port)
}

class LeagueProxyClient internal constructor(
    val serverSocket: ServerSocket,
    private val host: String,
    private val port: Int,
) {
    private val logger = KotlinLogging.logger { }
    suspend fun start() = coroutineScope {
        while (isActive) {
            val socket = serverSocket.accept()
            logger.info { "Accepted connection from ${socket.remoteAddress} in ${socket.localAddress}" }
            launch(Dispatchers.IO) {
                handle(socket)
            }
        }
    }

    private suspend fun handle(socket: Socket) = coroutineScope {
        runCatching {
            handleSocket(socket)
        }.onFailure {
            logger.error { it }
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

        val lolServerToClientProxy = async(Dispatchers.IO) {
            messagesHandler.start()
        }

        //lolCLient -> proxy -> lolServer
        //We don't need to intercept these messages
        val lolClientToServerProxy = async(Dispatchers.IO) {
            val lolClientByteArray = ByteArray(1024)
            while (isActive) {
                val bytes = serverReadChannel.readAvailable(lolClientByteArray)

                if (bytes == -1) {
                    socket.close()
                    clientSocket.close()
                    cancel("Socket closed")
                    return@async
                }

                clientWriteChannel.writeFully(lolClientByteArray, 0, bytes)
            }
        }

        awaitAll(lolServerToClientProxy, lolClientToServerProxy)
    }

    private suspend fun unmask(nodes: List<Amf0Node>): List<Amf0Node> {
        println(
            getSummonersData(
                listOf(
                    "9e216ea5-f64f-5e9c-ba00-477b5eaf1002",
                    "e2390091-fac4-5ecb-b818-53aef979cbbc",
                    "bbc9a6d9-34d5-5d98-acbe-6c1e7bca11c6",
                    "b812f96f-3e35-58eb-932f-5a21bb3b2abc"
                )
            )
        )
        val body = nodes.firstOrNull { it["body"] != null }?.get("body")

        val isCompressed = body?.get("compressedPayload")?.toAmf0Boolean()?.value ?: return nodes
        val payloadGzip = body["payload"].toAmf0String()?.value ?: return nodes

        val json = if (isCompressed) payloadGzip.base64Ungzip() else payloadGzip
        val payload = json.deserialized().getOrElse { throw it } // Can this come in other formats?

        if (payload["championSelectState"]["showQuitButton"].isRight()) {
            payload["championSelectState"]["showQuitButton"] = true
        }

        if (payload["queueId"].asInt().getOrNull() != SOLOQ_ID) {
            val serialized = payload.serialized()
            body["payload"] = if (isCompressed) serialized.gzipBase64().toAmf0String() else serialized.toAmf0String()
            return nodes
        }

        val playersPuuids = payload["championSelectState"]["cells"]["alliedTeam"].asArray().getOrNull()
            ?.filter { it["nameVisibilityType"].asString().getOrNull() == "HIDDEN" }
            ?.mapNotNull { it["puuid"].asString().getOrNull() }
            ?.sorted() ?: listOf()


        val summonersData = getSummonersData(playersPuuids)
        val localCellID = payload["championSelectState"]["localPlayerCellId"].asInt().getOrNull()

        payload["championSelectState"]["cells"]["alliedTeam"].asArray().getOrNull()?.forEach { node ->
            if (node["cellId"].asInt().getOrNull() == localCellID) return@forEach

            val puuid = node["puuid"].asString().getOrNull()
            val summonerData = summonersData?.firstOrNull { it.puuid == puuid }
            if (node["gameName"].isRight()) node["gameName"] = summonerData?.gameName ?: ""
            if (node["tagLine"].isRight()) node["tagLine"] = summonerData?.tagLine ?: ""
            if (node["nameVisibilityType"].isRight()) node["nameVisibilityType"] = "VISIBLE"
        }

        val serialized = payload.serialized()
        body["payload"] = if (isCompressed) serialized.gzipBase64().toAmf0String() else serialized.toAmf0String()

        return nodes
    }
}