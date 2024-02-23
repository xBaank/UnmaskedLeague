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


val proxyHosts = mutableMapOf<String, LcdsHost>()
val yamlOptions = DumperOptions().apply { defaultFlowStyle = DumperOptions.FlowStyle.BLOCK }
val yaml = Yaml(yamlOptions)

data class LcdsHost(val host: String, val port: Int)

fun main(): Unit = runBlocking {
    runCatching {
        if (isRiotClientRunning()) {
            val wantsToClose = askForClose(
                "Do you want to close it? If you dont close it UnmaskedLeague won't be launched",
                "Riot Client is already running"
            )
            if (wantsToClose) killRiotClient()
            else return@runCatching
        }
        val hosts = getHosts()
        proxies(hosts).forEach { launch(Dispatchers.IO) { it.start() } }
        startClient(hosts)
    }.onFailure {
        when (it) {
            is LeagueNotFoundException -> {
                showError(it.message ?: "", "League of Legends not found")
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

private fun proxies(hosts: Map<String, LcdsHost>) = hosts.map { (region, lcds) ->
    val proxyClient = LeagueProxyClient(lcds.host, lcds.port)
    val port = proxyClient.serverSocket.localAddress.port
    proxyHosts[region] = LcdsHost("127.0.0.1", port)
    println("Created proxy for $region on port $port")
    proxyClient
}

private suspend fun startClient(hosts: Map<String, LcdsHost>) = coroutineScope {
    val (riotClientPath, lolPath) = getLolPaths()
    val systemYamlPath = lolPath.toPath(true).resolve("system.yaml")
    val systemYaml = FileSystem.SYSTEM.source(systemYamlPath).buffer().use { it.readUtf8() }
    val systemYamlMap = yaml.load<Map<String, Any>>(systemYaml)
    val systemYamlMapOriginal = yaml.load<Map<String, Any>>(systemYaml)

    systemYamlMap.getMap("region_data").forEach {
        val region = it.key
        if (!hosts.containsKey(region)) return@forEach
        val lcds = it.value.getMap("servers").getMap("lcds") as MutableMap<String, Any?>
        lcds["lcds_host"] = proxyHosts[region]!!.host
        lcds["lcds_port"] = proxyHosts[region]!!.port
        lcds["use_tls"] = false
    }

    FileSystem.SYSTEM.sink(systemYamlPath).buffer().use { it.writeUtf8(yaml.dump(systemYamlMap)) }

    try {
        process(
            riotClientPath,
            "--launch-product=league_of_legends",
            "--launch-patchline=live",
            "--disable-patching",
            destroyForcibly = true
        )
        cancel("League closed")
    } finally {
        //Leave as it was originally
        FileSystem.SYSTEM.sink(systemYamlPath).buffer().use { it.writeUtf8(yaml.dump(systemYamlMapOriginal)) }
    }
}

private fun getLolPaths(): Pair<String, String> {
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
    return Pair(riotClientPath, lolPath)
}

fun getHosts(): Map<String, LcdsHost> {
    val (_, lolPath) = getLolPaths()
    val systemYamlPath = lolPath.toPath(true).resolve("system.yaml")
    val systemYaml = FileSystem.SYSTEM.source(systemYamlPath)
        .buffer()

    val systemYamlMap = systemYaml.use { yaml.load<Map<String, Any>>(systemYaml.readUtf8()) }
    val hosts = mutableMapOf<String, LcdsHost>()
    systemYamlMap.getMap("region_data").forEach {
        val region = it.key
        val lcds = it.value.getMap("servers").getMap("lcds") as MutableMap<String, Any?>
        val host = lcds["lcds_host"] as String
        val port = lcds["lcds_port"] as Int
        hosts[region] = LcdsHost(host, port)
    }
    return hosts
}

private fun showError(msg: String, title: String) =
    JOptionPane.showMessageDialog(null, msg, title, JOptionPane.ERROR_MESSAGE)

private fun askForClose(msg: String, title: String) =
    JOptionPane.showConfirmDialog(
        null,
        msg,
        title,
        JOptionPane.YES_NO_OPTION,
        JOptionPane.ERROR_MESSAGE
    ) == JOptionPane.YES_OPTION

private fun Any?.getMap(s: String) = (this as Map<String, Any?>)[s] as Map<String, Any?>
private val SocketAddress.port: Int
    get() = when (this) {
        is InetSocketAddress -> port
        else -> throw IllegalStateException("SocketAddress is not an InetSocketAddress")
    }