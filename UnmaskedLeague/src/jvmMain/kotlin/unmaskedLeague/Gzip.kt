package unmaskedLeague

import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

//TODO Can we use a multiplatform library for this?

fun String.base64Ungzip(): String {
    val gzipped: ByteArray = Base64.getDecoder().decode(this.toByteArray())
    val `in` = GZIPInputStream(gzipped.inputStream())
    return String(`in`.readBytes())
}

fun String.gzipBase64(): String {
    this.toByteArray(Charsets.UTF_8)
    val outputStream = ByteArrayOutputStream()
    GZIPOutputStream(outputStream).bufferedWriter(Charsets.UTF_8).use { it.write(this); it.flush() }
    return Base64.getEncoder().encodeToString(outputStream.toByteArray())
}