package mx.radar.laboral

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Tienes un recordatorio pendiente"
        val priority = intent.getStringExtra("priority") ?: "NORMAL"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("radar_reminders", "Recordatorios Radar", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = NotificationCompat.Builder(context, "radar_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Radar Laboral · $priority")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(text.hashCode(), notification)
        val pending = goAsync()
        Thread {
            var tts: TextToSpeech? = null
            try {
                val latch = java.util.concurrent.CountDownLatch(1)
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale("es", "MX")
                        tts?.speak("Recordatorio $priority. $text", TextToSpeech.QUEUE_FLUSH, null, "radar-${text.hashCode()}")
                    }
                    latch.countDown()
                }
                latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                Thread.sleep(5000)
            } finally {
                tts?.shutdown()
                pending.finish()
            }
        }.start()
    }
}
