package mx.radar.laboral

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

data class CapturedMessage(val sender: String, val body: String, val priority: String, val time: String)

class WhatsAppCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val extras = sbn.notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Remitente no disponible"
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (body.isBlank()) return
        val priority = classify(body)
        history.add(CapturedMessage(sender, body, priority, DateFormat.getDateTimeInstance().format(Date(sbn.postTime))))
        while (history.size > 300) history.removeAt(0)
    }

    private fun classify(text: String): String {
        val t = text.lowercase()
        if (listOf("urgente", "inmediatamente", "hoy", "ahora", "asap").any { it in t }) return "URGENTE"
        if (listOf("reunión", "reunion", "mañana", "manana", "entrega", "pendiente", "fecha límite").any { it in t }) return "IMPORTANTE"
        return "INFORMATIVO"
    }

    companion object { val history = CopyOnWriteArrayList<CapturedMessage>() }
}