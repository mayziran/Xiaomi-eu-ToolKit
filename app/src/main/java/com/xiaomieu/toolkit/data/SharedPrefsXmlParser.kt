package com.xiaomieu.toolkit.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Parses an Android SharedPreferences XML document (`<map> ... </map>`) into a
 * `name -> value` map. Only scalar entries are handled; `<set>` collections are ignored,
 * which is enough for the XMSF registration files.
 */
object SharedPrefsXmlParser {

    private val SCALAR_TAGS = setOf("string", "int", "long", "float", "boolean")

    fun parse(xml: String): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var pendingName: String? = null
        var pendingType: String? = null
        val buffer = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag in SCALAR_TAGS) {
                        pendingName = parser.getAttributeValue(null, "name")
                        pendingType = tag
                        buffer.setLength(0)
                    }
                }

                XmlPullParser.TEXT -> {
                    if (pendingName != null) {
                        buffer.append(parser.text ?: "")
                    }
                }

                XmlPullParser.END_TAG -> {
                    val name = pendingName
                    if (name != null && parser.name in SCALAR_TAGS) {
                        result[name] = convert(pendingType ?: "string", buffer.toString())
                        pendingName = null
                        pendingType = null
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    fun stringValues(xml: String): Map<String, String> =
        parse(xml).mapValues { it.value.toString() }

    fun longValues(xml: String): Map<String, Long> =
        parse(xml).mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()

    private fun convert(type: String, text: String): Any = when (type) {
        "int" -> text.toIntOrNull() ?: 0
        "long" -> text.toLongOrNull() ?: 0L
        "float" -> text.toFloatOrNull() ?: 0f
        "boolean" -> text == "true"
        else -> text
    }
}
