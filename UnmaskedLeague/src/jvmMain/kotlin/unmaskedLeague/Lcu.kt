package unmaskedLeague

import arrow.core.getOrElse
import com.mayakapps.kache.InMemoryKache
import com.mayakapps.kache.KacheStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import ru.gildor.coroutines.okhttp.await
import simpleJson.*
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.minutes

data class LcuData(val protocol: String, val port: Int, val auth: String)
data class SummonerData(val puuid: String, val gameName: String, val tagLine: String)

val cache = InMemoryKache<List<String>, List<SummonerData>>(maxSize = 1 * 1024 * 1024) {
    strategy = KacheStrategy.LRU
    expireAfterAccessDuration = 5.minutes
}


suspend fun getSummonersData(puuids: List<String>) = cache.getOrPut(puuids) {
    logger.info { "Getting summoner data for $puuids" }
    val (protocol, port, auth) = getLcuData()

    val body = puuids.map { it.asJson() }.asJson().serialized()
    val mediaType = "application/json".toMediaType()

    val request = Request.Builder()
        .url("$protocol://127.0.0.1:$port/lol-summoner/v2/summoners/puuid")
        .header("Authorization", "Basic $auth")
        .post(body.toRequestBody(mediaType))
        .build()

    val response = lcuClient.newCall(request).await()

    val textBody = response.body.source().readString(Charsets.UTF_8)

    if (!response.isSuccessful) logger.error { "LCU responded with ${response.code} $textBody" }

    val json = textBody.deserialized()
    json.asArray().getOrNull()?.map {
        val gameName = it["gameName"].asString().getOrElse { "" }
        val tagLine = it["tagLine"].asString().getOrElse { "" }
        val puuid = it["puuid"].asString().getOrElse { "" }
        SummonerData(puuid, gameName, tagLine)
    } ?: listOf()
}


private fun getLcuData(): LcuData {
    val lockFile = lolPaths.lolClientPath.toPath(true) / "lockfile"
    val data = FileSystem.SYSTEM.source(lockFile).buffer().readString(Charsets.UTF_8)
    val (_, _, port, password, protocol) = data.split(":")
    return LcuData(protocol, port.toInt(), "riot:$password".base64())
}

fun unsafeOkHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
    })

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
    val sslSocketFactory = sslContext.socketFactory

    return OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
}