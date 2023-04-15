package unmaskedLeague

import io.ktor.network.sockets.*

val SocketAddress.port: Int
    get() = when (this) {
        is InetSocketAddress -> port
        else -> throw IllegalStateException("SocketAddress is not an InetSocketAddress")
    }

fun Any?.getMap(s: String) = (this as Map<String, Any?>)[s] as Map<String, Any?>
