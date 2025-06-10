package unmaskedLeague

import arrow.core.getOrElse
import arrow.core.raise.either
import io.ktor.client.*
import io.ktor.client.plugins.compression.*
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
import io.ktor.client.engine.cio.CIO as ClientCIO

//TODO Use this url to download the latest yaml with manifest downloader and add --system-yaml-override
//https://clientconfig.rpg.riotgames.com/api/v1/config/public?os=windows&region={REGION_HERE}&app=league_of_legends&version=1&patchline=live

class ConfigProxy() {
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

    suspend fun start(): Int {
        val client = HttpClient(ClientCIO) {
            install(ContentEncoding) { gzip() }
        }
        val server = embeddedServer(CIO, port = 0) {
            routing {
                route("{...}") {
                    handle {
                        val url = "$url${call.request.uri}"

                        try {

                            val response = client.request(url) {
                                method = call.request.httpMethod
                                val reqHeaders = call.request.headers.filter { key, value -> !key.equals("Host", true) }
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

        return server.engine.resolvedConnectors().first().port
    }
}