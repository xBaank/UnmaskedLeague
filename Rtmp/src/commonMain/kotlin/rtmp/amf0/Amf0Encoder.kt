package rtmp.amf0

import io.ktor.utils.io.core.*
import okio.BufferedSink
import rtmp.forEachAsync
import rtmp.writeDouble

class Amf0Encoder(private val output: BufferedSink) {

    fun writeNumber(number: Amf0Number) {
        output.writeByte(Amf0Number.TYPE)
        output.writeDouble(number.value)
    }

    fun writeBoolean(boolean: Amf0Boolean) {
        output.writeByte(Amf0Boolean.TYPE)
        output.writeByte(if (boolean.value) 0x01 else 0x00)
    }

    fun writeString(string: Amf0String) {
        output.writeByte(Amf0String.TYPE)
        val stringArray = string.value.toByteArray()
        output.writeShort(stringArray.size)
        output.write(stringArray)
    }

    fun writeStringKey(string: Amf0String) {
        val stringArray = string.value.toByteArray()
        output.writeShort(stringArray.size)
        output.write(stringArray)
    }

    fun writeObject(obj: Amf0Object) {
        output.writeByte(Amf0Object.TYPE)

        for (entry in obj.value) {
            writeStringKey(entry.key.toAmf0String())
            write(entry.value)
        }

        output.writeByte(0x00)
        output.writeByte(0x00)
        output.writeByte(0x09)
    }

    fun writeTypedObject(obj: Amf0TypedObject) {
        output.writeByte(Amf0TypedObject.TYPE)

        writeStringKey(obj.name.toAmf0String())
        for (entry in obj.value) {
            if (entry.key == "configs")
                println()
            writeStringKey(entry.key.toAmf0String())
            write(entry.value)
        }
        output.writeByte(0x00)
        output.writeByte(0x00)
        output.writeByte(0x09)
    }

    fun writeNull() {
        output.writeByte(Amf0Null.TYPE)
    }

    fun writeUndefined() {
        output.writeByte(Amf0Undefined.TYPE)
    }

    fun writeECMAArray(array: Amf0ECMAArray) {
        output.writeByte(Amf0ECMAArray.TYPE)
        output.writeInt(array.value.size)
        for (entry in array.value) {
            writeStringKey(entry.key.toAmf0String())
            write(entry.value)
        }
        output.writeByte(0x00)
        output.writeByte(0x00)
        output.writeByte(0x09)
    }

    fun writeStrictArray(array: Amf0StrictArray) {
        output.writeByte(Amf0StrictArray.TYPE)
        output.writeInt(array.value.size)
        for (entry in array.value) {
            write(entry)
        }
    }


    fun write(node: Amf0Node) {
        when (node) {
            is Amf0Boolean -> writeBoolean(node)
            is Amf0ECMAArray -> writeECMAArray(node)
            Amf0Null -> writeNull()
            is Amf0Number -> writeNumber(node)
            is Amf0Object -> writeObject(node)
            is Amf0String -> writeString(node)
            is Amf0StrictArray -> writeStrictArray(node)
            is Amf0TypedObject -> writeTypedObject(node)
            Amf0Undefined -> writeUndefined()
            is Amf0Date -> writeDate(node)
            is Amf0Reference -> writeReference(node)
        }
    }

    private fun writeReference(node: Amf0Reference) {
        output.writeByte(Amf0Reference.TYPE)
        output.writeShort(node.value.toInt())
    }

    private fun writeDate(node: Amf0Date) {
        output.writeByte(Amf0Date.TYPE)
        output.writeDouble(node.value)
        output.writeShort(node.timezone.toInt())
    }

    suspend fun writeAll(nodes: List<Amf0Node>) = nodes.forEachAsync {
        write(it)
    }
}