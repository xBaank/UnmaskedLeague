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
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Request
import simpleJson.*
import unmaskedLeague.*
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.properties.Delegates

const val configUrl = "https://clientconfig.rpg.riotgames.com"

object ChatProxy {
    private val messages = MutableSharedFlow<String>()
    var port by Delegates.notNull<Int>()
    var ready = Job()
    lateinit var leagueAuth: LeagueAuth

    suspend fun sendPlayersOPGGMessage(playersIds: List<Long>) {
        val message = createOPGGMultisearchMessage(playersIds)
        messages.emit(message)
    }

    private suspend fun createOPGGMultisearchMessage(playersIds: List<Long>): String = coroutineScope {
        val names = playersIds.map { async(Dispatchers.IO) { getSummonerNameById(it, leagueAuth) } }.awaitAll()
        val stamp = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

        val bodyMessage =
            """https://www.op.gg/multisearch/${leagueAuth.region}?summoners=${
                URLEncoder.encode(
                    names.joinToString(","),
                    "UTF-8"
                )
            }"""

        val message = """
        <message from='c1~095b806ba8db6de50e185871a84874afb6378ea0@champ-select.eu1.pvp.net/d5029d9d-745e-507b-973c-3382a6d23985' stamp='$stamp' id='fake-$stamp' type='groupchat'>
          <body>${bodyMessage}</body>
        </message>
                  """

        message
    }

    suspend fun start() = coroutineScope {

        val serverSocket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind("localhost")

        val clientConfigServer = embeddedServer(Netty) {
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

        port = clientConfigServer.environment.connectors.first().port
        ready.complete()

        launch(Dispatchers.IO) {
            while (isActive) {
                println("Waiting for client...")
                val client = serverSocket.accept()
                leagueAuth = leagueAuth()
                val socket =
                    aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
                        .connect("${leagueAuth.region}.chat.si.riotgames.com", 5223)
                        .tls(Dispatchers.IO)
                println("Client connected")
                //Server to client
                val serverReadChannel = client.openReadChannel()
                val serverWriteChannel = client.openWriteChannel(autoFlush = true)

                val clientReadChannel = socket.openReadChannel()
                val clientWriteChannel = socket.openWriteChannel(autoFlush = true)

                launch {
                    messages.collect { message ->
                        clientWriteChannel.writeStringUtf8(message)
                    }
                }

                launch {
                    val buffer = ByteArray(8192)
                    while (isActive) {
                        val read = clientReadChannel.readAvailable(buffer)
                        if (read == -1) break
                        serverWriteChannel.writeFully(buffer, 0, read)
                    }
                }

                launch {
                    val buffer = ByteArray(8192)
                    while (isActive) {
                        val read = serverReadChannel.readAvailable(buffer)
                        if (read == -1) break
                        clientWriteChannel.writeFully(buffer, 0, read)
                    }
                }
            }
        }
    }
}