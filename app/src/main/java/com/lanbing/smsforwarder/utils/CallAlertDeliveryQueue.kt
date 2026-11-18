package com.lanbing.smsforwarder.utils

/** Framework-free delivery policy, keyed by call identity and alert type. */
data class CallDelivery(val routed: RoutedCallAlert, val numberSupplement: Boolean = false)
class CallAlertDeliveryQueue(private val waitMillis: Long = 3_000L, private val supplementMillis: Long = 30_000L) {
    private data class Key(val token: Long, val kind: CallAlertKind)
    private data class Pending(val routed: RoutedCallAlert, val deadline: Long)
    private data class Sent(val routed: RoutedCallAlert, val at: Long)
    private val pending = linkedMapOf<Key, Pending>()
    private val sent = linkedMapOf<Key, Sent>()
    private val supplemented = mutableSetOf<Long>()

    fun enqueue(routed: RoutedCallAlert, now: Long) {
        val key = Key(routed.token, routed.alert.kind)
        if (key !in sent && key !in pending) {
            if (pending.size >= 64) pending.remove(pending.keys.first())
            pending[key] = Pending(routed, now + if (routed.alert.number.isBlank()) waitMillis else 0L)
        }
    }
    fun hasPending() = pending.isNotEmpty()
    fun poll(now: Long, latest: (Long) -> RoutedCallAlert?): List<CallDelivery> {
        val result = mutableListOf<CallDelivery>()
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val (key, item) = iterator.next()
            val snapshot = latest(key.token)
            val number = snapshot?.alert?.number?.takeIf { it.isNotBlank() } ?: item.routed.alert.number
            if (number.isBlank() && now < item.deadline) continue
            val routed = item.routed.copy(alert = item.routed.alert.copy(number = number),
                sim = snapshot?.sim?.takeIf { it.isNotBlank() } ?: item.routed.sim)
            result += CallDelivery(routed)
            sent[key] = Sent(routed, now)
            iterator.remove()
        }
        // An already-sent Bark notification cannot be edited. At most one explicit
        // supplement, only if no normal alert has already supplied this call's number.
        for ((token, alerts) in sent.values.groupBy { it.routed.token }) {
            if (token in supplemented || pending.keys.any { it.token == token } ||
                alerts.any { it.routed.alert.number.isNotBlank() }) continue
            val previous = alerts.maxByOrNull { it.at } ?: continue
            if (now - previous.at !in 0L..supplementMillis) continue
            val snapshot = latest(token)?.takeIf { it.alert.number.isNotBlank() } ?: continue
            result += CallDelivery(snapshot.copy(alert = snapshot.alert.copy(kind = previous.routed.alert.kind)), true)
            supplemented += token
        }
        val expired = sent.filterValues { now - it.at > supplementMillis }.keys
        expired.forEach { sent.remove(it) }
        while (sent.size > 64) sent.remove(sent.keys.first())
        supplemented.retainAll(sent.keys.map { it.token }.toSet())
        return result
    }
    fun clear() { pending.clear(); sent.clear(); supplemented.clear() }
}

fun formatCallNumberSupplement(alert: CallAlert, time: String, sim: String, contactName: String? = null): String = buildString {
    val name = ContactIdentity.cleanName(contactName)
    append("【${if (name == null) "来电号码补充" else "来电信息补充"}】\n${if (name == null) "来电号码" else "来电人"}：${ContactIdentity.label(alert.number, name)}\n来电时间：$time")
    if (sim.isNotBlank()) append("\n接收卡：$sim")
    append("\n系统稍后提供了此次来电号码，并非新来电。")
}
