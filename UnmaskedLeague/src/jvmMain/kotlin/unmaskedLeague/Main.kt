package unmaskedLeague

import arrow.continuations.SuspendApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val hosts = mapOf(
    "BR" to LcdsHost("feapp.br1.lol.pvp.net", 2099),
    "EUNE" to LcdsHost("prod.eun1.lol.riotgames.com", 2099),
    "EUW" to LcdsHost("feapp.euw1.lol.pvp.net", 2099),
    "JP" to LcdsHost("feapp.jp1.lol.pvp.net", 2099),
    "LA1" to LcdsHost("feapp.la1.lol.pvp.net", 2099),
    "LA2" to LcdsHost("feapp.la2.lol.pvp.net", 2099),
    "NA" to LcdsHost("feapp.na1.lol.pvp.net", 2099),
    "OC1" to LcdsHost("feapp.oc1.lol.pvp.net", 2099),
    "OC1" to LcdsHost("feapp.oc1.lol.pvp.net", 2099),
    "RU" to LcdsHost("prod.ru.lol.riotgames.com", 2099),
)

data class LcdsHost(val host: String, val port: Int)

val mutex = Mutex()
fun main(): Unit = SuspendApp {
    var port = 21340
    hosts.forEach { (region, lcds) ->
        launch(Dispatchers.IO) {
            val proxyClient = mutex.withLock {
                println("Starting $region proxy on $port")
                val proxyClient = LeagueProxyClient(port, lcds.host, lcds.port)
                port += 1
                proxyClient
            }
            proxyClient.start()
        }
    }
}