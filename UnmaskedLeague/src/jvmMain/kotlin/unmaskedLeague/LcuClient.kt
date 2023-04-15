package unmaskedLeague

import arrow.core.getOrElse
import io.ktor.util.*
import okhttp3.OkHttpClient
import okhttp3.Request
import simpleJson.asString
import simpleJson.deserialized
import simpleJson.get

val httpClient = OkHttpClient.Builder().build()


fun getSummonerNameById(summonerId: Long, leagueAuth: LeagueAuth): String {
    val url = "https://localhost:${leagueAuth.port}/lol-summoner/v1/summoners/$summonerId"
    val request = Request.Builder()
        .header("Authorization", basicAuthHeader(leagueAuth.username, leagueAuth.password))
        .get()
        .url(url)
        .build()
    val response = httpClient.newCall(request).execute()
    return response.body!!.source().deserialized()["displayName"].asString().getOrElse { throw it }
}

private fun basicAuthHeader(user: String, pass: String): String {
    val info = "$user:$pass"
    val encoded = info.encodeBase64()
    return "Basic $encoded"
}