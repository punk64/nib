package com.yourname.nib.core

import java.security.SecureRandom

private const val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
private val random = SecureRandom()
private val deviceId = newUlid()
private var hlcMs = 0L
private var hlcCounter = 0

fun localDeviceId(): String = deviceId

fun newUlid(): String {
    val now = System.currentTimeMillis()
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    bytes[0] = (now shr 40).toByte()
    bytes[1] = (now shr 32).toByte()
    bytes[2] = (now shr 24).toByte()
    bytes[3] = (now shr 16).toByte()
    bytes[4] = (now shr 8).toByte()
    bytes[5] = now.toByte()
    val out = StringBuilder(26)
    var value = 0L
    var bits = 2
    for (b in bytes) {
        value = (value shl 8) or (b.toLong() and 0xFF)
        bits += 8
        while (bits >= 5) {
            bits -= 5
            out.append(alphabet[((value shr bits) and 31).toInt()])
        }
    }
    if (bits > 0) out.append(alphabet[((value shl (5 - bits)) and 31).toInt()])
    return out.toString()
}

@Synchronized
fun nextHlc(): String {
    val now = System.currentTimeMillis()
    if (now > hlcMs) {
        hlcMs = now
        hlcCounter = 0
    } else {
        hlcCounter++
    }
    return "%016d-%04d-%s".format(hlcMs, hlcCounter, deviceId)
}

fun newSortKey(): String = "%016d-%s".format(System.currentTimeMillis(), newUlid())
