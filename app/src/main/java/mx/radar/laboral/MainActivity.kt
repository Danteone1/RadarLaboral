package mx.radar.laboral

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
        setContent {
            MaterialTheme {
                var entries by remember { mutableStateOf(WhatsAppCaptureService.history.toList()) }
                var reminders by remember { mutableStateOf(ReminderEngine.all(this@MainActivity)) }
                var command by remember { mutableStateOf("") }
                var feedback by remember { mutableStateOf("Escribe: Avísame a las 10 de la mañana que debo comprar el pan") }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("RADAR LABORAL", style = MaterialTheme.typography.headlineMedium)
                    Text("Una agenda única para tus recordatorios y avisos")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Escribe o dicta un recordatorio") },
                        placeholder = { Text("Avísame a las 10 de la mañana que debo comprar el pan") },
                        minLines = 2
                    )
                    Button(
                        onClick = {
                            val parsed = ReminderEngine.parse(command)
                            if (parsed == null) {
                                feedback = "No reconocí el comando. Usa «Avísame a las 10:00 que ...»."
                            } else {
                                val saved = ReminderEngine.add(this@MainActivity, parsed.task, parsed.at, parsed.priority)
                                reminders = ReminderEngine.all(this@MainActivity)
                                command = ""
                                feedback = "Agendado: ${saved.text} · ${ReminderEngine.format(saved.triggerAt)} · ${saved.priority}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = command.isNotBlank()
                    ) { Text("Crear recordatorio") }
                    Text(feedback, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Activar acceso a notificaciones") }
                    OutlinedButton(
                        onClick = {
                            entries = WhatsAppCaptureService.history.toList()
                            reminders = ReminderEngine.all(this@MainActivity)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Actualizar") }
                    Spacer(Modifier.height(12.dp))
                    Text("AGENDA · ${reminders.count { !it.done }} pendientes", style = MaterialTheme.typography.titleLarge)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reminders.filter { !it.done }) { reminder ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(reminder.priority, style = MaterialTheme.typography.labelSmall)
                                    Text(reminder.text, style = MaterialTheme.typography.titleMedium)
                                    Text(ReminderEngine.format(reminder.triggerAt), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = {
                                        ReminderEngine.markDone(this@MainActivity, reminder.id)
                                        reminders = ReminderEngine.all(this@MainActivity)
                                    }) { Text("Atendido") }
                                }
                            }
                        }
                        item { Text("AVISOS CAPTURADOS · ${entries.size}", style = MaterialTheme.typography.titleMedium) }
                        items(entries.reversed()) { item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${item.priority} · ${item.source}", style = MaterialTheme.typography.titleSmall)
                                    Text(item.sender)
                                    Text(item.body)
                                    Text(item.time, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
