package com.yourname.nib.core.export

fun jsonText(value: String?): String {
    if (value == null) return "null"
    val out = StringBuilder(value.length + 2)
    out.append('"')
    for (c in value) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
    }
    out.append('"')
    return out.toString()
}

fun jsonNumber(value: Number?): String = value?.toString() ?: "null"

fun jsonObject(fields: List<Pair<String, String>>): String =
    fields.joinToString(",", "{", "}") { (key, raw) -> "${jsonText(key)}:$raw" }

fun jsonArray(items: List<String>): String = items.joinToString(",", "[", "]")

fun parseJson(text: String): Any? = JsonReader(text).read()

private class JsonReader(private val source: String) {
    private var at = 0

    fun read(): Any? {
        skip()
        val result = value()
        skip()
        return result
    }

    private fun value(): Any? = when (current()) {
        '{' -> obj()
        '[' -> arr()
        '"' -> string()
        't' -> { word("true"); true }
        'f' -> { word("false"); false }
        'n' -> { word("null"); null }
        else -> number()
    }

    private fun obj(): Map<String, Any?> {
        at++
        val map = LinkedHashMap<String, Any?>()
        skip()
        if (current() == '}') { at++; return map }
        while (at < source.length) {
            skip()
            val key = string()
            skip()
            expect(':')
            skip()
            map[key] = value()
            skip()
            when (current()) {
                ',' -> at++
                '}' -> { at++; return map }
                else -> error("invalid json object at $at")
            }
        }
        error("unterminated json object")
    }

    private fun arr(): List<Any?> {
        at++
        val list = ArrayList<Any?>()
        skip()
        if (current() == ']') { at++; return list }
        while (at < source.length) {
            skip()
            list.add(value())
            skip()
            when (current()) {
                ',' -> at++
                ']' -> { at++; return list }
                else -> error("invalid json array at $at")
            }
        }
        error("unterminated json array")
    }

    private fun string(): String {
        expect('"')
        val out = StringBuilder()
        while (at < source.length) {
            val c = source[at++]
            if (c == '"') return out.toString()
            if (c != '\\') { out.append(c); continue }
            if (at >= source.length) error("bad escape")
            when (val e = source[at++]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (at + 4 > source.length) error("bad unicode escape")
                    out.append(source.substring(at, at + 4).toInt(16).toChar())
                    at += 4
                }
                else -> error("bad escape $e")
            }
        }
        error("unterminated string")
    }

    private fun number(): Number {
        val start = at
        while (at < source.length && source[at] in "-+.eE0123456789") at++
        val raw = source.substring(start, at)
        if (raw.isEmpty()) error("invalid number at $start")
        return if (raw.any { it == '.' || it == 'e' || it == 'E' }) raw.toDouble() else raw.toLong()
    }

    private fun word(text: String) {
        if (!source.startsWith(text, at)) error("invalid token at $at")
        at += text.length
    }

    private fun expect(c: Char) {
        if (current() != c) error("expected $c at $at")
        at++
    }

    private fun current(): Char = if (at < source.length) source[at] else '\u0000'

    private fun skip() {
        while (at < source.length && source[at].isWhitespace()) at++
    }
}
