package rtmp.amf3

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import okio.BufferedSource
import rtmp.readDouble
import java.io.IOException

class AMF3Decoder(private val input: BufferedSource) {

    suspend fun decodeAll(): List<Amf3Node> = coroutineScope {
        val result = mutableListOf<Amf3Node>()
        while (isActive) {
            val node = decode()
            result.add(node)
            if (input.exhausted()) break
        }
        result
    }

    fun decode(): Amf3Node {
        val type = input.readByte()
        return when (type.toInt()) {
            Amf3Undefined.TYPE -> Amf3Undefined
            Amf3Null.TYPE -> Amf3Null
            Amf3False.TYPE -> Amf3False
            Amf3True.TYPE -> Amf3True
            Amf3Integer.TYPE -> Amf3Integer(input.readInt())
            Amf3Double.TYPE -> Amf3Double(input.readDouble())
            Amf3String.TYPE -> TODO()
            Amf3XMLDocument.TYPE -> TODO()
            Amf3Date.TYPE -> TODO()
            Amf3Array.TYPE -> TODO()
            Amf3Object.TYPE -> TODO()
            else -> throw IOException("Unsupported AMF3 type: $type")
        }
    }
}
