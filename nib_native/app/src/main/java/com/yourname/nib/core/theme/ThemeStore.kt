package com.yourname.nib.core.theme

import android.content.Context

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("nib_theme", Context.MODE_PRIVATE)

    fun load(): ThemeConfig = ThemeConfig(
        styleId = prefs.getString("style", "v1") ?: "v1",
        colorKey = prefs.getString("color", "indigo") ?: "indigo",
        dark = prefs.getBoolean("dark", true),
        tuning = decodeTuning(prefs.getString("tuning", "") ?: ""),
    )

    fun save(config: ThemeConfig) {
        prefs.edit()
            .putString("style", config.styleId)
            .putString("color", config.colorKey)
            .putBoolean("dark", config.dark)
            .putString("tuning", encodeTuning(config.tuning))
            .apply()
    }

    private fun encodeTuning(tuning: Map<String, StyleTuning>): String = tuning.entries
        .mapNotNull { (id, v) ->
            val style = themeStyles.firstOrNull { it.id == id } ?: return@mapNotNull null
            if (v == StyleTuning.of(style)) null else "$id:${v.glow},${v.radius},${v.grain},${v.tilt},${v.density}"
        }
        .joinToString("|")

    private fun decodeTuning(raw: String): Map<String, StyleTuning> {
        if (raw.isBlank()) return emptyMap()
        val ids = themeStyles.associateBy { it.id }
        return raw.split("|").mapNotNull { chunk ->
            val parts = chunk.split(":")
            if (parts.size != 2) return@mapNotNull null
            val style = ids[parts[0]] ?: return@mapNotNull null
            val nums = parts[1].split(",").mapNotNull { it.toIntOrNull() }
            if (nums.size != 5) return@mapNotNull null
            val value = StyleTuning(nums[0], nums[1], nums[2], nums[3], nums[4])
            if (value == StyleTuning.of(style)) null else parts[0] to value
        }.toMap()
    }
}
