package com.lanbing.smsforwarder.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import com.lanbing.smsforwarder.AppNotificationListenerService
import com.lanbing.smsforwarder.Constants
import org.json.JSONArray
import org.json.JSONObject

data class NotificationAppChoice(val packageName: String, val name: String)

object NotificationSettings {
    fun isAccessGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun forwardingEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(Constants.PREF_ENABLED, false) &&
            prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) &&
            prefs.getBoolean(Constants.PREF_APP_NOTIFICATION_ENABLED, false)

    fun selectedPackages(prefs: SharedPreferences): Set<String> = try {
        val array = JSONArray(prefs.getString(Constants.PREF_APP_NOTIFICATION_PACKAGES, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            if (array.isNull(index)) null else array.optString(index).trim().takeIf { it.isNotEmpty() }
        }.toSet()
    } catch (_: Exception) { emptySet() }

    fun saveSelectedPackages(prefs: SharedPreferences, packages: Set<String>) {
        prefs.edit().putString(Constants.PREF_APP_NOTIFICATION_PACKAGES, JSONArray(packages.sorted()).toString()).apply()
    }

    fun phoneAndSmsPackages(context: Context): Set<String> {
        val packages = mutableSetOf(
            "com.android.mms", "com.android.messaging", "com.google.android.apps.messaging",
            "com.samsung.android.messaging", "com.android.dialer", "com.google.android.dialer",
            "com.samsung.android.dialer", "com.android.phone", "com.android.incallui",
            "com.samsung.android.incallui", "com.android.server.telecom"
        )
        try { Telephony.Sms.getDefaultSmsPackage(context)?.let { packages.add(it) } } catch (_: Exception) { }
        if (Build.VERSION.SDK_INT >= 23) {
            val manager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try { manager?.defaultDialerPackage?.let { packages.add(it) } } catch (_: Exception) { }
            if (Build.VERSION.SDK_INT >= 29) {
                try { manager?.systemDialerPackage?.let { packages.add(it) } } catch (_: Exception) { }
            }
        }
        return packages
    }

    fun requestConnection(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 24 && forwardingEnabled(prefs) && isAccessGranted(context)) {
            try { NotificationListenerService.requestRebind(ComponentName(context, AppNotificationListenerService::class.java)) } catch (_: Exception) { }
        }
    }

    fun appName(context: Context, packageName: String): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: Exception) { packageName }

    fun rememberApp(prefs: SharedPreferences, packageName: String, name: String) {
        val known = try { JSONObject(prefs.getString(Constants.PREF_APP_NOTIFICATION_KNOWN_APPS, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        if (known.optString(packageName) == name) return
        if (known.length() >= 150 && !known.has(packageName)) known.remove(known.keys().next())
        known.put(packageName, name)
        prefs.edit().putString(Constants.PREF_APP_NOTIFICATION_KNOWN_APPS, known.toString()).apply()
    }

    @Suppress("DEPRECATION")
    fun availableApps(context: Context, prefs: SharedPreferences): List<NotificationAppChoice> {
        val apps = linkedMapOf<String, String>()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        try {
            context.packageManager.queryIntentActivities(launcher, 0).forEach { activity ->
                val packageName = activity.activityInfo.packageName
                apps[packageName] = activity.activityInfo.applicationInfo.loadLabel(context.packageManager).toString()
            }
        } catch (_: Exception) { }
        try {
            val known = JSONObject(prefs.getString(Constants.PREF_APP_NOTIFICATION_KNOWN_APPS, "{}") ?: "{}")
            known.keys().forEach { packageName -> if (packageName !in apps) apps[packageName] = known.optString(packageName, packageName) }
        } catch (_: Exception) { }
        selectedPackages(prefs).forEach { packageName -> if (packageName !in apps) apps[packageName] = appName(context, packageName) }
        return apps.filterKeys { it != context.packageName }.map { NotificationAppChoice(it.key, it.value) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
