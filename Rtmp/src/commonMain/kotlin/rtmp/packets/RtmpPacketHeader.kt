package rtmp.packets

import okio.Buffer


internal const val CHUNCK_HEADER_TYPE_0: Byte = 0x00
internal const val CHUNCK_HEADER_TYPE_1: Byte = 0x01
internal const val CHUNCK_HEADER_TYPE_2: Byte = 0x02
internal const val CHUNCK_HEADER_TYPE_3: Byte = 0x03

sealed interface RTMPPacketHeader {
    val originalFirtByte: Byte
    val chunkHeaderType: Byte
    val channelId: Byte
}

class RTMPPacketHeader0(
    val chunkBasicHeader: ChunkBasicHeader,
    val timeStamp: ByteArray,
    var length: ByteArray,
    val messageTypeId: Byte,
    val streamId: ByteArray,
) : RTMPPacketHeader by chunkBasicHeader

class RTMPPacketHeader1(
    val chunkBasicHeader: ChunkBasicHeader,
    val timeStamp: ByteArray,
    val length: ByteArray,
    val messageTypeId: Byte,
) : RTMPPacketHeader by chunkBasicHeader

class RTMPPacketHeader2(
    val chunkBasicHeader: ChunkBasicHeader,
    val timeStamp: ByteArray,
) : RTMPPacketHeader by chunkBasicHeader

class ChunkBasicHeader(
    override val originalFirtByte: Byte,
    override val chunkHeaderType: Byte,
    override val channelId: Byte
) : RTMPPacketHeader

fun ByteArray.toInt() = Buffer().writeByte(0).write(this).readInt()
