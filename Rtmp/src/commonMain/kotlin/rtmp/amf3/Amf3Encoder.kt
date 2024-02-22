package rtmp.amf3

import okio.BufferedSink
import rtmp.forEachAsync

class Amf3Encoder(private val output: BufferedSink) {
    suspend fun writeAll(nodes: List<Amf3Node>) = nodes.forEachAsync {
        write(it)
    }

    fun write(node: Amf3Node): Nothing = when (node) {
        is Amf3Array -> TODO()
        is Amf3ByteArray -> TODO()
        is Amf3Date -> TODO()
        Amf3Dictionary -> TODO()
        is Amf3Double -> TODO()
        Amf3False -> TODO()
        is Amf3Integer -> TODO()
        Amf3Null -> TODO()
        is Amf3Object -> TODO()
        is Amf3String -> TODO()
        Amf3True -> TODO()
        Amf3Undefined -> TODO()
        is Amf3VectorDouble -> TODO()
        is Amf3VectorInt -> TODO()
        is Amf3VectorObject -> TODO()
        is Amf3VectorUint -> TODO()
        is Amf3XMLDocument -> TODO()
    }
}