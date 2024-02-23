package rtmp.amf3

import okio.BufferedSink
import rtmp.forEachAsync
import rtmp.writeDouble

class Amf3Encoder(private val output: BufferedSink) {
    suspend fun encodeAll(nodes: List<Amf3Node>) = nodes.forEachAsync {
        encode(it)
    }

    private fun encode(node: Amf3Node): Unit = when (node) {
        is Amf3Array -> writeAmf3Array(node)
        is Amf3Double -> writeAmf3Double(node)
        Amf3False -> writeAmf3False()
        Amf3True -> writeAmf3True()
        is Amf3Integer -> writeAmf3Integer(node)
        Amf3Null -> writeAmf3Null()
        is Amf3Object -> writeAmf3Object(node)
        is Amf3String -> writeAmf3String(node)
        Amf3Undefined -> writeAmf3Undefined()
        is Amf3Date -> writeAmf3Date(node)
        else -> throw NotImplementedError("node $node has not encode implemented")
    }

    private fun writeAmf3Date(node: Amf3Date) {
        output.writeByte(Amf3Date.TYPE)
        output.writeDouble(node.value)
    }

    private fun writeAmf3Object(node: Amf3Object) {
        output.writeByte(Amf3Object.TYPE)
        when (val type = node.name) {
            null, "" -> {
                output.write(byteArrayOf(0x0b, 0x01))
                for (key in node.value.keys) {
                    val obj = node.value[key] ?: continue
                    writeAmf3StringNoType(Amf3String(key))
                    encode(obj)
                }
                output.writeByte(0x01)
            }

            "flex.messaging.io.ArrayCollection" -> {
                val array = node.value["array"] ?: return
                output.writeByte(0x07)
                writeAmf3StringNoType(Amf3String(type))
                encode(array)
            }

            else -> {
                writeAmf3IntegerNoType(Amf3Integer(node.value.size shl 4 or 3))
                writeAmf3StringNoType(Amf3String(type))
                val list = mutableListOf<String>()
                for (key in node.value.keys) {
                    writeAmf3StringNoType(Amf3String(key))
                    list.add(key)
                }
                for (key in list) encode(node.value[key] ?: continue)
            }
        }

    }

    private fun writeAmf3String(string: Amf3String) {
        output.writeByte(Amf3String.TYPE)
        writeAmf3StringNoType(string)
    }

    private fun writeAmf3StringNoType(string: Amf3String) {
        val value = string.value
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeAmf3IntegerNoType(Amf3Integer(bytes.size shl 1 or 1))
        output.write(bytes)
    }

    private fun writeAmf3Double(number: Amf3Double) {
        output.writeByte(Amf3Double.TYPE)
        output.writeDouble(number.value)
    }

    private fun writeAmf3Undefined() {
        output.writeByte(Amf3Undefined.TYPE)
    }

    private fun writeAmf3True() {
        output.writeByte(Amf3True.TYPE)
    }

    private fun writeAmf3Null() {
        output.writeByte(Amf3Null.TYPE)
    }

    private fun writeAmf3False() {
        output.writeByte(Amf3False.TYPE)
    }

    private fun writeAmf3Integer(number: Amf3Integer) {
        output.writeByte(Amf3Integer.TYPE)
        writeAmf3IntegerNoType(number)
    }

    private fun writeAmf3IntegerNoType(number: Amf3Integer) {
        val value = number.value
        if (value >= 0x200000 || value < 0) {
            output.write(
                byteArrayOf(
                    (((value shr 22) and 0x7f) or 0x80).toByte(),
                    (((value shr 15) and 0x7f) or 0x80).toByte(),
                    (((value shr 8) and 0x7f) or 0x80).toByte(),
                    (value and 0xff).toByte()
                )
            )
        } else {
            if (value >= 0x4000) {
                output.writeByte(((value shr 14) and 0x7f) or 0x80)
            }
            if (value >= 0x80) {
                output.writeByte(((value shr 7) and 0x7f) or 0x80)
            }
            output.writeByte(value and 0x7f)
        }
    }

    private fun writeAmf3Array(array: Amf3Array) {
        output.writeByte(Amf3Array.TYPE)
        writeAmf3IntegerNoType(Amf3Integer(array.value.size shl 1 or 1))
        output.writeByte(0x01)
        array.value.forEach(::encode)
    }
}