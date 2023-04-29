package patcher.manifest

import LeagueToolkit.IO.ReleaseManifestFile.ReleaseManifestBody
import com.github.luben.zstd.Zstd.decompress
import java.io.DataInputStream
import java.io.FileInputStream
import java.nio.ByteBuffer

@OptIn(ExperimentalUnsignedTypes::class)
class ReleaseManifest(val input: FileInputStream) {
    val dataStream = DataInputStream(input)
    val body: ReleaseManifestBody

    init {
        dataStream.use {
            val magicBuffer = ByteArray(4)
            dataStream.readFully(magicBuffer)
            val magic = magicBuffer.decodeToString()
            if (magic != "RMAN") {
                throw Exception("Invalid manifest file")
            }

            val major = dataStream.readByte()
            val minor = dataStream.readByte()

            if (major != 2.toByte())
                throw Exception("Unsupported manifest version: $major.$minor")

            val unknown = dataStream.readByte()
            if (unknown != 0.toByte())
                throw Exception("Unknown byte: $unknown")

            val signatureType = dataStream.readByte()
            val contentOffset = dataStream.readInt().toUInt();
            val compressedSize = dataStream.readInt().toUInt();
            val id = dataStream.readLong().toULong()
            val uncompressedSize = dataStream.readInt().toUInt()

            input.channel.position(contentOffset.toLong())

            val compressedFile = ByteArray(compressedSize.toInt())
            dataStream.readFully(compressedFile)

            val uncompressedFile = decompress(compressedFile, uncompressedSize.toInt())
            body = ReleaseManifestBody.getRootAsReleaseManifestBody(ByteBuffer.wrap(uncompressedFile))
        }
    }
}