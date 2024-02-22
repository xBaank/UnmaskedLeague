package rtmp

import rtmp.amf0.Amf0Node
import rtmp.amf3.Amf3Node
import rtmp.amf3.ClassDefinition

data class AmfLists(
    val stringList: MutableList<String> = mutableListOf(),
    val classList: MutableList<ClassDefinition> = mutableListOf(),
    val amf3ObjectList: MutableList<Amf3Node> = mutableListOf(),
    val amf0ObjectList: MutableList<Amf0Node> = mutableListOf()
)