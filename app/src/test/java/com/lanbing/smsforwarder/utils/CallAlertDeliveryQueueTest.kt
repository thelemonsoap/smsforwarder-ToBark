package com.lanbing.smsforwarder.utils
import org.junit.Assert.*
import org.junit.Test
class CallAlertDeliveryQueueTest {
 private fun alert(token:Long=1,number:String="",kind:CallAlertKind=CallAlertKind.INCOMING)=RoutedCallAlert(token,CallAlert(kind,number,100),"SIM 1")
 @Test fun hiddenCallerIsDeliveredAfterThreeSeconds() { val q=CallAlertDeliveryQueue();q.enqueue(alert(),0);assertTrue(q.poll(2999){alert()}.isEmpty());assertEquals(1,q.poll(3000){alert()}.size);assertFalse(q.hasPending());assertTrue(q.poll(4000){alert()}.isEmpty()) }
 @Test fun knownCallerIsImmediate() { val q=CallAlertDeliveryQueue();q.enqueue(alert(number="10086"),0);assertEquals("10086",q.poll(0){null}.single().routed.alert.number) }
 @Test fun numberArrivingAfterOldHalfSecondWindowIsUsed() { val q=CallAlertDeliveryQueue();q.enqueue(alert(),0);assertTrue(q.poll(500){alert()}.isEmpty());assertEquals("10086",q.poll(1500){alert(number="10086")}.single().routed.alert.number) }
 @Test fun answerNumberTriggersEarlyDelivery() { val r=CallEventRouter();val q=CallAlertDeliveryQueue();val a=r.onEvent(1,"SIM 1",CallState.RINGING,null,100)!!.alert!!;q.enqueue(a,0);r.onEvent(1,"SIM 1",CallState.OFFHOOK,"10086",1200);assertEquals("10086",q.poll(1100,r::latest).single().routed.alert.number) }
 @Test fun secondHangupNumberIsMergedIntoBothPendingAlerts() { val r=CallEventRouter();val q=CallAlertDeliveryQueue();val a=r.onEvent(1,"SIM 1",CallState.RINGING,null,100)!!.alert!!;q.enqueue(a,0);val m=r.onEvent(1,"SIM 1",CallState.IDLE,null,1000)!!.alert!!;q.enqueue(m,900);r.onEvent(1,"SIM 1",CallState.IDLE,"10086",1100);val deliveries=q.poll(1000,r::latest);assertEquals(2,deliveries.size);assertTrue(deliveries.all{it.routed.alert.number=="10086"});assertEquals(CallAlertKind.MISSED,deliveries.last().routed.alert.kind) }
 @Test fun lateNumberGetsOnlyOneExplicitSupplement() { val q=CallAlertDeliveryQueue();q.enqueue(alert(),0);q.poll(3000){alert()};val d=q.poll(5000){alert(number="10086")}.single();assertTrue(d.numberSupplement);assertTrue(q.poll(6000){alert(number="10086")}.isEmpty()) }
 @Test fun initiallyKnownNumberDoesNotGetSupplement() { val q=CallAlertDeliveryQueue();q.enqueue(alert(number="10086"),0);q.poll(0){null};assertTrue(q.poll(1000){alert(number="10086")}.isEmpty()) }
 @Test fun numberedMissedReminderMakesSupplementUnnecessary() { val q=CallAlertDeliveryQueue();q.enqueue(alert(),0);q.poll(3000){alert()};q.enqueue(alert(kind=CallAlertKind.MISSED),4000);val d=q.poll(4500){alert(number="10086")};assertEquals(1,d.size);assertEquals(CallAlertKind.MISSED,d.single().routed.alert.kind);assertFalse(d.single().numberSupplement) }
 @Test fun supplementExpiresAfterThirtySeconds() { val q=CallAlertDeliveryQueue();q.enqueue(alert(),0);q.poll(3000){alert()};assertTrue(q.poll(33001){alert(number="10086")}.isEmpty()) }
 @Test fun duplicateEnqueueDoesNotExtendDeadline() { val q=CallAlertDeliveryQueue();q.enqueue(alert(),0);q.enqueue(alert(),2000);assertEquals(1,q.poll(3000){alert()}.size);q.enqueue(alert(),3100);assertTrue(q.poll(3200){alert()}.isEmpty()) }
 @Test fun distinctTokensKeepSameCallerSeparate() { val q=CallAlertDeliveryQueue();q.enqueue(alert(1,"10086"),0);q.enqueue(alert(2,"10086"),0);assertEquals(2,q.poll(0){null}.size) }
 @Test fun clearCancelsOldPendingAlertsAndSupplements() { val q=CallAlertDeliveryQueue();q.enqueue(alert(),0);q.clear();assertFalse(q.hasPending());assertTrue(q.poll(10000){alert(number="10086")}.isEmpty()) }
 @Test fun supplementIsExplicitAndKeepsOriginalTime() { val text=formatCallNumberSupplement(alert(number="10086").alert,"12:51","SIM 1");assertTrue(text.contains("【来电号码补充】"));assertTrue(text.contains("并非新来电"));assertTrue(text.contains("10086"));assertTrue(text.contains("12:51")) }
}
