package com.qtmini.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock

class Actions(private val a: Activity) {

    fun tryHandle(text: String): String? {
        val s = text.lowercase().trim()

        if (s.contains("battery")) return battery()
        if (s.contains("storage")) return storage()

        if (s.contains("timer")) {
            val m = Regex("(\\d+)\\s*(sec|second|seconds|min|mins|minute|minutes|ghante|hour|hours|hr)").find(s)
                ?: return "Timer kitne der ka? Jaise: 'timer 5 min' ⏱️"
            val n = m.groupValues[1].toInt()
            val u = m.groupValues[2]
            val secs = when {
                u.startsWith("s") -> n
                u.startsWith("m") -> n * 60
                else -> n * 3600
            }
            return runIntent(Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "QT timer")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Timer set ho gaya: $n ${m.groupValues[2]} ⏱️")
        }

        if (s.contains("alarm")) {
            val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(s)
                ?: return "Alarm kis time ka? Jaise: 'alarm 7:30 am' ⏰"
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            val ap = m.groupValues[3]
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            if (h > 23 || min > 59) return "Ye time sahi nahi lag raha 🤔"
            return runIntent(Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "QT alarm")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                "Alarm set: %02d:%02d ⏰".format(h, min))
        }

        Regex("^(?:open|launch)\\s+(.+)$").find(s)?.let { return openApp(it.groupValues[1].trim()) }
        Regex("^(.+?)\\s+(?:kholo|khol do|open karo|chalao)$").find(s)?.let { return openApp(it.groupValues[1].trim()) }
        return null
    }

    private fun runIntent(i: Intent, ok: String): String = try {
        a.startActivity(i); ok
    } catch (e: Exception) { "Ye nahi ho paya 😕 (${e.javaClass.simpleName})" }

    private fun openApp(name: String): String {
        val pm = a.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val m = pm.queryIntentActivities(q, 0)
            .firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
            ?: return "'$name' naam ka app nahi mila 😕"
        val launch = pm.getLaunchIntentForPackage(m.activityInfo.packageName)
            ?: return "Is app ko khol nahi sakti 😕"
        a.startActivity(launch)
        return "${m.loadLabel(pm)} khol diya ✨"
    }

    private fun battery(): String {
        val bm = a.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chg = if (bm.isCharging) "charging ho rahi hai ⚡" else "charging pe nahi hai"
        return "Battery $pct% hai, $chg 🔋"
    }

    private fun storage(): String {
        val st = StatFs(Environment.getDataDirectory().path)
        val gb = 1024.0 * 1024 * 1024
        return "Storage: %.1f GB free, total %.1f GB 💾".format(st.availableBytes / gb, st.totalBytes / gb)
    }
}
