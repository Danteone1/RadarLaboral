# Radar Laboral WhatsApp — MVP inicial

Aplicación Android nativa en Kotlin para capturar notificaciones publicadas por WhatsApp y WhatsApp Business mediante NotificationListenerService.

## Estado
Esta es una base MVP inicial. El historial actual vive en memoria del proceso: todavía no hay persistencia Room, alarmas repetitivas, TTS, calendario ni aprendizaje adaptativo. La clasificación es heurística por palabras clave. No afirmar que estas funciones están terminadas.

## Compilar
GitHub Actions compila el APK Debug en cada push a main o mediante Actions > Build Radar Laboral APK > Run workflow. Descargar el artefacto RadarLaboral-debug-apk cuando el flujo termine correctamente.

## Instalar y activar
Instalar el APK en Android 15. Abrir Radar Laboral y pulsar Activar acceso a notificaciones. Habilitar el permiso para el servicio.

## Límites y privacidad
Solo se reciben notificaciones que Android entrega al servicio. No se accede a la base privada de WhatsApp ni se recuperan mensajes antiguos, silenciados o sin notificación. Esta versión mantiene el historial en memoria y no lo envía a servidores externos.