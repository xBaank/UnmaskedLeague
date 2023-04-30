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

            repeat(2) { dataStream.readByte() }

            val contentOffset = dataStream.readInt().toLittleEndian()
            val compressedSize = dataStream.readInt().toLittleEndian()
            val id = dataStream.readLong().toLittleEndian()
            val uncompressedSize = dataStream.readInt().toLittleEndian()

            input.channel.position(contentOffset.toLong())

            val compressedFile = ByteArray(compressedSize)
            dataStream.readFully(compressedFile)

            val uncompressedFile = decompress(compressedFile, uncompressedSize)
            body = ReleaseManifestBody.getRootAsReleaseManifestBody(ByteBuffer.wrap(uncompressedFile))
            repeat(body.bundlesLength) {
                val file = body.bundles(it)
                println(file?.id)
            }
            println("finished")
        }
    }

    fun Int.toLittleEndian(): Int {
        return ((this and 0xff) shl 24) or
                (((this shr 8) and 0xff) shl 16) or
                (((this shr 16) and 0xff) shl 8) or
                ((this shr 24) and 0xff)
    }

    fun Long.toLittleEndian(): Long {
        return ((this and 0xff) shl 56) or
                (((this shr 8) and 0xff) shl 48) or
                (((this shr 16) and 0xff) shl 40) or
                (((this shr 24) and 0xff) shl 32) or
                (((this shr 32) and 0xff) shl 24) or
                (((this shr 40) and 0xff) shl 16) or
                (((this shr 48) and 0xff) shl 8) or
                ((this shr 56) and 0xff)
    }


}