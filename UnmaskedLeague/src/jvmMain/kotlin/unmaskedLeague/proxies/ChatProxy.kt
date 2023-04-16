package unmaskedLeague.proxies

import arrow.core.getOrElse
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Request
import org.apache.commons.text.StringEscapeUtils
import simpleJson.*
import unmaskedLeague.*
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlin.properties.Delegates

const val configUrl = "https://clientconfig.rpg.riotgames.com"


private val chatUrl = mapOf(
    "BR" to Host("br1.chat.si.riotgames.com", 5223),
    "EUNE" to Host("eune1.chat.si.riotgames.com", 5223),
    "EUW" to Host("euw1.chat.si.riotgames.com", 5223),
    "JP" to Host("jp1.chat.si.riotgames.com", 5223),
    "LA1" to Host("la1.chat.si.riotgames.com", 5223),
    "LA2" to Host("la2.chat.si.riotgames.com", 5223),
    "NA" to Host("na1.chat.si.riotgames.com", 5223),
    "OC1" to Host("oc1.chat.si.riotgames.com", 5223),
    "TR" to Host("tr1.chat.si.riotgames.com", 5223),
)

object ChatProxy {
    private val messages = MutableSharedFlow<String>()
    var port by Delegates.notNull<Int>()
    var ready = Job()
    lateinit var leagueAuth: LeagueAuth

    suspend fun sendPlayersOPGGMessage(playersIds: List<Long>) {
        val message = createOPGGMultisearchMessage(playersIds)
        messages.emit(message)
    }

    private fun xmppMessage(message: String): String {
        val stamp = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

        return """<message from='41c322a1-b328-495b-a004-5ccd3e45eae8@eu1.pvp.net/UnmaskedLeague' stamp='$stamp' id='fake-$stamp' type='chat'>
                  <body>${StringEscapeUtils.escapeXml11(message)}</body>
                </message>"""
    }

    private suspend fun createOPGGMultisearchMessage(playersIds: List<Long>): String = coroutineScope {
        val names = playersIds.map { async(Dispatchers.IO) { getSummonerNameById(it, leagueAuth) } }.awaitAll()



        if (names.any { it == null }) {
            return@coroutineScope xmppMessage("Error getting summoner names")
        }


        val bodyMessage =
            """https://www.op.gg/multisearch/${leagueAuth.region}?summoners=${
                URLEncoder.encode(
                    names.joinToString(","),
                    Charsets.UTF_8
                )
            }"""

        xmppMessage(bodyMessage)
    }

    private suspend fun startChatProxy(serverSocket: ServerSocket) = coroutineScope {
        while (isActive) {
            val clientSocket = serverSocket.accept()
            println("Accepted connection on chat proxy from ${clientSocket.remoteAddress}")

            leagueAuth = leagueAuth()

            val chatData =
                chatUrl[leagueAuth.region] ?: throw LeagueNotFoundException("Region ${leagueAuth.region} not found")
            val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
                .connect(chatData.host, chatData.port)
                .tls(Dispatchers.IO)

            launch(Dispatchers.IO) {
                handle(clientSocket = clientSocket, socket = socket)
            }
        }
    }

    private suspend fun handle(clientSocket: Socket, socket: Socket) {
        runCatching {
            handleSockets(clientSocket, socket)
        }.onFailure {
            when (it) {
                is ClosedReceiveChannelException -> return
                is CancellationException -> return
                else -> throw it
            }
        }
    }

    private suspend fun handleSockets(clientSocket: Socket, socket: Socket) = coroutineScope {
        launch(Dispatchers.IO) {
            //Server to client
            val serverReadChannel = clientSocket.openReadChannel()
            val serverWriteChannel = clientSocket.openWriteChannel(autoFlush = true)

            val clientReadChannel = socket.openReadChannel()
            val clientWriteChannel = socket.openWriteChannel(autoFlush = true)

            val messagesJob = launch {
                messages.collect { message ->
                    if (!clientSocket.isClosed) serverWriteChannel.writeFully(message.toByteArray())
                }
            }

            launch {
                val buffer = ByteArray(8192)
                while (isActive) {
                    val read = clientReadChannel.readAvailable(buffer)
                    if (read == -1) {
                        clientSocket.close()
                        socket.close()
                        messagesJob.cancel()
                        break
                    }
                    serverWriteChannel.writeFully(buffer, 0, read)
                }
            }

            launch {
                val buffer = ByteArray(8192)
                while (isActive) {
                    val read = serverReadChannel.readAvailable(buffer)
                    if (read == -1) {
                        clientSocket.close()
                        socket.close()
                        messagesJob.cancel()
                        break
                    }
                    clientWriteChannel.writeFully(buffer, 0, read)
                }
            }
        }
    }


    suspend fun start() = coroutineScope {
        val serverSocket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind()
        val clientConfigServer = clientConfigServer(serverSocket)

        launch(Dispatchers.IO) { startChatProxy(serverSocket) }

        port = clientConfigServer.environment.connectors.first().port
        ready.complete()
    }

    private fun clientConfigServer(serverSocket: ServerSocket): NettyApplicationEngine = embeddedServer(Netty) {
        routing {
            get("{...}") {
                val url = "$configUrl${call.request.uri}"
                val userAgent = call.request.userAgent()
                val auth = call.request.headers["Authorization"]
                val jwt = call.request.headers["X-Riot-Entitlements-JWT"]

                val response = httpClient.newCall(
                    Request.Builder().url(url)
                        .apply { if (userAgent != null) header("User-Agent", userAgent) }
                        .apply { if (auth != null) header("Authorization", auth) }
                        .apply { if (jwt != null) header("X-Riot-Entitlements-JWT", jwt) }
                        .build()
                ).execute()
                val responseBytes = response.body!!.string()
                val json = responseBytes.deserialized().getOrElse { throw it }

                if (json["chat.port"].isRight()) {
                    json["chat.port"] = serverSocket.localAddress.port
                }

                if (json["chat.host"].isRight()) {
                    json["chat.host"] = "127.0.0.1"
                }

                if (json["chat.use_tls.enabled"].isRight()) {
                    json["chat.use_tls.enabled"] = false
                }

                json["chat.affinities"].asObject().getOrNull()?.forEach { key, _ ->
                    json["chat.affinities"][key] = "127.0.0.1"
                }

                call.respondText(
                    json.serialized(),
                    ContentType.Application.Json,
                    HttpStatusCode.fromValue(response.code)
                )
            }
        }
    }.start(wait = false)
}