package unmaskedLeague

import arrow.core.getOrElse
import io.ktor.util.*
import okhttp3.OkHttpClient
import okhttp3.Request
import simpleJson.asString
import simpleJson.deserialized
import simpleJson.get
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager


val trustAllCerts = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return arrayOf()
    }
}

fun trustAllSsl(): SSLSocketFactory {
    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, arrayOf(trustAllCerts), SecureRandom())
    return sslContext.socketFactory
}

val httpClient = OkHttpClient.Builder()
    .sslSocketFactory(trustAllSsl(), trustAllCerts)
    .hostnameVerifier { _, _ -> true }
    .build()


fun getSummonerNameById(summonerId: Long, leagueAuth: LeagueAuth): String? {
    val url = "https://127.0.0.1:${leagueAuth.port}/lol-summoner/v1/summoners/$summonerId"
    val request = Request.Builder()
        .header("Authorization", basicAuthHeader(leagueAuth.username, leagueAuth.password))
        .get()
        .url(url)
        .build()
    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) return null
    val json = response.body!!.string().deserialized()
    return json["internalName"].asString().getOrNull() ?: json["displayName"].asString().getOrElse { null }
}

private fun basicAuthHeader(user: String, pass: String): String {
    val info = "$user:$pass"
    val encoded = info.encodeBase64()
    return "Basic $encoded"
}