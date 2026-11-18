package com.lanbing.smsforwarder.utils

import org.junit.Assert.*
import org.junit.Test

class CallEventRouterTest {
    private fun CallEventRouter.event(id: Int?, state: CallState, number: String? = null, now: Long = 100L) =
        onEvent(id, if (id == null) "" else "SIM $id", state, number, now)

    @Test fun callbackThenBothBroadcastOrdersProduceOneIncomingWithNumber() {
        val router = CallEventRouter()
        router.setSubscriptions(setOf(7))
        val first = router.event(7, CallState.RINGING)!!.alert!!
        assertNull(router.event(7, CallState.RINGING, "10086", 150L)!!.alert)
        assertNull(router.event(7, CallState.RINGING, null, 200L)!!.alert)
        assertEquals("10086", router.latest(first.token)!!.alert.number)
        assertEquals(100L, router.latest(first.token)!!.alert.startedAtMillis)
    }

    @Test fun numberedBroadcastThenCallbackAndBlankBroadcastDoNotEraseNumber() {
        val router = CallEventRouter()
        val first = router.event(1, CallState.RINGING, "10086")!!.alert!!
        assertNull(router.event(1, CallState.RINGING)!!.alert)
        assertEquals("10086", router.latest(first.token)!!.alert.number)
    }

    @Test fun unknownSubscriptionBroadcastIsAdoptedByItsSimCallback() {
        val router = CallEventRouter()
        router.setSubscriptions(setOf(1, 2))
        val first = router.event(null, CallState.RINGING, "10086")!!.alert!!
        assertNull(router.event(2, CallState.RINGING, null, 150L)!!.alert)
        assertEquals("SIM 2", router.latest(first.token)!!.sim)
        assertEquals("10086", router.latest(first.token)!!.alert.number)
        assertEquals(first.token, router.event(2, CallState.IDLE, null, 900L)!!.alert!!.token)
    }

    @Test fun initialIdleOfAnotherSimCannotEndAnUnassignedIncomingCall() {
        val router = CallEventRouter()
        router.setSubscriptions(setOf(1, 2))
        val first = router.event(null, CallState.RINGING)!!.alert!!
        assertNull(router.event(1, CallState.IDLE)!!.alert)
        assertNull(router.event(2, CallState.RINGING)!!.alert)
        assertEquals(first.token, router.event(2, CallState.IDLE)!!.alert!!.token)
    }

    @Test fun unassignedBroadcastAfterCallbackUpdatesOnlyActiveSim() {
        val router = CallEventRouter()
        router.setSubscriptions(setOf(1, 2))
        val first = router.event(2, CallState.RINGING)!!.alert!!
        assertNull(router.event(null, CallState.RINGING, "10010")!!.alert)
        assertEquals("10010", router.latest(first.token)!!.alert.number)
        assertEquals("SIM 2", router.latest(first.token)!!.sim)
    }

    @Test fun callbackAndBroadcastIdleGenerateOneMissedAlert() {
        val router = CallEventRouter()
        router.setSubscriptions(setOf(1))
        router.event(1, CallState.RINGING, "10086")
        val missed = router.event(null, CallState.IDLE, null, 900L)!!.alert!!
        assertEquals(CallAlertKind.MISSED, missed.alert.kind)
        assertEquals("10086", missed.alert.number)
        assertNull(router.event(1, CallState.IDLE, null, 950L)!!.alert)
    }

    @Test fun anAnsweredCallDoesNotBecomeMissedWhenTwoSourcesEndIt() {
        val router = CallEventRouter()
        router.event(1, CallState.RINGING)
        router.event(null, CallState.OFFHOOK)
        router.event(1, CallState.OFFHOOK)
        assertNull(router.event(1, CallState.IDLE)!!.alert)
        assertNull(router.event(null, CallState.IDLE)!!.alert)
    }

    @Test fun twoSimCallsHaveSeparateIdentitiesAndResults() {
        val router = CallEventRouter()
        val first = router.event(1, CallState.RINGING, "10086")!!.alert!!
        val second = router.event(2, CallState.RINGING, "10010")!!.alert!!
        assertNotEquals(first.token, second.token)
        router.event(1, CallState.OFFHOOK)
        assertNull(router.event(1, CallState.IDLE)!!.alert)
        assertEquals(second.token, router.event(2, CallState.IDLE)!!.alert!!.token)
    }

    @Test fun ambiguousAggregateIdleCannotMarkBothSimCallsMissed() {
        val router = CallEventRouter()
        router.setSubscriptions(setOf(1, 2))
        router.event(1, CallState.RINGING, "10086")
        router.event(2, CallState.RINGING, "10010")
        assertNull(router.event(null, CallState.IDLE))
        assertNotNull(router.event(1, CallState.IDLE)!!.alert)
        assertNotNull(router.event(2, CallState.IDLE)!!.alert)
    }

    @Test fun sameCallerNextCallGetsAnotherTokenEvenImmediately() {
        val router = CallEventRouter()
        val first = router.event(1, CallState.RINGING, "10086")!!.alert!!
        router.event(1, CallState.IDLE)
        val second = router.event(1, CallState.RINGING, "10086", 200L)!!.alert!!
        assertNotEquals(first.token, second.token)
        assertEquals(200L, second.alert.startedAtMillis)
    }

    @Test fun hiddenNumberStillProducesAnIncomingAlert() {
        val router = CallEventRouter()
        val alert = router.event(1, CallState.RINGING)!!.alert!!.alert
        assertTrue(formatCallAlert(alert, "12:51", "Samsung", "SIM 1").contains("未知号码"))
    }

    @Test fun resetDoesNotReuseOldDelayedAlertIdentity() {
        val router = CallEventRouter()
        val old = router.event(1, CallState.RINGING, "10086")!!.alert!!
        router.reset()
        assertNull(router.latest(old.token))
        assertNull(router.event(1, CallState.IDLE)!!.alert)
        val next = router.event(1, CallState.RINGING, "10086")!!.alert!!
        assertNotEquals(old.token, next.token)
    }

    @Test fun outgoingAndInitialIdleRemainQuietAcrossBothSources() {
        val router = CallEventRouter()
        assertNull(router.event(null, CallState.IDLE)!!.alert)
        assertNull(router.event(1, CallState.OFFHOOK)!!.alert)
        assertNull(router.event(null, CallState.OFFHOOK)!!.alert)
        assertNull(router.event(1, CallState.IDLE)!!.alert)
        assertNull(router.event(null, CallState.IDLE)!!.alert)
    }

    @Test fun numberFirstReceivedAtAnswerEnrichesOriginalCall() {
        val r=CallEventRouter(); val a=r.event(1,CallState.RINGING)!!.alert!!
        r.event(1,CallState.OFFHOOK,"10086",1000)
        assertEquals("10086",r.latest(a.token)!!.alert.number)
        assertNull(r.event(1,CallState.IDLE,null,2000)!!.alert)
    }
    @Test fun numberFirstReceivedAtHangupEnrichesIncomingAndMissed() {
        val r=CallEventRouter();val a=r.event(1,CallState.RINGING)!!.alert!!
        val missed=r.event(1,CallState.IDLE,"10086",1000)!!.alert!!
        assertEquals("10086",missed.alert.number);assertEquals("10086",r.latest(a.token)!!.alert.number)
    }
    @Test fun secondIdleBroadcastCanSupplyNumberAfterSessionEnded() {
        val r=CallEventRouter();val a=r.event(1,CallState.RINGING)!!.alert!!
        r.event(1,CallState.IDLE,null,1000)
        assertNull(r.event(1,CallState.IDLE,"10086",1100)!!.alert)
        assertEquals("10086",r.latest(a.token)!!.alert.number)
    }
    @Test fun answeredCallCanGetNumberFromSecondIdleWithoutBecomingMissed() {
        val r=CallEventRouter();val a=r.event(1,CallState.RINGING)!!.alert!!
        r.event(1,CallState.OFFHOOK,null,500);r.event(1,CallState.IDLE,null,1000)
        assertNull(r.event(1,CallState.IDLE,"10086",1100)!!.alert)
        assertEquals("10086",r.latest(a.token)!!.alert.number)
    }
    @Test fun oldEndedSessionDoesNotAcceptUnlimitedDelayedNumber() {
        val r=CallEventRouter();val a=r.event(1,CallState.RINGING)!!.alert!!
        r.event(1,CallState.IDLE,null,1000);r.event(1,CallState.IDLE,"10086",6001)
        assertEquals("",r.latest(a.token)!!.alert.number)
    }
    @Test fun nextCallDoesNotInheritPreviousNumber() {
        val r=CallEventRouter();r.event(1,CallState.RINGING,"10086");r.event(1,CallState.IDLE,null,1000)
        val next=r.event(1,CallState.RINGING,null,1100)!!.alert!!
        assertEquals("",next.alert.number)
    }
    @Test fun delayedNumberedRingingBroadcastAfterAnswerDoesNotCreateAnotherCall() {
        val r=CallEventRouter();r.setSubscriptions(setOf(1,2));val a=r.event(1,CallState.RINGING)!!.alert!!
        r.event(1,CallState.OFFHOOK,null,500)
        assertNull(r.onEvent(null,"",CallState.RINGING,"10086",600,true)!!.alert)
        assertEquals("10086",r.latest(a.token)!!.alert.number)
        assertNull(r.event(1,CallState.IDLE,null,1000)!!.alert)
    }
    @Test fun distinctNumberRingingAfterAnswerStillCreatesNewCall() {
        val r=CallEventRouter();val a=r.event(1,CallState.RINGING,"10086")!!.alert!!
        r.event(1,CallState.OFFHOOK,null,500)
        val next=r.onEvent(1,"SIM 1",CallState.RINGING,"10010",600,true)!!.alert!!
        assertNotEquals(a.token,next.token)
    }
    @Test fun unassignedLateIdleNumberUpdatesOnlyUniqueEndedSession() {
        val r=CallEventRouter();r.setSubscriptions(setOf(1,2));val a=r.event(2,CallState.RINGING)!!.alert!!
        r.event(2,CallState.IDLE,null,1000);r.event(null,CallState.IDLE,"10086",1100)
        assertEquals("10086",r.latest(a.token)!!.alert.number)
    }
    @Test fun ambiguousTwoEndedCallsAreNotAssignedAnUnknownSimNumber() {
        val r=CallEventRouter();r.setSubscriptions(setOf(1,2));val a=r.event(1,CallState.RINGING)!!.alert!!
        val b=r.event(2,CallState.RINGING)!!.alert!!;r.event(1,CallState.IDLE,null,1000);r.event(2,CallState.IDLE,null,1000)
        r.event(null,CallState.IDLE,"10086",1100)
        assertEquals("",r.latest(a.token)!!.alert.number);assertEquals("",r.latest(b.token)!!.alert.number)
    }
}
