package rtmp.amf3

import simpleJson.*

fun JsonNode.toAm3Object(): Amf3Node = when (this) {
    is JsonArray -> Amf3Array(value.map(JsonNode::toAm3Object).toMutableList())
    is JsonBoolean -> if (value) Amf3True else Amf3False
    JsonNull -> Amf3Null
    is JsonNumber -> Amf3Integer(value.toInt())
    is JsonObject -> Amf3Object(null, value.mapValues { it.value.toAm3Object() }.toMutableMap())
    is JsonString -> Amf3String(value)
}