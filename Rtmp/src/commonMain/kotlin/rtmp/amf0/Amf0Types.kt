package rtmp.amf0

import arrow.core.raise.either
import rtmp.amf3.Amf3Node
import simpleJson.JsonNode
import simpleJson.asObject

sealed interface Amf0Node

@JvmInline
value class Amf0Number(val value: Double) : Amf0Node {
    companion object {
        const val TYPE = 0x00
    }
}

@JvmInline
value class Amf0Boolean(val value: Boolean) : Amf0Node {
    companion object {
        const val TYPE = 0x01
    }
}

@JvmInline
value class Amf0String(val value: String) : Amf0Node, CharSequence by value {
    companion object {
        const val TYPE = 0x02
    }
}

@JvmInline
value class Amf0Object(val value: MutableMap<String, Amf0Node>) : Amf0Node {
    companion object {
        const val TYPE = 0x03
        const val OBJECT_END = 0x09
    }
}

data class Amf0TypedObject(val name: String, val value: MutableMap<String, Amf0Node>) : Amf0Node {
    companion object {
        const val TYPE = 0x10
    }
}

data object Amf0Null : Amf0Node {
    const val TYPE = 0x05
}

data object Amf0Undefined : Amf0Node {
    const val TYPE = 0x06
}

@JvmInline
value class Amf0Reference(val value: Short) : Amf0Node {
    companion object {
        const val TYPE = 0x07
    }
}

@JvmInline
value class Amf0ECMAArray(val value: MutableMap<String, Amf0Node>) : Amf0Node {
    companion object {
        const val TYPE = 0x08
    }
}

@JvmInline
value class Amf0StrictArray(val value: MutableList<Amf0Node>) : Amf0Node {
    companion object {
        const val TYPE = 0x0A
    }
}

data class Amf0Date(val value: Double, val timezone: Short) : Amf0Node {
    companion object {
        const val TYPE = 0x0B
    }
}

data class Amf0Amf3(val nodes: List<Amf3Node>) : Amf0Node {
    companion object {
        const val TYPE = 0x11
    }
}


//extension functions for types
fun Number.toAmf0Number(): Amf0Number = Amf0Number(this.toDouble())
fun Boolean.toAmf0Boolean(): Amf0Boolean = Amf0Boolean(this)
fun String.toAmf0String(): Amf0String = Amf0String(this)

fun Nothing?.toAmf0Null(): Amf0Null = Amf0Null
fun Map<String, Amf0Node>.toAmf0Object(): Amf0Object = Amf0Object(this.toMutableMap())
fun Map<String, Amf0Node>.toAmf0ECMAArray(): Amf0ECMAArray = Amf0ECMAArray(this.toMutableMap())
fun List<Amf0Node>.toAmf0StrictArray(): Amf0StrictArray = Amf0StrictArray(this.toMutableList())

//safe extension functions for types
fun Amf0Node?.toAmf0Number(): Amf0Number? = this as? Amf0Number
fun Amf0Node?.toAmf0Boolean(): Amf0Boolean? = this as? Amf0Boolean
fun Amf0Node?.toAmf0String(): Amf0String? = this as? Amf0String
fun Amf0Node?.toAmf0Object(): Amf0Object? = this as? Amf0Object
fun Amf0Node?.toAmf0TypedObject(): Amf0TypedObject? = this as? Amf0TypedObject
fun Amf0Node?.toAmf0Null(): Amf0Null? = this as? Amf0Null
fun Amf0Node?.toAmf0Undefined(): Amf0Undefined? = this as? Amf0Undefined
fun Amf0Node?.toAmf0ECMAArray(): Amf0ECMAArray? = this as? Amf0ECMAArray
fun Amf0Node?.toAmf0StrictArray(): Amf0StrictArray? = this as? Amf0StrictArray

//getter functions for types
operator fun Amf0Node.get(key: String): Amf0Node? = when (this) {
    is Amf0Object -> value[key]
    is Amf0TypedObject -> value[key]
    is Amf0ECMAArray -> value[key]
    else -> null
}

operator fun Amf0Node.get(index: Int): Amf0Node? = when (this) {
    is Amf0StrictArray -> value[index]
    else -> null
}

//setter functions for types
operator fun Amf0Node.set(key: String, value: Amf0Node) {
    when (this) {
        is Amf0Object -> this.value[key] = value
        is Amf0TypedObject -> this.value[key] = value
        is Amf0ECMAArray -> this.value[key] = value
        else -> {}
    }
}

operator fun Amf0Node.set(index: Int, value: Amf0Node) {
    when (this) {
        is Amf0StrictArray -> this.value[index] = value
        else -> {}
    }
}

fun JsonNode.toAmf0TypedObject(name: String) = either {
    asObject().bind().map {

    }
    TODO()
}


