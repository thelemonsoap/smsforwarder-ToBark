package com.lanbing.smsforwarder.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A targeted on-demand local lookup. No address-book scan, persistent cache or observer. */
object ContactNameResolver {
    private val timeout = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }

    fun resolve(context: Context, number: String): String? {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.PREF_CONTACT_NAME_ENABLED, false) ||
            !prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false) ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
            Looper.myLooper() == Looper.getMainLooper()) return null
        val normalized = ContactIdentity.normalizeNumber(number) ?: return null
        val signal = CancellationSignal()
        val deadline = timeout.schedule({ signal.cancel() }, 1L, TimeUnit.SECONDS)
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalized))
            val candidates = mutableListOf<Pair<String, String?>>()
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null, signal)?.use { cursor ->
                val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.NUMBER)
                val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (candidates.size >= 100) return null
                    candidates += cursor.getString(numberColumn).orEmpty() to cursor.getString(nameColumn)
                }
            }
            // Permission or toggle can change while the provider query is running.
            if (!prefs.getBoolean(Constants.PREF_CONTACT_NAME_ENABLED, false) ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) null
            else ContactIdentity.chooseName(number, candidates)
        } catch (_: Exception) {
            null // Denied/revoked permission, unavailable provider or timeout must never block forwarding.
        } finally { deadline.cancel(false) }
    }
}
