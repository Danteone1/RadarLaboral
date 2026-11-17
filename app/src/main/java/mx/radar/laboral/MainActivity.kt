package mx.radar.laboral

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var entries by remember { mutableStateOf(WhatsAppCaptureService.history.toList()) }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("RADAR LABORAL", style = MaterialTheme.typography.headlineMedium)
                    Text("Captura de notificaciones disponibles de WhatsApp")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()) { Text("Activar acceso a notificaciones") }
                    OutlinedButton(onClick = { entries = WhatsAppCaptureService.history.toList() },
                        modifier = Modifier.fillMaxWidth()) { Text("Actualizar historial") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entries.reversed()) { item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${item.priority} · ${item.sender}", style = MaterialTheme.typography.titleSmall)
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