package rtmp.amf3

import arrow.core.getOrElse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import okio.BufferedSource
import rtmp.AmfLists
import rtmp.readDouble
import simpleJson.asObject
import simpleJson.deserialized
import kotlin.collections.set
import kotlin.experimental.and

class AMF3Decoder(private val input: BufferedSource, private val amfLists: AmfLists) {

    suspend fun decodeAll(): List<Amf3Node> = coroutineScope {
        val result = mutableListOf<Amf3Node>()
        while (isActive) {
            val node = decode()
            result.add(node)
            if (input.exhausted()) break
        }
        result
    }

    private fun decode(): Amf3Node {
        val type = input.readByte()
        return when (type.toInt()) {
            Amf3Undefined.TYPE -> Amf3Undefined
            Amf3Null.TYPE -> Amf3Null
            Amf3False.TYPE -> Amf3False
            Amf3True.TYPE -> Amf3True
            Amf3Integer.TYPE -> readAmf3Int()
            Amf3Double.TYPE -> Amf3Double(input.readDouble())
            Amf3String.TYPE -> readAmf3String()
            Amf3Array.TYPE -> readAmf3Array()
            Amf3Object.TYPE -> readAmf3Object()
            Amf3ByteArray.TYPE -> readAmf3ByteArray()
            Amf3Dictionary.TYPE -> Amf3Dictionary
            Amf3Date.TYPE -> readAmf3Date()
            else -> throw NotImplementedError("Unsupported AMF3 type: $type")
        }
    }

    private fun readAmf3Date(): Amf3Node {
        val date = input.readDouble()
        return Amf3Date(date)
    }

    private fun readAmf3ByteArray(): Amf3ByteArray {
        val type = readAmf3Int().value
        if ((type and 0x01) == 0) {
            return amfLists.amf3ObjectList[type shr 1] as Amf3ByteArray
        } else {
            val bytes = input.readByteArray((type shr 1).toLong())
            val uint8Array = Amf3ByteArray(bytes)
            amfLists.amf3ObjectList += uint8Array
            return uint8Array
        }
    }

    private fun readDSK(): Amf3Object {
        val typedObject = readDSA()
        val flags = readFlagData()
        flags.forEach { flag ->
            readRemaining(flag, 0)
        }
        return typedObject
    }

    private fun readDSA(): Amf3Object {
        val typedObject = Amf3Object("DSA", mutableMapOf())
        val flags = readFlagData()
        for ((index, flag) in flags.withIndex()) {
            var bits = 0
            if (index == 0) {
                if ((flag and 0x01) != 0) typedObject.value["body"] = decode()
                if ((flag and 0x02) != 0) typedObject.value["clientId"] = decode()
                if ((flag and 0x04) != 0) typedObject.value["destination"] = decode()
                if ((flag and 0x08) != 0) typedObject.value["headers"] = decode()
                if ((flag and 0x10) != 0) typedObject.value["messageId"] = decode()
                if ((flag and 0x20) != 0) typedObject.value["timeStamp"] = decode()
                if ((flag and 0x40) != 0) typedObject.value["timeToLive"] = decode()
                bits = 7
            } else if (index == 1) {
                if ((flag and 0x01) != 0) {
                    typedObject.value["clientId"] = Amf3String(convertByteArrayToId(readAmf3ByteArray().value))
                }
                if ((flag and 0x02) != 0) {
                    typedObject.value["messageId"] = Amf3String(convertByteArrayToId(readAmf3ByteArray().value))
                }
                bits = 2
            }
            readRemaining(flag, bits)
        }
        val flags2 = readFlagData()
        for ((index, flag) in flags2.withIndex()) {
            var bits = 0
            if (index == 0) {
                if ((flag and 0x01) != 0) typedObject.value["correlationId"] = decode()
                if ((flag and 0x02) != 0) {
                    val ignored = readAmf3Int()
                    typedObject.value["correlationId"] = Amf3String(convertByteArrayToId(readAmf3ByteArray().value))
                }
                bits = 2
            }
            readRemaining(flag, bits)
        }
        return typedObject
    }

    private fun readRemaining(flag: Int, bits: Int) {
        if (flag shr bits == 0) return
        for (i in bits..<6) {
            if ((flag shr i) and 1 != 0) {
                val o = decode()
                println("Ignoring AMF3 $o")
            }
        }
    }

    private fun createArrayCollection(array: Amf3Array): Amf3Object {
        val typedObject = Amf3Object("flex.messaging.io.ArrayCollection", mutableMapOf())
        typedObject.value["array"] = array
        return typedObject
    }

    private fun readFlagData(): IntArray {
        val flags = mutableListOf<Int>()
        var flag: Int
        do {
            flags.add(input.readByte().toInt().also { flag = it })
        } while ((flag and 0x80) != 0)
        return flags.toIntArray()
    }

    private fun convertByteArrayToId(bytes: ByteArray): String {
        val builder = mutableListOf<String>()
        for ((index, byte) in bytes.withIndex()) {
            if (index == 4 || index == 6 || index == 8 || index == 10) builder.add("-")
            builder.add(byte.toInt().and(0xFF).toString(16).padStart(2, '0'))
        }
        return builder.joinToString("")
    }

    private fun readAmf3Array(): Amf3Array {
        val type: Int = readAmf3Int().value
        if ((type and 0x01) == 0) {
            return amfLists.amf3ObjectList[type shr 1] as Amf3Array
        } else {
            val size: Int = type shr 1
            val key: String = readAmf3String().value
            if (key.isEmpty()) {
                val objects = Array(size) { decode() }
                amfLists.amf3ObjectList += objects
                return Amf3Array(objects.toMutableList())
            } else {
                throw Error("Associative arrays are not supported")
            }
        }
    }

    private fun readAmf3Object(): Amf3Object {
        val type = readAmf3Int().value
        if ((type and 0x01) == 0) return amfLists.amf3ObjectList[type shr 1] as Amf3Object

        val defineInline = ((type shr 1) and 0x01) != 0

        val classDefinition: ClassDefinition = if (defineInline) {
            val classDefinition = ClassDefinition(
                externalizable = ((type shr 2) and 1) != 0,
                encoding = (type shr 2) and 0x03,
                properties = mutableListOf(),
                className = readAmf3String().value
            )

            for (i in 0..<(type shr 4)) {
                classDefinition.properties.add(i, readAmf3String().value)
            }

            amfLists.classList += classDefinition
            classDefinition
        } else {
            amfLists.classList[type]
        }


        val typedObject: Amf3Object = if (classDefinition.externalizable) {
            when (classDefinition.className) {
                "DSK" -> readDSK()
                "DSA" -> readDSA()
                "flex.messaging.io.ArrayCollection" -> createArrayCollection(readAmf3Array())

                "com.riotgames.platform.systemstate.ClientSystemStatesNotification",
                "com.riotgames.platform.broadcast.BroadcastNotification",
                "com.riotgames.platform.summoner.SummonerCatalog",
                "com.riotgames.platform.game.GameTypeConfigDTO" -> readJson()

                else -> throw Error("Unhandled Externalizable: ${classDefinition.className}\nRAW:")
            }
        } else {
            val typedObject = Amf3Object(classDefinition.className, mutableMapOf())
            for (i in 0..<classDefinition.properties.size) {
                typedObject.value[classDefinition.properties[i]] = decode()
            }

            if (classDefinition.encoding == 0x02) {
                while (true) {
                    val key = readAmf3String()
                    if (key.isEmpty()) break
                    typedObject.value[key.value] = decode()
                }
            }
            typedObject
        }

        amfLists.amf3ObjectList += typedObject
        return typedObject
    }

    private fun readJson(): Amf3Object {
        var size = 0
        repeat(4) {
            size = size * 256 + (input.readByte() and 0xFF.toByte()).toUInt().toInt()
        }
        val jsonString = input.readByteArray(size.toLong()).decodeToString()
        val jsonObj = jsonString.deserialized().asObject().getOrElse { throw it }
        return jsonObj.toAm3Object() as Amf3Object
    }

    private fun readAmf3String(): Amf3String {
        val result: String
        val type = readAmf3Int().value
        if ((type and 0x01) != 0) {
            val length = type shr 1
            result = if (length > 0) {
                val bytes = input.readByteArray(length.toLong())
                String(bytes, charset("UTF-8"))
            } else ""
            amfLists.stringList += result
        } else result = amfLists.stringList[type shr 1]
        return Amf3String(result)
    }

    private fun readAmf3Int(): Amf3Integer {
        var result = 0
        var n = 0
        var b = (input.readByte() and 0xFF.toByte()).toUInt().toInt()
        while ((b and 0x80) != 0 && n < 3) {
            result = result shl 7
            result = result or (b and 0x7f)
            b = (input.readByte() and 0xFF.toByte()).toUInt().toInt()
            n++
        }
        if (n < 3) {
            result = result shl 7
            result = result or b
        } else {
            result = result shl 8
            result = result or b
            if ((result and 0x10000000) != 0) result = result or 0xe0000000.toInt()
        }
        return Amf3Integer(result)
    }


}
