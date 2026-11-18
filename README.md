# Radar Laboral — MVP multicanal

Aplicación Android nativa en Kotlin para capturar notificaciones que Android publique desde WhatsApp, WhatsApp Business, Telegram, Gmail y Outlook.

## Estado de esta versión

- Captura por `NotificationListenerService` de las aplicaciones incluidas en la lista permitida.
- Clasificación heurística inicial por palabras clave: urgente, importante e informativo.
- Historial temporal en memoria del proceso, con límite de 300 avisos.
- No incluye todavía persistencia Room, alarmas repetitivas, voz/TTS, calendario ni aprendizaje adaptativo.
- La clasificación es orientativa y debe probarse con mensajes reales; no sustituye una revisión humana.

## Alcance y límites

La aplicación lee únicamente el contenido que Android expone en las notificaciones recibidas. El contenido puede estar oculto por la configuración de privacidad, resumido por la aplicación emisora o no aparecer si las notificaciones están desactivadas.

No accede a las bases privadas de WhatsApp o Telegram, no recupera mensajes históricos y no puede leer conversaciones que no generen notificaciones. Para correo, esta versión captura notificaciones de Gmail y Outlook; no se conecta directamente a los buzones ni descarga correos mediante API.

## Privacidad

El procesamiento de esta versión ocurre localmente en el dispositivo. El historial se mantiene en memoria y se pierde si el proceso se cierra. No se envían mensajes a servidores externos.

## Compilar

GitHub Actions compila el APK Debug en cada push a `main`. Descargar el artefacto `RadarLaboral-debug-apk` desde la ejecución exitosa del flujo.

## APK Release firmado

El flujo puede producir un APK Release firmado, pero **no lo hará hasta que el propietario configure una clave de firma propia**. No se debe guardar el archivo keystore, contraseñas ni claves privadas en el repositorio.

1. En un equipo de confianza con Java instalado, generar una clave privada:
   ```bash
   keytool -genkeypair -v -keystore radar-laboral-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias radar-laboral
   ```
2. Guardar el archivo `radar-laboral-release.jks` en un respaldo privado y seguro. No perderlo: las futuras actualizaciones de Android deben firmarse con la misma clave.
3. Codificar el archivo en Base64. En Linux:
   ```bash
   base64 -w 0 radar-laboral-release.jks
   ```
   En PowerShell:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("radar-laboral-release.jks"))
   ```
4. En GitHub, abrir Settings → Secrets and variables → Actions → New repository secret y crear estos cuatro secretos:
   - `KEYSTORE_BASE64`: contenido Base64 del keystore.
   - `KEYSTORE_PASSWORD`: contraseña del keystore.
   - `KEY_ALIAS`: `radar-laboral` (o el alias elegido al generar la clave).
   - `KEY_PASSWORD`: contraseña de la clave.
5. Ejecutar Actions → Build Radar Laboral APK → Run workflow. Si los cuatro secretos son válidos, se publicarán dos artefactos: el Debug y `RadarLaboral-release-signed-apk`.

**Seguridad:** nunca enviar el keystore ni sus contraseñas por chat, correo, WhatsApp o commits. Si se pierde la clave privada, no se podrán firmar actualizaciones compatibles con la instalación anterior. El APK firmado acredita continuidad de firma, pero no equivale a una auditoría de seguridad ni garantiza que Play Protect no muestre advertencias.

## Instalar y activar

1. Instalar el APK en Android.
2. Abrir Radar Laboral.
3. Pulsar **Activar acceso a notificaciones**.
4. En Android, habilitar el acceso para Radar Laboral.
5. Comprobar que las notificaciones de WhatsApp, Telegram y las aplicaciones de correo elegidas estén habilitadas y permitan mostrar contenido.
6. Enviar avisos de prueba y pulsar **Actualizar historial**.

## Próximas etapas

1. Persistencia local con Room.
2. Motor de alertas con repetición configurable y horario de silencio.
3. Lectura por voz para avisos urgentes e importantes.
4. Acciones de confirmación: «Ya lo vi» y «Atendido».
5. Reglas configurables por aplicación, remitente y palabras clave.
6. Evaluar integración Gmail API con OAuth solo si se necesita leer contenido más allá de las notificaciones.
