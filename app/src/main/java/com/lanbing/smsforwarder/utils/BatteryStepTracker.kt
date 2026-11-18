package com.lanbing.smsforwarder.utils

data class BatteryStep(val threshold: Int, val ascending: Boolean)
/** Compare real successive readings. Duplicate broadcasts and same-direction jitter stay quiet. */
class BatteryStepTracker(previous: Int? = null, lastThreshold: Int = -1, lastDirection: Int = 0) {
    var previous: Int? = previous?.takeIf { it in 0..100 }; private set
    var lastThreshold = lastThreshold; private set
    var lastDirection = lastDirection; private set
    fun observe(percent: Int): List<BatteryStep> {
        if (percent !in 0..100) return emptyList()
        val old = previous
        previous = percent
        if (old == null || old == percent) return emptyList()
        val ascending = percent > old
        val direction = if (ascending) 1 else -1
        val crossed = (10..90 step 10).filter {
            if (ascending) old < it && it <= percent else percent <= it && it < old
        }.let { if (ascending) it else it.reversed() }
        return crossed.mapNotNull {
            if (lastThreshold == it && lastDirection == direction) null else {
                lastThreshold = it; lastDirection = direction; BatteryStep(it, ascending)
            }
        }
    }
}

fun formatBatteryStepReminder(percent: Int, step: BatteryStep, charging: Boolean, phoneInfo: String = "", thresholdNote: String = ""): String = buildString {
    append("【电量提醒】当前电量：${percent}%，${if (charging) "充电中" else "使用"}")
    if (percent == step.threshold) append("，电量${if (step.ascending) "上升" else "下降"}至${step.threshold}%")
    else append("，电量${if (step.ascending) "上升" else "下降"}经过${step.threshold}%档位")
    if (thresholdNote.isNotBlank()) append("，$thresholdNote")
    if (phoneInfo.isNotBlank()) append("\n设备：$phoneInfo")
}
