package rtmp.amf0

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.io.IOException
import okio.BufferedSource
import rtmp.readDouble

class AMF0Decoder(private val input: BufferedSource) {

    suspend fun decodeAll(): List<Amf0Node> = coroutineScope {
        val result = mutableListOf<Amf0Node>()
        while (isActive) {
            val node = decode()
            result.add(node)
            if (input.exhausted()) break
        }
        result
    }


    fun decode(): Amf0Node {
        val type = input.readByte()
        return when (type.toInt()) {
            Amf0Number.TYPE -> Amf0Number(input.readDouble())
            Amf0Boolean.TYPE -> Amf0Boolean(input.readByte() == 0x01.toByte())
            Amf0String.TYPE -> readAMF0String()
            Amf0Object.TYPE -> readAMF0Object()
            Amf0Null.TYPE -> Amf0Null
            Amf0ECMAArray.TYPE -> readAMF0EcmaArray()
            Amf0StrictArray.TYPE -> readAMF0StrictArray()
            Amf0TypedObject.TYPE -> readTypedObject()
            Amf0Date.TYPE -> readAMF0Date()
            Amf0Reference.TYPE -> readAMF0Reference()
            else -> throw IOException("Unsupported AMF0 type: $type")
        }
    }

    private fun readAMF0Reference(): Amf0Node {
        val reference = input.readShort()
        return Amf0Reference(reference)
    }

    private fun readAMF0Date(): Amf0Node {
        val date = input.readDouble()
        val timezone = input.readShort()
        return Amf0Date(date, timezone)
    }

    private fun readTypedObject(): Amf0TypedObject {
        val objectName = readAMF0String()
        val objectValue = readAMF0Object()
        return Amf0TypedObject(objectName.value, objectValue.value)
    }


    private fun readAMF0String(): Amf0String {
        val length = input.readShort()
        val buffer = ByteArray(length.toInt())
        input.readFully(buffer)
        return Amf0String(String(buffer))
    }


    private fun readAMF0Object(): Amf0Object {
        val result = mutableMapOf<String, Amf0Node>()
        while (true) {
            val propertyName = readAMF0String()
            if (propertyName.isEmpty()) {
                val nextType = input.readByte().toInt()
                if (nextType == Amf0Object.OBJECT_END) {
                    break
                } else {
                    throw IOException("Invalid AMF0 object format")
                }
            } else {
                val value = decode()
                result[propertyName.value] = value
            }
        }
        return Amf0Object(result)
    }


    private fun readAMF0EcmaArray(): Amf0ECMAArray {
        val length = input.readInt()
        val result = mutableMapOf<String, Amf0Node>()
        for (i in 0 until length) {
            val propertyName = readAMF0String()
            val value = decode()
            result[propertyName.value] = value
        }
        return Amf0ECMAArray(result)
    }


    private fun readAMF0StrictArray(): Amf0StrictArray {
        val length = input.readInt()
        val result = mutableListOf<Amf0Node>()
        for (i in 0 until length) {
            val value = decode()
            result.add(value)
        }
        return Amf0StrictArray(result)
    }
}