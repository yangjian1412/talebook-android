package com.talebook.app.data.opds

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class XmlLite private constructor(
    val name: String,
    private val attrs: Map<String, String>,
    val text: String?,
    val children: List<XmlLite>
) {
    fun attr(name: String): String? = attrs[name]

    fun firstByPath(path: String): XmlLite? {
        val parts = path.split("/")
        return firstByPathRec(parts, 0)
    }

    private fun firstByPathRec(parts: List<String>, idx: Int): XmlLite? {
        val target = parts[idx]
        val candidate = if (idx == 0 && parts[0] == name) this else children.firstOrNull { it.name == target }
            ?: if (idx == 0) children.firstOrNull { it.name == target } else null
        return if (candidate == null) {
            null
        } else if (idx == parts.lastIndex) candidate
        else candidate.firstByPathRec(parts, idx + 1)
    }

    fun allByPath(path: String): List<XmlLite> {
        val parts = path.split("/")
        val out = mutableListOf<XmlLite>()
        collectAll(parts, 0, out)
        return out
    }

    private fun collectAll(parts: List<String>, idx: Int, out: MutableList<XmlLite>) {
        val target = parts[idx]
        val matches = if (idx == 0 && parts[0] == name) listOf(this) else children.filter { it.name == target }
        for (m in matches) {
            if (idx == parts.lastIndex) out.add(m) else m.collectAll(parts, idx + 1, out)
        }
    }

    companion object {
        fun parse(xml: String): XmlLite {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(xml.byteInputStream(), "UTF-8")

            val stack = ArrayDeque<Node>()
            var root: XmlLite? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val attrs = (0 until parser.attributeCount).associate { i ->
                            parser.getAttributeName(i) to parser.getAttributeValue(i)
                        }
                        stack.addLast(Node(parser.name, attrs, StringBuilder()))
                    }
                    XmlPullParser.TEXT -> {
                        stack.lastOrNull()?.text?.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        val node = stack.removeLastOrNull() ?: break
                        val lite = XmlLite(node.name, node.attrs, node.text.toString().takeIf { it.isNotEmpty() }, node.children.toList())
                        if (stack.isEmpty()) {
                            root = lite
                        } else {
                            stack.last().children.add(lite)
                        }
                    }
                }
                event = parser.next()
            }
            return root ?: XmlLite("__empty__", attrs = emptyMap(), text = null, children = emptyList())
        }

        private class Node(
            val name: String,
            val attrs: Map<String, String>,
            val text: StringBuilder
        ) {
            val children = mutableListOf<XmlLite>()
        }
    }
}