package rtmp.packets

import okio.Buffer

class RawRtmpPacket(
    val header: RTMPPacketHeader,
    val payload: Buffer,
    var length: Int
)