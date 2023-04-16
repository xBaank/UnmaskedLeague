package unmaskedLeague

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

data class LeagueAuth(
    val port: Int,
    val password: String,
    val username: String,
    val region: String
)

val portRegex = Regex("--app-port=(\\d+)")
val passwordRegex = Regex("--remoting-auth-token=(\\w+)")
val regionRegex = Regex("--region=(\\w+)")

suspend fun leagueAuth(): LeagueAuth {
    var retries = 0
    while (retries < 10) {
        val execute = process(
            "wmic",
            "process",
            "where",
            "name='LeagueClientUx.exe'",
            "get",
            "commandline",
            stderr = Redirect.CAPTURE,
            stdout = Redirect.CAPTURE
        )
        val output = execute.output.joinToString(separator = " ")
        val region = regionRegex.find(output)?.groupValues?.get(1)
        val port = portRegex.find(output)?.groupValues?.get(1)?.toInt()
        val password = passwordRegex.find(output)?.groupValues?.get(1)

        if (region == null || port == null || password == null) {
            retries++
            delay(1.seconds)
            continue
        }

        return LeagueAuth(
            port = port,
            password = password,
            region = region,
            username = "riot"
        )
    }
    throw LeagueNotFoundException("LeagueUx.exe not found")
}