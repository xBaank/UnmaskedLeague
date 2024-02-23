package rtmp.amf0

import io.ktor.utils.io.errors.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import okio.BufferedSource
import rtmp.AmfLists
import rtmp.amf3.AMF3Decoder
import rtmp.readDouble

class AMF0Decoder(private val input: BufferedSource, private val amfLists: AmfLists) {

    suspend fun decodeAll(): List<Amf0Node> = coroutineScope {
        val result = mutableListOf<Amf0Node>()
        while (isActive) {
            val node = decode()
            result.add(node)
            if (input.exhausted()) break
        }
        result
    }


    suspend fun decode(): Amf0Node {
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
            Amf0Amf3.TYPE -> readAMF3()
            else -> throw NotImplementedError("Unsupported AMF0 type: $type")
        }
    }

    private suspend fun readAMF3(): Amf0Amf3 {
        val decoded = AMF3Decoder(input, amfLists).decodeAll()
        return Amf0Amf3(decoded)
    }

    private fun readAMF0Reference(): Amf0Node {
        val reference = input.readShort().toInt()
        return amfLists.amf0ObjectList[reference]
    }

    private fun readAMF0Date(): Amf0Node {
        val date = input.readDouble()
        val timezone = input.readShort()
        return Amf0Date(date, timezone)
    }

    private suspend fun readTypedObject(): Amf0TypedObject {
        val objectName = readAMF0String()
        val objectValue = readAMF0Object()
        val typedObject = Amf0TypedObject(objectName.value, objectValue.value)
        amfLists.amf0ObjectList += typedObject
        return typedObject
    }


    private fun readAMF0String(): Amf0String {
        val length = input.readShort()
        val buffer = ByteArray(length.toInt())
        input.readFully(buffer)
        return Amf0String(String(buffer))
    }


    private suspend fun readAMF0Object(): Amf0Object {
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


    private suspend fun readAMF0EcmaArray(): Amf0ECMAArray {
        val length = input.readInt()
        val result = mutableMapOf<String, Amf0Node>()
        for (i in 0..<length) {
            val propertyName = readAMF0String()
            val value = decode()
            result[propertyName.value] = value
        }
        val array = Amf0ECMAArray(result)
        amfLists.amf0ObjectList += array.value.values
        return array
    }


    private suspend fun readAMF0StrictArray(): Amf0StrictArray {
        val length = input.readInt()
        val result = mutableListOf<Amf0Node>()
        for (i in 0..<length) {
            val value = decode()
            result.add(value)
        }
        return Amf0StrictArray(result)
    }
}