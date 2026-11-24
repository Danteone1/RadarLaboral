package mx.radar.laboral

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

data class CapturedMessage(
    val source: String,
    val sender: String,
    val body: String,
    val priority: String,
    val time: String
)

class WhatsAppCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = supportedSources[sbn.packageName] ?: return
        val extras = sbn.notification.extras ?: return

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: source
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()?.trim().orEmpty()

        if (body.isBlank()) return

        // Los comandos explícitos se convierten en recordatorios dentro de la agenda común.
        val command = ReminderEngine.parse(body)
        if (command != null) {
            ReminderEngine.add(applicationContext, command.task, command.at, command.priority)
        }

        val priority = classify("$sender $body")
        val item = CapturedMessage(
            source = source,
            sender = sender,
            body = body,
            priority = priority,
            time = DateFormat.getDateTimeInstance().format(Date(sbn.postTime))
        )

        // Evita duplicar actualizaciones de una misma notificación.
        val duplicateIndex = history.indexOfFirst {
            it.source == item.source && it.sender == item.sender &&
                it.body == item.body && it.time == item.time
        }
        if (duplicateIndex >= 0) return

        history.add(item)
        while (history.size > 300) history.removeAt(0)
    }

    private fun classify(text: String): String {
        val t = text.lowercase()
        if (listOf("urgente", "inmediatamente", "hoy", "ahora", "asap", "a la brevedad")
                .any { it in t }) return "URGENTE"
        if (listOf("reunión", "reunion", "mañana", "manana", "entrega",
                "pendiente", "fecha límite", "fecha limite", "convocatoria")
                .any { it in t }) return "IMPORTANTE"
        return "INFORMATIVO"
    }

    companion object {
        val history = CopyOnWriteArrayList<CapturedMessage>()

        private val supportedSources = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.gm" to "Gmail",
            "com.microsoft.office.outlook" to "Outlook"
        )
    }
}
