package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class PowerOptimizationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun emptyQueueNeverSchedulesHeartbeat() {
        assertNull(RetryWakePolicy.delay(emptyList(), true))
    }
    @Test fun offlineQueueWaitsForNetworkInsteadOfPolling() {
        assertNull(RetryWakePolicy.delay(listOf(-1L, 30_000L), false))
    }
    @Test fun earliestMessageDeterminesDeadline() {
        assertEquals(30_000L, RetryWakePolicy.delay(listOf(120_000L, 30_000L, 60_000L), true))
    }
    @Test fun overdueMessagesCannotCreateBusyRetryLoop() {
        assertEquals(10_000L, RetryWakePolicy.delay(listOf(-300_000L), true))
    }
    @Test fun networkRecoveryRestoresPendingDeadline() {
        assertNull(RetryWakePolicy.delay(listOf(60_000L), false))
        assertEquals(60_000L, RetryWakePolicy.delay(listOf(60_000L), true))
    }
    @Test fun legacyLogsPreserveOrderAcrossUpgradeAndRestart() {
        val file = folder.newFile()
        file.writeText("newest\nmiddle\noldest\n")
        val log = AppendLogFile(file, 200)
        assertEquals(listOf("newest", "middle", "oldest"), log.readAll())
        log.append("current")
        assertEquals(listOf("current", "newest", "middle", "oldest"), AppendLogFile(file, 200).readAll())
    }
    @Test fun regularAppendKeepsExistingFilePrefix() {
        val file = folder.newFile()
        val log = AppendLogFile(file, 200)
        log.append("first")
        val prefix = file.readText()
        log.append("第二条")
        assertEquals(prefix + "第二条\n", file.readText())
        assertEquals("第二条", log.latest())
    }
    @Test fun rotationKeepsMostRecentEntriesAndBoundedFile() {
        val file = folder.newFile()
        val log = AppendLogFile(file, 3, 2)
        (1..20).forEach { log.append("event$it") }
        assertEquals(listOf("event20", "event19", "event18"), log.readAll())
        assertTrue(file.readLines().size <= 6)
        assertEquals(log.readAll(), AppendLogFile(file, 3, 2).readAll())
    }
    @Test fun clearAndRestartDoNotRestoreOldLogs() {
        val file = folder.newFile()
        val log = AppendLogFile(file, 200)
        log.append("old")
        log.clear()
        assertNull(AppendLogFile(file, 200).latest())
        log.append("new")
        assertEquals(listOf("new"), log.readAll())
    }
    @Test fun concurrentWritersKeepEveryEntry() {
        val log = AppendLogFile(folder.newFile(), 200)
        val workers = (1..4).map { n -> Thread { (1..25).forEach { log.append("$n-$it") } }.apply { start() } }
        workers.forEach { it.join() }
        assertEquals(100, log.readAll().toSet().size)
    }
    @Test fun irrelevantNotificationRemovalDoesNotChangeDedupState() {
        val cache = NotificationDeduplicator()
        assertFalse(cache.forget("system-not-forwarded"))
    }
}
