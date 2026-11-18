package com.lanbing.smsforwarder.utils

import java.util.Locale

/** One pass: braces inside a notification/SMS value are data, never another template. */
object TemplateTokens {
    private val token = Regex("\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}|\\{([A-Za-z][A-Za-z0-9_]*)\\}")
    fun names(template: String): Set<String> = token.findAll(template)
        .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toSet()
    fun render(template: String, values: Map<String, String>): String {
        val normalized = values.mapKeys { it.key.lowercase(Locale.ROOT) }
        return token.replace(template) { match ->
            val name = match.groupValues[1].ifEmpty { match.groupValues[2] }.lowercase(Locale.ROOT)
            normalized[name] ?: match.value
        }
    }
}
