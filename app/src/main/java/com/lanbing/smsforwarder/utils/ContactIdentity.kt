package com.lanbing.smsforwarder.utils

/** Display identity is separate from the raw address used for matching and deduplication. */
object ContactIdentity {
    fun normalizeNumber(value: String): String? {
        val raw = value.trim()
        if (raw in setOf("-1", "-2", "-3") || raw.any { !it.isDigit() && it !in "+-(). \u00a0\t" }) return null
        var number = buildString {
            raw.forEach { c -> if (c.isDigit()) append(Character.digit(c, 10)) else if (c == '+') append(c) }
        }
        if (number.startsWith("00")) number = "+" + number.drop(2)
        if (number.count { it == '+' } > 1 || ('+' in number && !number.startsWith('+'))) return null
        return number.takeIf { it.count(Char::isDigit) >= 3 }
    }

    private fun nationalNumber(number: String): String = when {
        number.startsWith("+86") && number.length == 14 && number[3] == '1' -> number.drop(3)
        number.startsWith("86") && number.length == 13 && number[2] == '1' -> number.drop(2)
        number.startsWith("+1") && number.length == 12 -> number.drop(2)
        else -> number
    }

    fun numbersMatch(incoming: String, stored: String): Boolean {
        val a = normalizeNumber(incoming) ?: return false
        val b = normalizeNumber(stored) ?: return false
        return a == b || a.removePrefix("+") == b.removePrefix("+") || nationalNumber(a) == nationalNumber(b)
    }

    fun cleanName(name: String?): String? = name?.map { if (it.isISOControl()) ' ' else it }
        ?.joinToString("")?.trim()?.take(120)?.takeIf { it.isNotBlank() }

    fun label(number: String, name: String?): String = cleanName(name) ?: number.ifBlank { "未知号码" }

    fun chooseName(incoming: String, candidates: List<Pair<String, String?>>): String? {
        val names = candidates.filter { numbersMatch(incoming, it.first) }.mapNotNull { cleanName(it.second) }.distinct()
        return names.singleOrNull() // Ambiguous contacts fall back to the number.
    }
}
