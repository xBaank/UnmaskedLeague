package unmaskedLeague

import arrow.core.getOrElse
import arrow.core.raise.either
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
import simpleJson.*

class ConfigProxy {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    var port = -1
    val url = "https://clientconfig.rpg.riotgames.com"

    fun Headers.isJson() = this[HttpHeaders.ContentType]?.contains("application/json") == true

    fun spoofArguments(json: JsonNode): JsonNode {
        either {
            json.asObject().bind().keys.forEach { key ->
                if ("keystone.products.league_of_legends.patchlines." in key) {
                    val platforms = json[key]["platforms"].asObject().bind()
                    platforms.values.forEach {
                        val configurations = it["configurations"]
                        configurations.asArray().bind().forEach { config ->
                            val args = config["launcher"]["arguments"].asArray().bind().value
                            args += "--system-yaml-override=Config/system.yaml".asJson()
                        }
                    }
                }
            }


        }.getOrElse { logger.error { it } }
        return json
    }

    fun stop() = server?.stop(1000, 5000)

    suspend fun start() {
        server = embeddedServer(CIO, port = 0) {
            routing {
                route("{...}") {
                    handle {
                        val url = "$url${call.request.uri}"

                        try {

                            val response = client.request(url) {
                                method = call.request.httpMethod
                                val reqHeaders =
                                    call.request.headers.filter { key, value -> !key.equals("Host", true) }
                                headers.appendAll(reqHeaders)
                                setBody(call.receiveChannel().toByteArray())
                            }

                            val responseBody = when {
                                response.headers.isJson() -> {
                                    val text = response.bodyAsText()
                                    val json = text.deserialized().getOrNull()
                                    val spoofedJson = json?.let { spoofArguments(it) }
                                    spoofedJson?.serialized()?.toByteArray() ?: text.toByteArray()
                                }

                                else -> response.bodyAsBytes()
                            }

                            response.headers.forEach { s, strings ->
                                strings.forEach {
                                    call.response.headers.append(s, it, safeOnly = false)

                                }
                            }

                            call.respondBytes(
                                status = response.status
                            ) { responseBody }

                        } catch (ex: Throwable) {
                            logger.error(ex) { "failed with url: $url" }
                            throw ex
                        }
                    }
                }
            }
        }.start(false)
        port = server!!.engine.resolvedConnectors().first().port
    }
}