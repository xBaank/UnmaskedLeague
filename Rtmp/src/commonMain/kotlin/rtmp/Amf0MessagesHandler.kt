package rtmp

import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import okio.Buffer
import rtmp.amf0.*
import rtmp.packets.*
import kotlin.collections.set

internal const val CHUNK_SIZE = 128


internal const val TIMESTAMP_SIZE = 3
internal const val LENGTH_SIZE = 3
internal const val MESSAGE_ID_SIZE = 4

class Amf0MessagesHandler(
    private val amfLists: AmfLists = AmfLists(),
    private val incomingPartialRawMessages: MutableMap<Byte, RawRtmpPacket> = mutableMapOf(),
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val interceptor: (List<Amf0Node>) -> List<Amf0Node>
) {

    private val payloadBuffer = ByteArray(CHUNK_SIZE)

    suspend fun start(): Unit = coroutineScope {
        while (isActive) {
            val header = readHeader()
            val packet = incomingPartialRawMessages.getOrPut(header.channelId) {
                val length = if (header is RTMPPacketHeader0) header.length.toInt() else 0
                RawRtmpPacket(header, Buffer(), length)
            }

            val toRead = minOf(CHUNK_SIZE, packet.length)
            input.readFully(payloadBuffer, 0, toRead)
            packet.payload.write(payloadBuffer, 0, toRead)
            packet.length -= toRead

            if (packet.length == 0) {
                incomingPartialRawMessages.remove(header.channelId)
                handle(packet)
            }
        }
    }

    private suspend fun write(packet: RawRtmpPacket): Unit = coroutineScope {
        writeHeader(packet.header)
        while (packet.length > 0 && isActive) {
            val toWrite = minOf(CHUNK_SIZE, packet.length).toLong()
            val asByteArray = packet.payload.readByteArray(toWrite)
            output.writeFully(asByteArray, 0, toWrite.toInt())

            packet.length -= toWrite.toInt()
            if (packet.length != 0) {
                val firstByte =
                    ((CHUNCK_HEADER_TYPE_3.toInt() shl 6) and 0b11000000) or (packet.header.channelId.toInt() and 0b00111111)
                output.writeByte(firstByte)
            }
        }
    }

    private suspend fun handle(packet: RawRtmpPacket): Unit = coroutineScope {
        if (packet.header is RTMPPacketHeader0 && packet.header.messageTypeId.toInt() == 0x14) {
            val message = AMF0Decoder(packet.payload, amfLists).decodeAll().let(interceptor)
            val newMessageRaw = Buffer()
            Amf0Encoder(newMessageRaw).encodeAll(message)
            val newHeader = RTMPPacketHeader0(
                chunkBasicHeader = packet.header.chunkBasicHeader,
                timeStamp = packet.header.timeStamp,
                length = newMessageRaw.size.toInt().toLengthArray(),
                messageTypeId = packet.header.messageTypeId,
                streamId = packet.header.streamId
            )
            write(RawRtmpPacket(newHeader, newMessageRaw, newMessageRaw.size.toInt()))
        } else if (packet.header is RTMPPacketHeader0 && packet.header.messageTypeId.toInt() == 0x11) {
            val obj = mutableMapOf<String, Amf0Node>()
            if (packet.payload[0].toInt() == 0x00) {
                obj["version"] = Amf0Number(0.0)
                packet.payload.readByte()
            }
            obj["result"] = AMF0Decoder(packet.payload, amfLists).decode()
            obj["invokeId"] = AMF0Decoder(packet.payload, amfLists).decode()
            obj["serviceCall"] = AMF0Decoder(packet.payload, amfLists).decode()
            obj["data"] = AMF0Decoder(packet.payload, amfLists).decode()

            interceptor(listOf(Amf0Object(obj)))

            val newMessageRaw = Buffer()
            obj["version"]?.let { newMessageRaw.writeByte(0x00) }
            obj["result"]?.let { Amf0Encoder(newMessageRaw).encode(it) }
            obj["invokeId"]?.let { Amf0Encoder(newMessageRaw).encode(it) }
            obj["serviceCall"]?.let { Amf0Encoder(newMessageRaw).encode(it) }
            obj["data"]?.let { Amf0Encoder(newMessageRaw).encode(it) }


            val newHeader = RTMPPacketHeader0(
                chunkBasicHeader = packet.header.chunkBasicHeader,
                timeStamp = packet.header.timeStamp,
                length = newMessageRaw.size.toInt().toLengthArray(),
                messageTypeId = packet.header.messageTypeId,
                streamId = packet.header.streamId
            )
            write(RawRtmpPacket(newHeader, newMessageRaw, newMessageRaw.size.toInt()))
        } else {
            if (packet.header is RTMPPacketHeader1) println("asd")
            packet.length = packet.payload.size.toInt()
            write(packet)
        }
    }


    private suspend fun readHeader(): RTMPPacketHeader {
        val firstByte = input.readByte().toInt()
        val chunkHeaderType = (firstByte shr 6 and 0b11).toByte()
        val channelId = (firstByte and 0b00111111).toByte()
        val chunkBasicHeader = ChunkBasicHeader(firstByte.toByte(), chunkHeaderType, channelId)

        return when (chunkBasicHeader.chunkHeaderType) {
            CHUNCK_HEADER_TYPE_0 -> {
                val timeStampArray = ByteArray(TIMESTAMP_SIZE)
                input.readFully(timeStampArray, 0, TIMESTAMP_SIZE)

                val lengthArray = ByteArray(LENGTH_SIZE)
                input.readFully(lengthArray, 0, LENGTH_SIZE)

                val messageIdType = input.readByte()

                val streamIdArray = ByteArray(MESSAGE_ID_SIZE)
                input.readFully(streamIdArray, 0, MESSAGE_ID_SIZE)

                RTMPPacketHeader0(
                    chunkBasicHeader = chunkBasicHeader,
                    timeStamp = timeStampArray,
                    length = lengthArray,
                    messageTypeId = messageIdType,
                    streamId = streamIdArray,
                )
            }

            CHUNCK_HEADER_TYPE_1 -> {
                val timeStampArray = ByteArray(TIMESTAMP_SIZE)
                input.readFully(timeStampArray, 0, TIMESTAMP_SIZE)

                val lengthArray = ByteArray(LENGTH_SIZE)
                input.readFully(lengthArray, 0, LENGTH_SIZE)

                val messageIdType = input.readByte()

                RTMPPacketHeader1(
                    chunkBasicHeader = chunkBasicHeader,
                    timeStamp = timeStampArray,
                    length = lengthArray,
                    messageTypeId = messageIdType,
                )
            }

            CHUNCK_HEADER_TYPE_2 -> {
                val timeStampArray = ByteArray(TIMESTAMP_SIZE)
                input.readFully(timeStampArray, 0, TIMESTAMP_SIZE)

                RTMPPacketHeader2(
                    chunkBasicHeader = chunkBasicHeader,
                    timeStamp = timeStampArray,
                )
            }

            CHUNCK_HEADER_TYPE_3 -> chunkBasicHeader

            else -> throw Exception("Unknown chunk header type")
        }
    }

    private suspend fun writeHeader(header: RTMPPacketHeader) = when (header) {
        is ChunkBasicHeader -> {
            output.writeByte(header.originalFirtByte)
        }

        is RTMPPacketHeader0 -> {
            output.writeByte(header.originalFirtByte)
            output.writeFully(header.timeStamp)
            output.writeFully(header.length)
            output.writeByte(header.messageTypeId)
            output.writeFully(header.streamId)
        }

        is RTMPPacketHeader1 -> {
            output.writeByte(header.originalFirtByte)
            output.writeFully(header.timeStamp)
            output.writeFully(header.length)
            output.writeByte(header.messageTypeId)
        }

        is RTMPPacketHeader2 -> {
            output.writeByte(header.originalFirtByte)
            output.writeFully(header.timeStamp)
        }
    }
}


