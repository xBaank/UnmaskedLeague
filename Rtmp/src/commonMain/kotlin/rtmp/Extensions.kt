package rtmp

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import okio.BufferedSink
import okio.BufferedSource
import kotlin.coroutines.cancellation.CancellationException

fun BufferedSource.readDouble(): Double = Double.fromBits(readLong())
fun BufferedSink.writeDouble(value: Double) = writeLong(value.toBits())
fun Int.toLengthArray(): ByteArray  {
    val result = ByteArray(3)
    result[0] = (this ushr 16).toByte()
    result[1] = (this ushr 8).toByte()
    result[2] = this.toByte()
    return result
}
suspend inline fun <T> Iterable<T>.forEachAsync(crossinline action: (T) -> Unit) = coroutineScope {
    for (element in this@forEachAsync) {
        if (!isActive) throw CancellationException()
        action(element)
    }
}
