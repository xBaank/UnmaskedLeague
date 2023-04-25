package unmaskedLeague

import arrow.core.getOrElse
import com.github.pgreze.process.process
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import simpleJson.asString
import simpleJson.deserialized
import simpleJson.get
import javax.swing.JOptionPane
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess


val hosts = mapOf(
    "BR" to LcdsHost("feapp.br1.lol.pvp.net", 2099),
    "EUNE" to LcdsHost("prod.eun1.lol.riotgames.com", 2099),
    "EUW" to LcdsHost("feapp.euw1.lol.pvp.net", 2099),
    "JP" to LcdsHost("feapp.jp1.lol.pvp.net", 2099),
    "LA1" to LcdsHost("feapp.la1.lol.pvp.net", 2099),
    "LA2" to LcdsHost("feapp.la2.lol.pvp.net", 2099),
    "NA" to LcdsHost("feapp.na1.lol.pvp.net", 2099),
    "OC1" to LcdsHost("feapp.oc1.lol.pvp.net", 2099),
    "TR" to LcdsHost("prod.tr.lol.riotgames.com", 2099),
)

val proxyHosts = mutableMapOf<String, LcdsHost>()

val yamlOptions = DumperOptions().apply {
    defaultFlowStyle = DumperOptions.FlowStyle.BLOCK // Optional
}
val yaml = Yaml(yamlOptions)


data class LcdsHost(val host: String, val port: Int)

fun main(): Unit = runBlocking {
    runCatching {
        proxies().forEach { launch(Dispatchers.IO) { it.start() } }
        startClient()
    }.onFailure {
        when (it) {
            is LeagueNotFoundException -> {
                showLeagueNotFound(it.message ?: "")
                return@onFailure
            }

            is CancellationException -> {
                return@onFailure
            }

            else -> {
                showError(it.stackTraceToString(), it.message ?: "An error happened")
                it.printStack()
            }
        }
    }
    exitProcess(0)
}

private fun proxies() = hosts.map { (region, lcds) ->
    val proxyClient = LeagueProxyClient(lcds.host, lcds.port)
    val port = proxyClient.serverSocket.localAddress.port
    proxyHosts[region] = LcdsHost("127.0.0.1", port)
    println("Created proxy for $region on port $port")
    proxyClient
}

private suspend fun startClient() = coroutineScope {
    val lolClientInstalls: Path = System.getenv("ALLUSERSPROFILE")
        ?.let { "$it/Riot Games/Metadata/league_of_legends.live/league_of_legends.live.product_settings.yaml" }
        ?.toPath(true)
        ?.takeIf { FileSystem.SYSTEM.exists(it) }
        ?: throw LeagueNotFoundException("Cannot find Lol Client Installs (ALLUSERSPROFILE)")

    val riotClientInstalls = System.getenv("ALLUSERSPROFILE")
        ?.let { "$it/Riot Games/RiotClientInstalls.json" }
        ?.toPath(true)
        ?.takeIf { FileSystem.SYSTEM.exists(it) }
        ?: throw LeagueNotFoundException("Cannot find Riot Client Installs (ALLUSERSPROFILE)")

    val riotClientInstallsJson = FileSystem.SYSTEM.source(riotClientInstalls).buffer().readUtf8().deserialized()
    val riotClientPath = riotClientInstallsJson["rc_live"].asString()
        .getOrElse { throw LeagueNotFoundException("Cannot find property rc_live") }

    val file = FileSystem.SYSTEM.source(lolClientInstalls).buffer()

    val yamlMap = yaml.load<Map<String, Any>>(file.readUtf8())
    val lolPath: String = yamlMap["product_install_full_path"] as String
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

private fun showLeagueNotFound(msg: String) =
    JOptionPane.showMessageDialog(null, msg, "League Not Found", JOptionPane.ERROR_MESSAGE);

private fun showError(msg: String, error: String = "An error happened") =
    JOptionPane.showMessageDialog(null, msg, error, JOptionPane.ERROR_MESSAGE);

private fun Any?.getMap(s: String) = (this as Map<String, Any?>)[s] as Map<String, Any?>
private val SocketAddress.port: Int
    get() = when (this) {
        is InetSocketAddress -> port
        else -> throw IllegalStateException("SocketAddress is not an InetSocketAddress")
    }

