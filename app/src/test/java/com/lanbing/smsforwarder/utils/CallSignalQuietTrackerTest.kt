package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test

class CallSignalQuietTrackerTest {
    @Test fun initiallyNotMuted() { assertFalse(CallSignalQuietTracker().muted(0)) }
    @Test fun initialIdleDoesNotMute() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.IDLE, false, 100)
        assertFalse(t.muted(100)); assertEquals(0L, t.quietUntil)
    }
    @Test fun ringingImmediatelyMutes() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.RINGING, false, 10)
        assertTrue(t.muted(10)); assertTrue(t.muted(100_000))
    }
    @Test fun answeredCallStaysMutedUntilThirtySecondsAfterHangup() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.RINGING, false, 10)
        t.observe(1, CallState.OFFHOOK, false, 100); t.observe(1, CallState.IDLE, false, 1_000)
        assertFalse(t.busy); assertTrue(t.muted(30_999)); assertFalse(t.muted(31_000))
    }
    @Test fun missedCallAlsoGetsCooldown() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.RINGING, false, 0)
        t.observe(1, CallState.IDLE, false, 200)
        assertTrue(t.muted(30_199)); assertFalse(t.muted(30_200))
    }
    @Test fun outgoingCallAlsoMutes() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.OFFHOOK, false, 0)
        assertTrue(t.muted(10)); t.observe(1, CallState.IDLE, false, 50)
        assertEquals(30_050L, t.quietUntil)
    }
    @Test fun duplicateIdleDoesNotRestartDeadline() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.RINGING, false, 0)
        t.observe(1, CallState.IDLE, false, 100); t.observe(1, CallState.IDLE, false, 20_000)
        t.observe(-1, CallState.IDLE, true, 25_000)
        assertEquals(30_100L, t.quietUntil); assertFalse(t.muted(30_100))
    }
    @Test fun otherSimInitialIdleCannotEndActiveCall() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.OFFHOOK, false, 0)
        t.observe(2, CallState.IDLE, false, 100); assertTrue(t.busy)
        assertTrue(t.muted(100_000))
    }
    @Test fun dualSimWaitsForLastCall() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.OFFHOOK, false, 0)
        t.observe(2, CallState.RINGING, false, 10); t.observe(1, CallState.IDLE, false, 100)
        assertTrue(t.busy); t.observe(2, CallState.IDLE, false, 200)
        assertEquals(30_200L, t.quietUntil)
    }
    @Test fun newCallDuringGraceRestartsFromItsEnd() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.RINGING, false, 0)
        t.observe(1, CallState.IDLE, false, 100); t.observe(2, CallState.RINGING, false, 20_000)
        assertTrue(t.muted(100_000)); t.observe(2, CallState.IDLE, false, 100_000)
        assertTrue(t.muted(129_999)); assertFalse(t.muted(130_000))
    }
    @Test fun broadcastOnlyStillWorks() {
        val t = CallSignalQuietTracker(); t.observe(-1, CallState.RINGING, true, 0)
        t.observe(-1, CallState.OFFHOOK, true, 10); assertTrue(t.muted(50))
        t.observe(-1, CallState.IDLE, true, 100); assertEquals(30_100L, t.quietUntil)
    }
    @Test fun callbackAdoptsEarlierBroadcast() {
        val t = CallSignalQuietTracker(); t.observe(-1, CallState.RINGING, true, 0)
        t.observe(2, CallState.RINGING, false, 10); t.observe(2, CallState.IDLE, false, 100)
        assertFalse(t.busy); assertEquals(30_100L, t.quietUntil)
    }
    @Test fun genericIdleDoesNotClearOtherSimCallback() {
        val t = CallSignalQuietTracker(); t.observe(2, CallState.OFFHOOK, false, 0)
        t.observe(-1, CallState.IDLE, true, 100); assertTrue(t.busy)
    }
    @Test fun genericRingingDoesNotCreateDuplicateCall() {
        val t = CallSignalQuietTracker(); t.observe(2, CallState.OFFHOOK, false, 0)
        t.observe(-1, CallState.RINGING, true, 100); t.observe(2, CallState.IDLE, false, 200)
        assertFalse(t.busy); assertEquals(30_200L, t.quietUntil)
    }
    @Test fun matchingCallbackIdleClearsScopedBroadcast() {
        val t = CallSignalQuietTracker(); t.observe(2, CallState.RINGING, true, 0)
        t.observe(2, CallState.IDLE, false, 100); assertFalse(t.busy)
        assertEquals(30_100L, t.quietUntil)
    }
    @Test fun removedSimDoesNotStickBusy() {
        val t = CallSignalQuietTracker(); t.observe(2, CallState.OFFHOOK, false, 0)
        t.retainSubscriptions(setOf(1), 100); assertFalse(t.busy)
        assertEquals(30_100L, t.quietUntil)
    }
    @Test fun removingOtherSimDoesNotEndCall() {
        val t = CallSignalQuietTracker(); t.observe(1, CallState.OFFHOOK, false, 0)
        t.retainSubscriptions(setOf(1), 100); assertTrue(t.busy)
    }
    @Test fun restoredGraceIsBoundedAndDoesNotDependOnWallClock() {
        val t = CallSignalQuietTracker(); t.restore(100_000, 500)
        assertTrue(t.muted(30_499)); assertFalse(t.muted(30_500))
        t.restore(-1, 40_000); assertFalse(t.muted(40_000))
    }
    @Test fun noReplayAfterLteAndFiveGDuringCall() {
        val q = CallSignalQuietTracker(); val t = SignalTransitionTracker(MobileSignal.FIVE_G)
        q.observe(1, CallState.RINGING, false, 0)
        t.observe(MobileSignal.LTE, 0) // already pending when ringing arrives
        t.rebase(MobileSignal.LTE); assertNull(t.deadline())
        q.observe(1, CallState.IDLE, false, 10_000)
        assertTrue(q.muted(39_999)); t.rebase(MobileSignal.FIVE_G)
        assertFalse(q.muted(40_000)); t.rebase(t.stable)
        t.observe(MobileSignal.FIVE_G, 40_000); assertNull(t.poll(340_000))
        t.observe(MobileSignal.LTE, 350_000)
        assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(650_000))
    }
    @Test fun firstNetworkDuringCallIsSilentDespiteInitialOption() {
        val t = SignalTransitionTracker(announceInitial = true)
        t.rebase(null); t.observe(MobileSignal.LTE, 0)
        assertEquals(SignalDecision(MobileSignal.LTE, false), t.poll(300_000))
    }
    @Test fun rebaseCancelsPendingEvenWhenNetworkUnknown() {
        val t = SignalTransitionTracker(MobileSignal.FIVE_G)
        t.observe(MobileSignal.LTE, 0); t.rebase(null)
        assertNull(t.deadline()); assertNull(t.poll(100_000)); assertEquals(MobileSignal.FIVE_G, t.stable)
    }
    @Test fun mixedBroadcastScopesDoNotKeepPhantomCall() {
        for (unknownFirst in listOf(true, false)) {
            val t = CallSignalQuietTracker()
            t.observe(if (unknownFirst) -1 else 2, CallState.RINGING, true, 0)
            t.observe(if (unknownFirst) 2 else -1, CallState.RINGING, true, 10)
            t.observe(2, CallState.IDLE, true, 100)
            assertFalse(t.busy); assertEquals(30_100L, t.quietUntil)
        }
    }

}
