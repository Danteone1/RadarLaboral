package mx.radar.laboral

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Reminder(
    val id: String,
    val text: String,
    val triggerAt: Long,
    val priority: String,
    val done: Boolean = false
)

object ReminderEngine {
    private const val PREFS = "radar_reminders"
    private const val KEY = "items"

    fun all(context: Context): List<Reminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                Reminder(o.getString("id"), o.getString("text"), o.getLong("at"), o.optString("priority", "NORMAL"), o.optBoolean("done"))
            }.getOrNull()
        }.sortedBy { it.triggerAt }
    }

    private fun save(context: Context, list: List<Reminder>) {
        val array = JSONArray()
        list.forEach { r ->
            array.put(JSONObject().put("id", r.id).put("text", r.text).put("at", r.triggerAt).put("priority", r.priority).put("done", r.done))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun add(context: Context, text: String, at: Long, priority: String = "NORMAL"): Reminder {
        val r = Reminder(UUID.randomUUID().toString(), text, at, priority)
        save(context, all(context) + r)
        schedule(context, r)
        return r
    }

    fun markDone(context: Context, id: String) {
        save(context, all(context).map { if (it.id == id) it.copy(done = true) else it })
        cancel(context, id)
    }

    fun schedule(context: Context, reminder: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).putExtra("id", reminder.id).putExtra("text", reminder.text).putExtra("priority", reminder.priority)
        val pi = PendingIntent.getBroadcast(context, reminder.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (android.os.Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
        else am.set(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
    }

    private fun cancel(context: Context, id: String) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
    }

    data class Parsed(val task: String, val at: Long, val priority: String)

    fun parse(command: String, now: Long = System.currentTimeMillis()): Parsed? {
        val normalized = command.trim()
        if (!normalized.contains(Regex("\\b(av[ií]same|recu[eé]rdame)\\b", RegexOption.IGNORE_CASE))) return null
        val timeMatch = Regex("\\b(?:a las|a la)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(de la mañana|de la tarde|de la noche|am|pm)?", RegexOption.IGNORE_CASE).find(normalized) ?: return null
        var hour = timeMatch.groupValues[1].toIntOrNull() ?: return null
        val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val suffix = timeMatch.groupValues[3].lowercase(Locale.ROOT)
        if (suffix.contains("tarde") || suffix.contains("noche") || suffix == "pm") { if (hour in 1..11) hour += 12 }
        if (suffix.contains("mañana") || suffix == "am") { if (hour == 12) hour = 0 }
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val lower = normalized.lowercase(Locale.ROOT)
        val datePrefix = lower.substring(0, timeMatch.range.first)
        val tomorrow = Regex("\\bmañana\\b|\\bmanana\\b").containsMatchIn(datePrefix)
        if (tomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (cal.timeInMillis <= now && !tomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)
        val task = normalized.substring(timeMatch.range.last + 1).trim().trimStart(',', ':', ' ')
            .replace(Regex("^(debo|tengo que)\\s+", RegexOption.IGNORE_CASE), "")
            .ifBlank { normalized.substringAfter(timeMatch.value).trim() }
        if (task.isBlank()) return null
        val priority = if (lower.contains("urgente")) "URGENTE" else if (lower.contains("importante")) "IMPORTANTE" else "NORMAL"
        return Parsed(task, cal.timeInMillis, priority)
    }

    fun format(time: Long): String = SimpleDateFormat("EEE d MMM, HH:mm", Locale("es", "MX")).format(Date(time))
}
