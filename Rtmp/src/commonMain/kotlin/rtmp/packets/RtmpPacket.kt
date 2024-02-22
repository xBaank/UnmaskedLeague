package rtmp.packets

import okio.Buffer

class RawRtmpPacket(
    val header: RTMPPacketHeader,
    var payload: Buffer,
    var length: Int
)