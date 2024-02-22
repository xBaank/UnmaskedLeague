package rtmp.amf3

data class ClassDefinition(
    val externalizable: Boolean,
    val encoding: Int,
    val properties: MutableList<String>,
    val className: String
)

sealed interface Amf3Node

data object Amf3Undefined : Amf3Node {
    const val TYPE = 0x00
}

data object Amf3Null : Amf3Node {
    const val TYPE = 0x01
}

data object Amf3False : Amf3Node {
    const val TYPE = 0x02
}

data object Amf3True : Amf3Node {
    const val TYPE = 0x03
}

@JvmInline
value class Amf3Integer(val value: Int) : Amf3Node {
    companion object {
        const val TYPE = 0x04
    }
}

@JvmInline
value class Amf3Double(val value: Double) : Amf3Node {
    companion object {
        const val TYPE = 0x05
    }
}

@JvmInline
value class Amf3String(val value: String) : Amf3Node, CharSequence by value {
    companion object {
        const val TYPE = 0x06
    }
}

@JvmInline
value class Amf3XMLDocument(val value: String) : Amf3Node, CharSequence by value {
    companion object {
        const val TYPE = 0x07
    }
}

@JvmInline
value class Amf3Date(val value: Double) : Amf3Node {
    companion object {
        const val TYPE = 0x08
    }
}

@JvmInline
value class Amf3Array(val value: MutableList<Amf3Node>) : Amf3Node {
    companion object {
        const val TYPE = 0x09
    }
}

data class Amf3Object(val name: String?, val value: MutableMap<String, Amf3Node>) : Amf3Node {
    companion object {
        const val TYPE = 0x0A
    }
}

@JvmInline
value class Amf3ByteArray(val value: ByteArray) : Amf3Node {
    companion object {
        const val TYPE = 0x0C
    }
}

@JvmInline
value class Amf3VectorInt(val value: List<Int>) : Amf3Node {
    companion object {
        const val TYPE = 0x0D
    }
}

@JvmInline
value class Amf3VectorUint(val value: List<Int>) : Amf3Node {
    companion object {
        const val TYPE = 0x0E
    }
}

@JvmInline
value class Amf3VectorDouble(val value: List<Double>) : Amf3Node {
    companion object {
        const val TYPE = 0x0F
    }
}

@JvmInline
value class Amf3VectorObject(val value: List<Amf3Node>) : Amf3Node {
    companion object {
        const val TYPE = 0x10
    }
}

data object Amf3Dictionary : Amf3Node {
    const val TYPE = 0x11
}


// Extension functions for converting Kotlin types to AMF3 types

fun Int.toAmf3Integer(): Amf3Integer = Amf3Integer(this)

fun Double.toAmf3Double(): Amf3Double = Amf3Double(this)

fun Boolean.toAmf3Boolean(): Amf3Node = if (this) Amf3True else Amf3False

fun String.toAmf3String(): Amf3String = Amf3String(this)

fun ByteArray.toAmf3ByteArray(): Amf3ByteArray = Amf3ByteArray(this)

fun List<Int>.toAmf3VectorInt(): Amf3VectorInt = Amf3VectorInt(this)

fun List<Int>.toAmf3VectorUint(): Amf3VectorUint = Amf3VectorUint(this)

fun List<Double>.toAmf3VectorDouble(): Amf3VectorDouble = Amf3VectorDouble(this)

fun List<Amf3Node>.toAmf3VectorObject(): Amf3VectorObject = Amf3VectorObject(this)

// Extension functions for safely converting AMF3 types

fun Amf3Node?.toAmf3Integer(): Amf3Integer? = this as? Amf3Integer

fun Amf3Node?.toAmf3Double(): Amf3Double? = this as? Amf3Double

fun Amf3Node?.toAmf3Boolean(): Boolean? {
    return when (this) {
        is Amf3True -> true
        is Amf3False -> false
        else -> null
    }
}

fun Amf3Node?.toAmf3String(): Amf3String? = this as? Amf3String

fun Amf3Node?.toAmf3ByteArray(): Amf3ByteArray? = this as? Amf3ByteArray

fun Amf3Node?.toAmf3VectorInt(): Amf3VectorInt? = this as? Amf3VectorInt

fun Amf3Node?.toAmf3VectorUint(): Amf3VectorUint? = this as? Amf3VectorUint

fun Amf3Node?.toAmf3VectorDouble(): Amf3VectorDouble? = this as? Amf3VectorDouble

fun Amf3Node?.toAmf3VectorObject(): Amf3VectorObject? = this as? Amf3VectorObject

