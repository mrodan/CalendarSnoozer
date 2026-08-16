package com.calendareventsnooze.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * The calendar apps the app knows about — one registry shared by the three
 * places that used to keep their own copies: which notifications get
 * intercepted, which packages "Open Calendar Event" may launch, and what the
 * Settings picker offers.
 *
 * Round 20 — the previous list had three entries (Google, AOSP, Samsung), so on
 * a Xiaomi or a Huawei the listener ignored every calendar notification and the
 * app appeared completely dead while reporting every permission granted. That
 * was the most likely failure for anyone not on a Pixel.
 */
object CalendarApps {

    /**
     * Packages known to post calendar reminders. Anything here that is also
     * installed shows up in the Settings picker.
     *
     * Only packages listed in the manifest's `<queries>` block are visible to
     * `getLaunchIntentForPackage` on Android 11+, so adding a package here
     * means adding it there too.
     */
    /**
     * Apps whose **only** notifications are calendar reminders. Safe to watch
     * without asking, because intercepting one of their notifications can only
     * ever mean an event is due.
     */
    private val DEDICATED = linkedMapOf(
        "com.google.android.calendar"          to "Google Calendar",
        "com.android.calendar"                 to "Calendar",
        "com.samsung.android.calendar"         to "Samsung Calendar",
        "com.miui.calendar"                    to "Mi Calendar",
        "com.xiaomi.calendar"                  to "Xiaomi Calendar",
        "com.huawei.calendar"                  to "Huawei Calendar",
        "com.coloros.calendar"                 to "ColorOS Calendar",
        "com.oplus.calendar"                   to "OPPO Calendar",
        "com.oneplus.calendar"                 to "OnePlus Calendar",
        "com.vivo.calendar"                    to "vivo Calendar",
        "com.bbk.calendar"                     to "vivo Calendar",
        "com.transsion.calendar"               to "Calendar",
        "com.asus.calendar"                    to "ASUS Calendar",
        "com.lge.calendar"                     to "LG Calendar",
        "com.sonyericsson.organizer"           to "Sony Calendar",
        "com.motorola.calendar"                to "Motorola Calendar",
        "com.appgenix.bizcal"                  to "Business Calendar",
        "com.underwood.calendar_beta"          to "Today Calendar",
        "ws.xsoh.etar"                         to "Etar"
    )

    /**
     * Apps that carry a calendar but post plenty of other notifications too.
     *
     * These are offered in the picker and are **never** on by default: the
     * listener intercepts *every* notification from a watched package, so
     * ticking Outlook means its mail lands as a full-screen alarm too. Ticking
     * one is a deliberate choice behind a confirmation.
     *
     * Pure mail clients are deliberately absent. Gmail was briefly listed here
     * and should not have been: Gmail posts mail, while the reminders for
     * "events from Gmail" are posted by Google Calendar.
     */
    private val MIXED_USE = linkedMapOf(
        "com.microsoft.office.outlook"         to "Outlook",
        "com.anydo"                            to "Any.do"
    )

    /** Everything offerable, dedicated calendars first. */
    val KNOWN: Map<String, String> = DEDICATED + MIXED_USE

    /** True when watching this package can only ever mean "an event is due". */
    fun isDedicatedCalendar(packageName: String) = packageName in DEDICATED

    /**
     * The set watched on a fresh install: every dedicated calendar app present.
     * Mixed-use apps are excluded on purpose — see [MIXED_USE].
     */
    fun defaultsFor(context: Context): Set<String> {
        val pm = context.packageManager
        val installed = DEDICATED.keys.filter { isInstalled(pm, it) }.toSet()
        // Nothing recognised — fall back to Google Calendar so the app still has
        // a sensible target if the user later installs it.
        return installed.ifEmpty { setOf("com.google.android.calendar") }
    }

    data class Entry(val packageName: String, val label: String)

    /**
     * Known calendar apps that are actually installed, plus anything the system
     * itself reports as a calendar app, so an OEM package we have never heard of
     * still turns up in the picker.
     */
    fun installedKnown(context: Context): List<Entry> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, String>()

        KNOWN.forEach { (pkg, label) ->
            if (isInstalled(pm, pkg)) found[pkg] = label
        }

        // CATEGORY_APP_CALENDAR is how an app declares "I am a calendar", which
        // catches OEM and third-party apps missing from KNOWN. Covered by the
        // manifest's <queries> intent entry.
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            pm.queryIntentActivities(intent, 0).forEach { info ->
                val pkg = info.activityInfo?.packageName ?: return@forEach
                if (!found.containsKey(pkg)) {
                    found[pkg] = runCatching {
                        info.loadLabel(pm).toString()
                    }.getOrDefault(pkg)
                }
            }
        }

        return found.map { (pkg, label) -> Entry(pkg, label) }
    }

    /** A display name for a package that may no longer be installed. */
    fun labelFor(context: Context, packageName: String): String {
        runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            return context.packageManager.getApplicationLabel(info).toString()
        }
        return KNOWN[packageName] ?: packageName
    }

    fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching { pm.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)
}
