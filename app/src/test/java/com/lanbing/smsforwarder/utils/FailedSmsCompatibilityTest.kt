package com.lanbing.smsforwarder.utils

import com.lanbing.smsforwarder.SmsReceiver.Companion.FailedMessage
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class FailedSmsCompatibilityTest {
    private fun message() = FailedMessage("bark", "Bark", "WEBHOOK", "https://example.invalid", "sender", "短信正文",
        "device", true, false, System.currentTimeMillis(), messageTemplate = "{content}", matchedKeyword = "短信")

    @Test fun oldQueueFormatRetainsMessageAndTemplateAcrossUpgrade() {
        val original = message()
        val restored = FailedMessage.fromJSONObject(JSONObject(original.toJSONObject().toString()))
        assertEquals(original, restored)
    }
    @Test fun legacyMissingOptionalFieldsStillLoad() {
        val json = message().toJSONObject()
        listOf("receiverPhoneNumber", "showSenderPhone", "highlightVerificationCode", "retryCount", "errorMessage", "messageTemplate", "matchedKeyword").forEach { json.remove(it) }
        val restored = FailedMessage.fromJSONObject(json)
        assertEquals("短信正文", restored.content)
        assertNull(restored.messageTemplate)
        assertEquals(0, restored.retryCount)
        assertTrue(restored.isRetryable())
    }
    @Test fun retryCountsKeepOriginalAbsoluteSchedule() {
        val original = message().copy(timestamp = System.currentTimeMillis() - 70_000L)
        assertTrue(original.isReadyForRetry())
        assertTrue(original.copy(retryCount = 1).isReadyForRetry())
        assertFalse(original.copy(retryCount = 2).isReadyForRetry())
    }
    @Test fun fourthFailureIsTerminal() {
        assertTrue(message().copy(retryCount = 3).isRetryable())
        assertFalse(message().copy(retryCount = 4).isRetryable())
        assertTrue(message().copy(retryCount = 4).shouldDiscard())
    }
}
