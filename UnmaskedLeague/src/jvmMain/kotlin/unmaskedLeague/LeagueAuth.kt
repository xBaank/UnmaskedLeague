package unmaskedLeague

import com.github.pgreze.process.Redirect
import com.github.pgreze.process.process

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
    return LeagueAuth(
        region = regionRegex.find(output)?.groupValues?.get(1)
            ?: throw LeagueNotFoundException("Could not find LeagueClientUx.exe"),
        port = portRegex.find(output)?.groupValues?.get(1)?.toInt()
            ?: throw LeagueNotFoundException("Could not find LeagueClientUx.exe"),
        password = passwordRegex.find(output)?.groupValues?.get(1)
            ?: throw LeagueNotFoundException("Could not find LeagueClientUx.exe"),
        username = "riot"
    )
}