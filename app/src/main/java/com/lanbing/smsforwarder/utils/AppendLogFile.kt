package com.lanbing.smsforwarder.utils

import java.io.File
import java.io.IOException

/** Append normally; compact only after a bounded surplus. Legacy newest-first files migrate once. */
class AppendLogFile(private val file: File, private val limit: Int, private val surplus: Int = 50) {
    private val header = "#smsforwarder-append-log-v1"
    private var count: Int? = null

    private fun initialize() {
        if (count != null) return
        val lines = if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
        if (lines.firstOrNull() == header) {
            count = lines.drop(1).count { it.isNotBlank() }
        } else {
            replace(lines.filter { it.isNotBlank() }.take(limit).reversed())
        }
    }

    private fun replace(lines: List<String>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.append(header).append('\n')
            lines.forEach { writer.append(it).append('\n') }
        }
        if (!temp.renameTo(file)) throw IOException("Cannot replace log file")
        count = lines.size
    }

    @Synchronized fun append(line: String) {
        initialize()
        // One append instead of copying the entire log for every event.
        file.appendText(line + "\n", Charsets.UTF_8)
        count = count!! + line.lineSequence().count { it.isNotBlank() }
        if (count!! > limit + surplus) replace(file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.takeLast(limit))
    }

    @Synchronized fun readAll(): List<String> {
        initialize()
        return file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.takeLast(limit).reversed()
    }

    @Synchronized fun clear() { replace(emptyList()) }
    @Synchronized fun latest(): String? = readAll().firstOrNull()
}
