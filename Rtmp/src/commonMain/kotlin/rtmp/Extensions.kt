package rtmp

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import okio.BufferedSink
import okio.BufferedSource

fun BufferedSource.readDouble(): Double = Double.fromBits(readLong())
fun BufferedSink.writeDouble(value: Double) = writeLong(value.toBits())
fun Int.toLengthArray(): ByteArray {
    val result = ByteArray(3)
    result[0] = (this ushr 16).toByte()
    result[1] = (this ushr 8).toByte()
    result[2] = this.toByte()
    return result
}

suspend inline fun <T> Iterable<T>.forEachAsync(crossinline action: suspend (T) -> Unit) = coroutineScope {
    for (element in this@forEachAsync) {
        ensureActive()
        action(element)
    }
}
