package unmaskedLeague

import arrow.continuations.SuspendApp
import arrow.core.getOrElse
import com.github.pgreze.process.process
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import simpleJson.asString
import simpleJson.deserialize
import simpleJson.get


val hosts = mapOf(
    "BR" to LcdsHost("feapp.br1.lol.pvp.net", 2099),
    "EUNE" to LcdsHost("prod.eun1.lol.riotgames.com", 2099),
    "EUW" to LcdsHost("feapp.euw1.lol.pvp.net", 2099),
    "JP" to LcdsHost("feapp.jp1.lol.pvp.net", 2099),
    "LA1" to LcdsHost("feapp.la1.lol.pvp.net", 2099),
    "LA2" to LcdsHost("feapp.la2.lol.pvp.net", 2099),
    "NA" to LcdsHost("feapp.na1.lol.pvp.net", 2099),
    "OC1" to LcdsHost("feapp.oc1.lol.pvp.net", 2099),
    "RU" to LcdsHost("prod.ru.lol.riotgames.com", 2099),
)

val proxyHosts = mutableMapOf<String,LcdsHost>()

val yamlOptions = DumperOptions().apply {
    defaultFlowStyle = DumperOptions.FlowStyle.BLOCK // Optional
}
val yaml = Yaml(yamlOptions)


data class LcdsHost(val host: String, val port: Int)

val mutex = Mutex()
fun main(): Unit = SuspendApp {
    hosts.forEach { (region, lcds) ->
        launch(Dispatchers.IO) {
            val proxyClient = mutex.withLock {
                val proxyClient = LeagueProxyClient(lcds.host, lcds.port)
                val port = proxyClient.serverSocket.localAddress.port
                proxyHosts[region] = LcdsHost("127.0.0.1",port)
                println("Starting $region proxy on $port")
                proxyClient
            }
            proxyClient.start()
        }
    }
    val lolClientInstalls : Path = System.getenv("ALLUSERSPROFILE")
        ?.let { "$it/Riot Games/Metadata/league_of_legends.live/league_of_legends.live.product_settings.yaml" }
        ?.toPath(true)
        ?.takeIf { FileSystem.SYSTEM.exists(it)}
        ?: return@SuspendApp cancel("Cannot find ALLUSERSPROFILE")

    val riotClientInstalls = System.getenv("ALLUSERSPROFILE")
    ?.let { "$it/Riot Games/RiotClientInstalls.json" }
    ?.toPath(true)
    ?.takeIf { FileSystem.SYSTEM.exists(it)}
    ?: return@SuspendApp cancel("Cannot find ALLUSERSPROFILE")

    val riotClientInstallsJson = FileSystem.SYSTEM.source(riotClientInstalls).buffer().readUtf8().deserialize()
    val riotClientPath = riotClientInstallsJson["rc_live"].asString().getOrElse { return@SuspendApp cancel("Cannot find rc_live") }

    val file = FileSystem.SYSTEM.source(lolClientInstalls).buffer()

    val yamlMap = yaml.load<Map<String, Any>>(file.readUtf8())
    val lolPath : String = yamlMap["product_install_full_path"] as String
    val systemYamlPath = lolPath.toPath(true).resolve("system.yaml")

    val systemYaml = FileSystem.SYSTEM.source(systemYamlPath)
        .buffer()

    val systemYamlMap = systemYaml.use { yaml.load<Map<String, Any>>(systemYaml.readUtf8()) }

    systemYamlMap.getMap("region_data").forEach {
        val region = it.key
        if (!hosts.containsKey(region)) return@forEach
        val lcds = it.value.getMap("servers").getMap("lcds") as MutableMap<String, Any?>
        lcds["lcds_host"] = proxyHosts[region]!!.host
        lcds["lcds_port"] = proxyHosts[region]!!.port
        lcds["use_tls"] = false
    }

    FileSystem.SYSTEM.sink(systemYamlPath).buffer().use { it.writeUtf8(yaml.dump(systemYamlMap)) }

    process(riotClientPath, "--launch-product=league_of_legends", "--launch-patchline=live", "--allow-multiple-clients")
    cancel("League closed")
}

private fun Any?.getMap(s: String) = (this as Map<String, Any?>)[s] as Map<String, Any?>
private val SocketAddress.port: Int
    get() = when (this) {
        is InetSocketAddress -> port
        else -> throw IllegalStateException("SocketAddress is not an InetSocketAddress")
    }

