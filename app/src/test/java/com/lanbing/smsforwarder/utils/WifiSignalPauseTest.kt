package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test

class WifiSignalPauseTest {
    @Test fun noConnectedNetworkMeansWifiToggleAloneDoesNotPause() {
        val wifi = WifiPresenceTracker<String>()
        assertFalse(wifi.connected)
        assertTrue(SignalWifiRules.mayDeliver(wifi.connected, wifi.epoch, 0))
    }

    @Test fun firstWifiConnectionImmediatelyBlocksSignalDelivery() {
        val wifi = WifiPresenceTracker<String>()
        assertTrue(wifi.available("wifi"))
        assertFalse(SignalWifiRules.mayDeliver(wifi.connected, wifi.epoch, wifi.epoch))
    }

    @Test fun duplicateCallbacksDoNotRepeatedlyPauseOrRebase() {
        val wifi = WifiPresenceTracker<String>()
        wifi.available("wifi")
        val epoch = wifi.epoch
        assertFalse(wifi.available("wifi"))
        assertFalse(wifi.capabilities("wifi", true))
        assertEquals(epoch, wifi.epoch)
    }

    @Test fun losingOneOfTwoWifiNetworksMustNotResumeCellularDetection() {
        val wifi = WifiPresenceTracker<String>()
        wifi.available("wifiA"); wifi.available("wifiB")
        val epoch = wifi.epoch
        assertFalse(wifi.lost("wifiA"))
        assertTrue(wifi.connected)
        assertEquals(epoch, wifi.epoch)
        assertTrue(wifi.lost("wifiB"))
        assertFalse(wifi.connected)
    }

    @Test fun unrelatedNetworkLossDoesNotCreateFalseResume() {
        val wifi = WifiPresenceTracker<String>()
        wifi.available("wifi")
        assertFalse(wifi.lost("cellular"))
        assertTrue(wifi.connected)
    }

    @Test fun vpnOverCellularDoesNotCountAsWifi() {
        val wifi = WifiPresenceTracker<String>()
        assertFalse(wifi.capabilities("vpn", false))
        assertFalse(wifi.connected)
    }

    @Test fun vpnChangingToWifiAndBackUpdatesPauseState() {
        val wifi = WifiPresenceTracker<String>()
        assertTrue(wifi.capabilities("vpn", true))
        assertTrue(wifi.connected)
        assertTrue(wifi.capabilities("vpn", false))
        assertFalse(wifi.connected)
    }

    @Test fun startupSnapshotAlreadyOnWifiBlocksPendingSends() {
        val wifi = WifiPresenceTracker<String>()
        wifi.replace(setOf("wifi"))
        assertTrue(wifi.connected)
        assertFalse(SignalWifiRules.mayDeliver(wifi.connected, wifi.epoch, 0))
    }

    @Test fun briefWifiConnectionInvalidatesOldSendEvenAfterDisconnect() {
        val wifi = WifiPresenceTracker<String>()
        val queuedEpoch = wifi.epoch
        wifi.available("wifi"); wifi.lost("wifi")
        assertFalse(wifi.connected)
        assertFalse(SignalWifiRules.mayDeliver(wifi.connected, wifi.epoch, queuedEpoch))
        assertTrue(SignalWifiRules.mayDeliver(wifi.connected, wifi.epoch, wifi.epoch))
    }

    @Test fun resumingOnDifferentSignalSilentlyReplacesPreWifiBaseline() {
        val tracker = SignalWifiRules.tracker(MobileSignal.LTE, true, true)
        tracker.observe(MobileSignal.FIVE_G, 0)
        assertNull(tracker.poll(299_999))
        val baseline = tracker.poll(300_000)!!
        assertEquals(MobileSignal.FIVE_G, baseline.signal)
        assertFalse(baseline.notify)
    }

    @Test fun nsaInitialLteCallbackCannotCauseAFakePostWifi5gAlert() {
        val tracker = SignalWifiRules.tracker(MobileSignal.LTE, true, true)
        tracker.observe(MobileSignal.LTE, 0)
        tracker.observe(MobileSignal.FIVE_G, 10)
        val baseline = tracker.poll(300_000)!!
        assertEquals(MobileSignal.FIVE_G, baseline.signal)
        assertFalse(baseline.notify)
    }

    @Test fun subsequentChangesStillUseFullFiveMinuteWindow() {
        val tracker = SignalWifiRules.tracker(MobileSignal.LTE, true, true)
        tracker.observe(MobileSignal.FIVE_G, 0); tracker.poll(300_000)
        tracker.observe(MobileSignal.LTE, 300_001)
        assertNull(tracker.poll(600_000))
        val decision = tracker.poll(600_001)!!
        assertTrue(decision.notify)
        assertEquals(MobileSignal.LTE, decision.signal)
    }

    @Test fun unknownNetworkDoesNotFinishResumeBaselineOrSendAnything() {
        val tracker = SignalWifiRules.tracker(MobileSignal.LTE, true, true)
        tracker.observe(null, 0)
        assertNull(tracker.deadline())
        tracker.observe(MobileSignal.FIVE_G, 1)
        tracker.observe(null, 2)
        assertNull(tracker.poll(300_001))
        tracker.observe(MobileSignal.LTE, 300_002)
        assertFalse(tracker.poll(600_002)!!.notify)
    }

    @Test fun savedWifiResumeFlagIsSafeAfterProcessRestart() {
        val beforeRestart = SignalWifiRules.tracker(MobileSignal.LTE, true, true)
        beforeRestart.observe(MobileSignal.FIVE_G, 100)
        val restored = SignalWifiRules.tracker(MobileSignal.LTE, true, true)
        restored.observe(MobileSignal.FIVE_G, 0)
        assertFalse(restored.poll(300_000)!!.notify)
    }

    @Test fun callQuietRebaseStillSuppressesAnyDeferredSignalAlert() {
        val tracker = SignalWifiRules.tracker(MobileSignal.LTE, true, true)
        tracker.observe(MobileSignal.FIVE_G, 0)
        tracker.rebase(MobileSignal.FIVE_G)
        assertNull(tracker.deadline())
        assertNull(tracker.poll(300_000))
        tracker.observe(MobileSignal.LTE, 300_001)
        assertTrue(tracker.poll(600_001)!!.notify)
    }

    @Test fun cellularOnlyStartupRetainsOriginalInitialReminderPreference() {
        val announce = SignalWifiRules.tracker(null, true, false)
        announce.observe(MobileSignal.FIVE_G, 0)
        assertTrue(announce.poll(300_000)!!.notify)
        val silent = SignalWifiRules.tracker(null, false, false)
        silent.observe(MobileSignal.LTE, 0)
        assertFalse(silent.poll(300_000)!!.notify)
    }
}
