package unmaskedLeague

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import simpleJson.deserialized
import simpleJson.serialized
import io.ktor.client.engine.cio.CIO as ClientCIO

//TODO Use this url to download the latest yaml with manifest downloader and add --system-yaml-override
//https://clientconfig.rpg.riotgames.com/api/v1/config/public?os=windows&region={REGION_HERE}&app=league_of_legends&version=1&patchline=live

class ConfigProxy(val region: String, val locale: String) {
    val url = "https://clientconfig.rpg.riotgames.com"
    fun Headers.isJson() = this[HttpHeaders.ContentType]?.contains("application/json") == true
    suspend fun start(): Int {
        val client = HttpClient(ClientCIO)
        val server = embeddedServer(CIO, port = 0) {
            routing {
                route("{...}") {
                    handle {
                        val url = "$url${call.request.uri}"

                        try {
                            val body = when {
                                call.request.headers.isJson() -> {
                                    val text = call.receiveText().replace("\\u0001", "")
                                    val json = text.deserialized().getOrNull()
                                    json?.serialized()?.toByteArray() ?: text.toByteArray()
                                }

                                else -> {
                                    call.receiveChannel().toByteArray()
                                }
                            }

                            val response = client.request(url) {
                                method = call.request.httpMethod
                                val reqHeaders = call.request.headers.toMap().toMutableMap()
                                reqHeaders["Host"] = listOf("clientconfig.rpg.riotgames.com")
                                headers.appendAll(StringValues.build {
                                    for (entry in reqHeaders) {
                                        appendAll(entry.key, entry.value)
                                    }
                                })
                                setBody(body)
                            }

                            response.headers.forEach { s, strings ->
                                strings.forEach {
                                    call.response.headers.append(s, it, safeOnly = false)

                                }
                            }

                            call.respondBytes(
                                status = response.status
                            ) { response.bodyAsBytes() }

                        } catch (ex: Throwable) {
                            logger.error(ex) { "failed with url: $url" }
                            throw ex
                        }
                    }
                }
            }
        }.start(false)
        return server.engine.resolvedConnectors().first().port
    }
}