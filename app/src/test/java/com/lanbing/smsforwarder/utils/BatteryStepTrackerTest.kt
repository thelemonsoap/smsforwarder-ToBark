package com.lanbing.smsforwarder.utils
import org.junit.Assert.*
import org.junit.Test
class BatteryStepTrackerTest {
 @Test fun firstReadingOnlySeedsBaseline() { val t=BatteryStepTracker();assertTrue(t.observe(60).isEmpty());assertEquals(60,t.previous) }
 @Test fun chargingHitsAllNineMilestones() { val t=BatteryStepTracker(0);val events=(1..100).flatMap{t.observe(it)};assertEquals((10..90 step 10).toList(),events.map{it.threshold});assertTrue(events.all{it.ascending}) }
 @Test fun dischargingHitsAllNineMilestones() { val t=BatteryStepTracker(100);val events=(99 downTo 0).flatMap{t.observe(it)};assertEquals((90 downTo 10 step 10).toList(),events.map{it.threshold});assertTrue(events.none{it.ascending}) }
 @Test fun repeatedBroadcastAtThresholdIsQuiet() { val t=BatteryStepTracker(51);assertEquals(1,t.observe(50).size);repeat(10){assertTrue(t.observe(50).isEmpty())} }
 @Test fun ordinaryPercentChangesAreQuiet() { val t=BatteryStepTracker(55);assertTrue(t.observe(54).isEmpty());assertTrue(t.observe(53).isEmpty()) }
 @Test fun skippedDescendingThresholdIsNotLost() { val t=BatteryStepTracker(52);assertEquals(listOf(BatteryStep(50,false)),t.observe(48)) }
 @Test fun skippedAscendingThresholdIsNotLost() { val t=BatteryStepTracker(48);assertEquals(listOf(BatteryStep(50,true)),t.observe(52)) }
 @Test fun skippedMultipleMilestonesHaveCorrectOrder() { val t=BatteryStepTracker(55);assertEquals(listOf(50,40,30),t.observe(25).map{it.threshold});assertEquals(listOf(30,40,50,60,70),t.observe(75).map{it.threshold}) }
 @Test fun fullAndEmptyDoNotAddZeroOrHundredAlert() { val t=BatteryStepTracker(99);assertTrue(t.observe(100).isEmpty());val z=BatteryStepTracker(1);assertTrue(z.observe(0).isEmpty()) }
 @Test fun invalidReadingsDoNotChangeBaseline() { val t=BatteryStepTracker(51);assertTrue(t.observe(-1).isEmpty());assertTrue(t.observe(101).isEmpty());assertEquals(51,t.previous);assertEquals(50,t.observe(50).single().threshold) }
 @Test fun sameDirectionJitterDoesNotSpam() { val t=BatteryStepTracker(51);t.observe(50);assertTrue(t.observe(51).isEmpty());assertTrue(t.observe(50).isEmpty()) }
 @Test fun oppositeDirectionCanRemindAtSameThreshold() { val t=BatteryStepTracker(51);t.observe(50);t.observe(49);assertEquals(listOf(BatteryStep(50,true)),t.observe(50));t.observe(51);assertEquals(listOf(BatteryStep(50,false)),t.observe(50)) }
 @Test fun neighboringMilestoneRearmsPreviousOne() { val t=BatteryStepTracker(51);t.observe(50);t.observe(40);t.observe(51);assertEquals(listOf(BatteryStep(50,false)),t.observe(50)) }
 @Test fun savedStateAvoidsRestartDuplicate() { val t=BatteryStepTracker(51);t.observe(50);val restart=BatteryStepTracker(t.previous,t.lastThreshold,t.lastDirection);assertTrue(restart.observe(50).isEmpty());restart.observe(51);assertTrue(restart.observe(50).isEmpty());assertEquals(40,restart.observe(40).single().threshold) }
 @Test fun templateKeepsExistingHeaderAndDevice() { val m=formatBatteryStepReminder(50,BatteryStep(50,true),true,"SIM1 13800000000");assertTrue(m.startsWith("【电量提醒】当前电量：50%"));assertTrue(m.contains("充电中"));assertTrue(m.contains("\n设备：SIM1 13800000000")) }
 @Test fun dischargeTemplateUsesActualReadingForSkippedThreshold() { val m=formatBatteryStepReminder(48,BatteryStep(50,false),false);assertTrue(m.contains("当前电量：48%"));assertTrue(m.contains("，使用，"));assertFalse(m.contains("放电"));assertTrue(m.contains("经过50%档位"));assertFalse(m.contains("当前电量：50%")) }
 @Test fun combinedThresholdNoteIsPreserved() { val m=formatBatteryStepReminder(20,BatteryStep(20,false),false,"SIM 1","电量较低，请及时充电");assertTrue(m.contains("电量较低，请及时充电"));assertEquals(1,Regex("【电量提醒】").findAll(m).count()) }
}
