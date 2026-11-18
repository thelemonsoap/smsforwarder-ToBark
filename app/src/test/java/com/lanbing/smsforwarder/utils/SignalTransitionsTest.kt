package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test

class SignalTransitionsTest {
    @Test fun lteVariants() { for (o in 0..2) assertEquals(MobileSignal.LTE, classifyMobileSignal(13, o)) }
    @Test fun nsaVariants() { for (o in 3..5) assertEquals(MobileSignal.FIVE_G, classifyMobileSignal(13, o)) }
    @Test fun standalone() { assertEquals(MobileSignal.FIVE_G, classifyMobileSignal(20)) }
    @Test fun unsupported() { for (n in listOf(0, 1, 2, 3, 8, 15)) assertNull(classifyMobileSignal(n, 5)) }
    @Test fun initialWaitsFiveMinutes() {
        val t = SignalTransitionTracker(); t.observe(MobileSignal.LTE, 1_000)
        assertNull(t.poll(300_999)); assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(301_000))
        assertNull(t.poll(400_000))
    }
    @Test fun repeatedCallbackDoesNotPostpone() {
        val t = SignalTransitionTracker(); t.observe(MobileSignal.LTE, 0); t.observe(MobileSignal.LTE, 200_000)
        assertEquals(300_000L, t.deadline()); assertNotNull(t.poll(300_000))
    }
    @Test fun initialOffThenTransition() {
        val t = SignalTransitionTracker(announceInitial = false); t.observe(MobileSignal.LTE, 0)
        assertFalse(t.poll(300_000)!!.notify); t.observe(MobileSignal.FIVE_G, 400_000)
        assertTrue(t.poll(700_000)!!.notify)
    }
    @Test fun transitionsBothWays() {
        val t = SignalTransitionTracker(MobileSignal.LTE); t.observe(MobileSignal.FIVE_G, 0)
        assertEquals(SignalDecision(MobileSignal.FIVE_G, true), t.poll(300_000))
        t.observe(MobileSignal.LTE, 400_000)
        assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(700_000))
    }
    @Test fun briefFlutterIsIgnored() {
        val t = SignalTransitionTracker(MobileSignal.LTE); t.observe(MobileSignal.FIVE_G, 0)
        t.observe(MobileSignal.LTE, 200_000); assertNull(t.poll(300_000)); assertEquals(MobileSignal.LTE, t.stable)
        assertNull(t.deadline())
    }
    @Test fun unknownAtDeadlineSuppressesAndRecoveryDoesNotRepeat() {
        val t = SignalTransitionTracker(MobileSignal.LTE); t.observe(MobileSignal.FIVE_G, 0)
        t.observe(null, 200_000); assertNull(t.poll(300_000)); assertEquals(MobileSignal.LTE, t.stable)
        t.observe(MobileSignal.LTE, 400_000); assertNull(t.deadline()); assertNull(t.poll(700_000))
    }
    @Test fun cachedStableAvoidsRestartDuplicate() {
        val t = SignalTransitionTracker(MobileSignal.FIVE_G); t.observe(MobileSignal.FIVE_G, 0)
        assertNull(t.poll(300_000)); assertNull(t.deadline())
    }
    @Test fun fiveGBrandVariantsDoNotDuplicate() {
        val t = SignalTransitionTracker(MobileSignal.FIVE_G)
        for (o in 3..5) t.observe(classifyMobileSignal(13, o), o.toLong())
        assertNull(t.poll(300_000))
    }
    @Test fun initialCandidateUsesLatestLabelWithoutRestartingWindow() {
        val t = SignalTransitionTracker(); t.observe(MobileSignal.LTE, 0); t.observe(MobileSignal.FIVE_G, 200_000)
        assertEquals(300_000L, t.deadline()); assertEquals(SignalDecision(MobileSignal.FIVE_G, true), t.poll(300_000))
    }
    @Test fun exactPayload() {
        assertEquals("【信号提醒】现在使用LTE", formatSignalAlert(MobileSignal.LTE))
        assertEquals("【信号提醒】现在使用5G\n数据卡：SIM 2", formatSignalAlert(MobileSignal.FIVE_G, "SIM 2"))
    }
    @Test fun requestedLteFiveGLteFiveGLteExampleDoesNotNotify() {
        val t = SignalTransitionTracker(MobileSignal.LTE)
        t.observe(MobileSignal.FIVE_G, 10_000); t.observe(MobileSignal.LTE, 80_000)
        t.observe(MobileSignal.FIVE_G, 180_000); t.observe(MobileSignal.LTE, 290_000)
        assertEquals(310_000L, t.deadline()); assertNull(t.poll(310_000)); assertEquals(MobileSignal.LTE, t.stable)
    }
    @Test fun requestedFiveGLteFiveGLteExampleNotifiesOnce() {
        val t = SignalTransitionTracker(MobileSignal.FIVE_G)
        t.observe(MobileSignal.LTE, 10_000); t.observe(MobileSignal.FIVE_G, 120_000); t.observe(MobileSignal.LTE, 290_000)
        assertEquals(310_000L, t.deadline()); assertNull(t.poll(309_999))
        assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(310_000))
        assertNull(t.poll(600_009)); t.observe(MobileSignal.LTE, 700_000); assertNull(t.deadline())
    }
    @Test fun returnToBaselineDoesNotCancelOrRestartWindow() {
        val t = SignalTransitionTracker(MobileSignal.LTE)
        t.observe(MobileSignal.FIVE_G, 0); t.observe(MobileSignal.LTE, 100_000)
        assertEquals(300_000L, t.deadline()); t.observe(MobileSignal.FIVE_G, 299_990)
        assertEquals(SignalDecision(MobileSignal.FIVE_G, true), t.poll(300_000))
    }
    @Test fun baselineIsNotReplacedByIntermediateSamples() {
        val t = SignalTransitionTracker(MobileSignal.FIVE_G)
        t.observe(MobileSignal.LTE, 0); assertEquals(MobileSignal.FIVE_G, t.stable)
        t.observe(MobileSignal.FIVE_G, 100_000); t.observe(MobileSignal.LTE, 200_000)
        assertEquals(MobileSignal.FIVE_G, t.stable)
        assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(300_000))
    }
    @Test fun newWindowStartsAfterSuppressedWindow() {
        val t = SignalTransitionTracker(MobileSignal.LTE)
        t.observe(MobileSignal.FIVE_G, 0); t.observe(MobileSignal.LTE, 200_000); assertNull(t.poll(300_000))
        t.observe(MobileSignal.FIVE_G, 400_000); assertEquals(700_000L, t.deadline())
        assertNull(t.poll(699_999)); assertEquals(SignalDecision(MobileSignal.FIVE_G, true), t.poll(700_000))
    }
    @Test fun reverseTransitionGetsItsOwnWindowAfterNotification() {
        val t = SignalTransitionTracker(MobileSignal.LTE); t.observe(MobileSignal.FIVE_G, 0)
        assertNotNull(t.poll(300_000)); t.observe(MobileSignal.LTE, 300_010)
        assertEquals(600_010L, t.deadline()); assertNull(t.poll(600_009))
        assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(600_010))
    }
    @Test fun temporaryUnknownDoesNotExtendWindow() {
        val t = SignalTransitionTracker(MobileSignal.LTE); t.observe(MobileSignal.FIVE_G, 0)
        t.observe(null, 100_000); t.observe(MobileSignal.FIVE_G, 250_000)
        assertEquals(300_000L, t.deadline()); assertEquals(SignalDecision(MobileSignal.FIVE_G, true), t.poll(300_000))
    }
    @Test fun unknownBaselineDoesNotStartUntilRecognizedNetwork() {
        val t = SignalTransitionTracker(); t.observe(null, 0); assertNull(t.deadline())
        t.observe(MobileSignal.FIVE_G, 50_000); assertEquals(350_000L, t.deadline())
        assertEquals(SignalDecision(MobileSignal.FIVE_G, true), t.poll(350_000))
    }
    @Test fun lateTimerEmitsOnlyCurrentDifferenceOnce() {
        val t = SignalTransitionTracker(MobileSignal.FIVE_G); t.observe(MobileSignal.LTE, 0)
        assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(1_200_000)); assertNull(t.poll(1_210_000))
    }
    @Test fun callQuietRebaseDiscardsWholeFluctuationWindow() {
        val t = SignalTransitionTracker(MobileSignal.FIVE_G); t.observe(MobileSignal.LTE, 0)
        t.rebase(MobileSignal.LTE); assertNull(t.deadline()); assertNull(t.poll(300_000))
        t.rebase(MobileSignal.FIVE_G); t.observe(MobileSignal.FIVE_G, 600_000)
        assertNull(t.deadline()); t.observe(MobileSignal.LTE, 700_000)
        assertEquals(1_000_000L, t.deadline()); assertEquals(SignalDecision(MobileSignal.LTE, true), t.poll(1_000_000))
    }
}
