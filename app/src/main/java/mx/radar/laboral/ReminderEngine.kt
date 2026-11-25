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
    private const val PROFILE = "radar_profile"
    private const val ALIASES = "aliases"
    private const val TRUSTED = "trusted_senders"

    fun aliases(context: Context): List<String> {
        val raw = context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE)
            .getString(ALIASES, "Jers,Jersaín,Jersain") ?: "Jers,Jersaín,Jersain"
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    fun saveAliases(context: Context, value: String) {
        context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE).edit().putString(ALIASES, value).apply()
    }

    fun trustedSenders(context: Context): List<String> =
        (context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE).getString(TRUSTED, "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }

    fun saveTrustedSenders(context: Context, value: String) {
        context.getSharedPreferences(PROFILE, Context.MODE_PRIVATE).edit().putString(TRUSTED, value).apply()
    }

    fun isAddressedToUser(context: Context, sender: String, body: String): Boolean {
        val aliases = aliases(context)
        val trusted = trustedSenders(context)
        val senderMatch = (aliases + trusted).any { it.equals(sender.trim(), true) }
        val bodyMatch = aliases.any { alias ->
            Regex("(?i)(^|[^\\p{L}])" + Regex.escape(alias) + "([^\\p{L}]|$)").containsMatchIn(body)
        }
        return senderMatch || bodyMatch
    }

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
        val intent = Intent(context, ReminderAlarmReceiver::class.java).putExtra("id", reminder.id)
            .putExtra("text", reminder.text).putExtra("priority", reminder.priority)
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
        val raw = command.trim().replace(Regex("\\s+"), " ")
        if (!Regex("(?i)\\b(av[ií]same|recu[eé]rdame|no olvides|pendiente)\\b").containsMatchIn(raw)) return null
        val lower = raw.lowercase(Locale("es", "MX"))
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        var timeStart = -1
        var timeEnd = -1

        val relative = Regex("(?i)\\b(?:en|dentro de)\\s+(\\d+)\\s*(minutos?|mins?|horas?|hrs?|d[ií]as?)\\b").find(raw)
        val relativeOne = Regex("(?i)\\b(?:en|dentro de)\\s+(un|una)\\s+(minuto|hora|d[ií]a)\\b").find(raw)
        if (relative != null || relativeOne != null) {
            val match = relative ?: relativeOne!!
            val amount = if (relative != null) relative.groupValues[1].toLong() else 1L
            val unit = match.groupValues.last().lowercase(Locale("es", "MX"))
            when {
                unit.startsWith("min") -> cal.add(Calendar.MINUTE, amount)
                unit.startsWith("hor") -> cal.add(Calendar.HOUR_OF_DAY, amount)
                else -> cal.add(Calendar.DAY_OF_YEAR, amount)
            }
            timeStart = match.range.first
            timeEnd = match.range.last + 1
        } else {
            val clock = Regex("(?i)\\b(?:a las|a la)\\s*(\\d{1,2})(?::(\\d{1,2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?|de la mañana|de la tarde|de la noche)?\\b").find(raw)
                ?: Regex("(?i)\\b(\\d{1,2}):(\\d{2})\\s*(a\\.?m\\.?|p\\.?m\\.?)?\\b").find(raw)
                ?: return null
            var hour = clock.groupValues[1].toIntOrNull() ?: return null
            val minute = clock.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) return null
            val suffix = clock.groupValues.getOrElse(3) { "" }.lowercase(Locale("es", "MX")).replace(".", "").trim()
            when {
                suffix == "pm" || suffix.contains("tarde") || suffix.contains("noche") -> if (hour in 1..11) hour += 12
                suffix == "am" || suffix.contains("mañana") -> if (hour == 12) hour = 0
            }
            if (suffix.isBlank() && hour in 1..12) {
                // Ambiguous 1–12 hour clock: do not silently guess morning or evening.
                return null
            }
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            timeStart = clock.range.first
            timeEnd = clock.range.last + 1
        }

        val tomorrow = Regex("(?i)\\bmañana\\b|\\bmanana\\b").containsMatchIn(raw)
        val today = Regex("(?i)\\bhoy\\b").containsMatchIn(raw)
        val dayAfter = Regex("(?i)\\bpasado mañana\\b|\\bpasado manana\\b").containsMatchIn(raw)
        if (dayAfter) cal.add(Calendar.DAY_OF_YEAR, 2)
        else if (tomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)
        else if (today && cal.timeInMillis <= now) return null
        else if (relative == null && relativeOne == null && cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)

        val priority = when {
            Regex("(?i)\\b(urgente|urgencia|inmediatamente|asap)\\b").containsMatchIn(raw) -> "URGENTE"
            Regex("(?i)\\b(importante|prioritario|prioridad alta)\\b").containsMatchIn(raw) -> "IMPORTANTE"
            else -> "NORMAL"
        }

        var task = raw.removeRange(timeStart, timeEnd).trim(' ', ',', '.', ':', ';', '-')
        task = task.replace(Regex("(?i)\\b(av[ií]same|recu[eé]rdame|no olvides|pendiente)\\b"), "")
            .replace(Regex("(?i)\\b(hoy|mañana|manana|pasado)\\b"), "")
            .replace(Regex("(?i)\\b(debo|tengo que|que|para|el día|el dia)\\b"), "")
            .replace(Regex("(?i)\\b(urgente|urgencia|inmediatamente|asap|importante|prioritario|prioridad alta)\\b"), "")
            .trim(' ', ',', '.', ':', ';', '-')
        if (task.isBlank()) return null
        return Parsed(task, cal.timeInMillis, priority)
    }

    fun format(time: Long): String = SimpleDateFormat("EEE d MMM, HH:mm", Locale("es", "MX")).format(Date(time))
}
