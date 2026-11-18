package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test

class CallSessionTrackerTest {
    @Test fun repeatedRingingProducesOneIncomingAlert() {
        val tracker = CallSessionTracker()
        val first = tracker.onState(CallState.RINGING, "10086", 100L)!!
        assertEquals(CallAlertKind.INCOMING, first.kind)
        assertEquals("10086", first.number)
        assertNull(tracker.onState(CallState.RINGING, "10086", 200L))
        assertNull(tracker.onState(CallState.RINGING, null, 300L))
    }

    @Test fun unansweredCallProducesOneMissedAlertWithOriginalTimeAndNumber() {
        val tracker = CallSessionTracker()
        tracker.onState(CallState.RINGING, " 10086 ", 100L)
        val missed = tracker.onState(CallState.IDLE, "", 1000L)!!
        assertEquals(CallAlertKind.MISSED, missed.kind)
        assertEquals("10086", missed.number)
        assertEquals(100L, missed.startedAtMillis)
        assertNull(tracker.onState(CallState.IDLE, "", 2000L))
    }

    @Test fun answeredCallDoesNotProduceMissedAlert() {
        val tracker = CallSessionTracker()
        tracker.onState(CallState.RINGING, "10086", 100L)
        assertNull(tracker.onState(CallState.OFFHOOK, null, 200L))
        assertNull(tracker.onState(CallState.IDLE, null, 500L))
    }

    @Test fun outgoingCallAndInitialIdleAreIgnored() {
        val tracker = CallSessionTracker()
        assertNull(tracker.onState(CallState.IDLE, null, 0L))
        assertNull(tracker.onState(CallState.OFFHOOK, "10086", 100L))
        assertNull(tracker.onState(CallState.IDLE, "10086", 500L))
    }

    @Test fun nextCallFromSameNumberIsNotSuppressed() {
        val tracker = CallSessionTracker()
        tracker.onState(CallState.RINGING, "10086", 100L)
        tracker.onState(CallState.IDLE, null, 200L)
        val second = tracker.onState(CallState.RINGING, "10086", 300L)!!
        assertEquals(CallAlertKind.INCOMING, second.kind)
        assertEquals(300L, second.startedAtMillis)
    }

    @Test fun laterNumberUpdatesSessionWithoutDuplicateIncomingAlert() {
        val tracker = CallSessionTracker()
        tracker.onState(CallState.RINGING, null, 100L)
        assertNull(tracker.onState(CallState.RINGING, "10086", 200L))
        assertEquals("10086", tracker.onState(CallState.IDLE, null, 300L)!!.number)
    }

    @Test fun twoSimSessionsStayIndependent() {
        val sim1 = CallSessionTracker()
        val sim2 = CallSessionTracker()
        assertNotNull(sim1.onState(CallState.RINGING, "10086", 100L))
        assertNotNull(sim2.onState(CallState.RINGING, "10010", 100L))
        sim1.onState(CallState.OFFHOOK, null, 200L)
        assertNull(sim1.onState(CallState.IDLE, null, 300L))
        assertEquals("10010", sim2.onState(CallState.IDLE, null, 300L)!!.number)
    }

    @Test fun resetDoesNotCarryAnOldCallIntoANewMonitoringSession() {
        val tracker = CallSessionTracker()
        tracker.onState(CallState.RINGING, "10086", 100L)
        tracker.reset()
        assertNull(tracker.onState(CallState.IDLE, null, 300L))
    }

    @Test fun missingPermissionOrHiddenCallerStillProducesUsefulText() {
        val message = formatCallAlert(CallAlert(CallAlertKind.INCOMING, "", 100L), "2026-10-03 12:00:00", "Phone", "SIM 2")
        assertTrue(message.contains("【来电提醒】"))
        assertTrue(message.contains("未知号码"))
        assertTrue(message.contains("SIM 2"))
        assertTrue(message.contains("2026-10-03 12:00:00"))
    }

    @Test fun missedTextDoesNotClaimToDistinguishRejection() {
        val message = formatCallAlert(CallAlert(CallAlertKind.MISSED, "10086", 100L), "12:00", "", "")
        assertTrue(message.contains("未接／拒接提醒"))
        assertTrue(message.contains("10086"))
        assertFalse(message.contains("设备："))
    }
}
