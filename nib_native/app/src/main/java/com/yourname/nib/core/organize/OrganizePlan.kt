package com.yourname.nib.core.organize

private const val keyChars = "-0123456789ABCDEFGHJKMNPQRSTVWXYZ"
private const val keyMax = keyChars.length - 1

private fun keyIndex(c: Char): Int = keyChars.indexOf(c)

private fun validKey(key: String): Boolean = key.all { keyIndex(it) >= 0 }

private fun tailAfter(a: String, from: Int): String {
    for (j in from until a.length) {
        val cj = keyIndex(a[j])
        if (cj < keyMax) return a.substring(from, j) + keyChars[(cj + 1 + keyMax) / 2]
    }
    return a.substring(from) + keyChars[keyMax / 2]
}

fun sortKeyBetween(prev: String?, next: String?): String? {
    if (prev != null && !validKey(prev)) return null
    if (next != null && !validKey(next)) return null
    if (prev != null && next != null && prev >= next) return null
    val a = prev.orEmpty()
    val b = next
    val out = StringBuilder()
    var i = 0
    while (true) {
        val ai = if (i < a.length) keyIndex(a[i]) else -1
        val bi = when {
            b == null -> keyMax + 1
            i < b.length -> keyIndex(b[i])
            else -> -1
        }
        if (ai + 1 < bi) {
            val low = maxOf(ai + 1, 0)
            val high = minOf(bi - 1, keyMax)
            if (low <= high) return out.append(keyChars[(low + high) / 2]).toString()
        }
        if (ai + 1 == bi && i < a.length) {
            return out.toString() + a[i] + tailAfter(a, i + 1)
        }
        if (ai == bi && i < a.length) {
            out.append(a[i])
            i++
            continue
        }
        return null
    }
}

fun rebalanceKeys(prev: String?, next: String?, count: Int): List<String> {
    if (count <= 0) return emptyList()
    val mid = sortKeyBetween(prev, next)
        ?: return List(count) { index -> prev.orEmpty() + keyChars[keyMax / 2] + index.toString() }
    val left = count / 2
    val right = count - 1 - left
    return rebalanceKeys(prev, mid, left) + listOf(mid) + rebalanceKeys(mid, next, right)
}

fun normalizeFtsQuery(raw: String): String? {
    val q = raw.trim()
    if (q.length < 3) return null
    return "\"" + q.replace("\"", "\"\"") + "\""
}

fun shareTextFor(subject: String?, text: String?): String? {
    val s = subject?.trim().orEmpty()
    val t = text?.trim().orEmpty()
    if (s.isEmpty()) return t.ifEmpty { null }
    if (t.isEmpty()) return s
    return if (t.contains(s)) t else "$s $t"
}

fun selectedInOrder(selected: Set<String>, orderedIds: List<String>): List<String> =
    orderedIds.filter { it in selected }

fun batchTagLinks(
    links: Map<String, Set<String>>,
    ids: Set<String>,
    tagId: String,
): Map<String, Set<String>> = links.toMutableMap().also { result ->
    ids.forEach { id ->
        val next = result[id].orEmpty().toMutableSet()
        next.add(tagId)
        result[id] = next
    }
}

fun reorderIds(ids: List<String>, from: Int, to: Int): List<String> {
    if (from !in ids.indices || to !in ids.indices || from == to) return ids
    return ids.toMutableList().also { it.add(to, it.removeAt(from)) }
}

fun <T> pinnedOrder(
    items: List<T>,
    pinned: (T) -> Boolean,
    sortKey: (T) -> String,
): List<T> = items.sortedWith(
    compareByDescending<T> { if (pinned(it)) 1 else 0 }.thenBy { sortKey(it) },
)
