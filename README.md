# ShellDroid

<p align="center">
  <img src="docs/shelldroid-logo.svg" alt="ShellDroid" width="128" />
</p>

<p align="center">
  <a href="https://shelldroid.ebadenes.com/">Web</a> ·
  <a href="#build">Build</a> ·
  <a href="#licencia">Licencia</a>
</p>

**Cliente SSH profesional para Android.** Código abierto, sin anuncios, sin tracking.

Construido con **libssh nativo** (JNI + mbedTLS), **Jetpack Compose** (Material 3) y el emulador de terminal **termlib** de ConnectBot. 12 módulos, más de 200 tests, diseñado para funcionar.

## Features

### Conexión
- 🔐 **SSH nativo** — libssh 0.11.4 vía JNI, no wrappers Java. Auth por password y clave pública (RSA, Ed25519, ECDSA)
- ⚡ **Quick Connect** — `user@host:port`, contraseña opcional, opción de guardar la conexión. Si no la guardas se borra al desconectar
- 🟢 **Estado en vivo** — punto verde en la lista de hosts cuando la sesión SSH está activa; gris cuando no
- 🔑 **TOFU** — Trust On First Use con known hosts manager individual (ver/borrar claves por host)
- 💳 **Credentials picker** — elige entre contraseña puntual o una identidad guardada en el mismo modal al conectar

### Terminal
- 📱 **Compose-native** — termlib de ConnectBot, sin `AndroidView`. IME, selección, hyperlinks integrados
- ⌨️ **Hacker keyboard** — 2 filas estilo JuiceSSH con ESC, flechas, CTRL/ALT sticky, F1-F12, haptic feedback
- 📋 **Snippets** — comandos guardados ejecutables desde el terminal con un tap, o desde la lista de snippets eligiendo sesión destino
- 🧬 **Auto-command** — comando que se ejecuta automáticamente al conectar al host
- 💡 **Mantener pantalla** — flag `FLAG_KEEP_SCREEN_ON` opcional mientras haya terminal abierta
- 🔊 **Volume zoom** — sube y baja el tamaño de fuente con las teclas de volumen (persistido)
- ↩️ **Smart back gesture** — predictive back de Android 14+ con diálogo mantener/desconectar

### Port forwarding
- 🔀 **LOCAL** — `ssh -L` sobre libssh `direct-tcpip`. `ServerSocket` → pipe bidireccional via canal SSH
- 🧦 **DYNAMIC** — SOCKS5 proxy sobre SSH (`ssh -D`). Handshake RFC 1928 en Kotlin, el canal direct-tcpip se reutiliza
- 🔌 **Auto-connect** — tocar Play en un forward conecta primero el SSH del host si no está vivo
- 🔜 **REMOTE** — `ssh -R` marcado como "próximamente" en la UI, tracked en el backlog post-v1

### Seguridad
- 🔒 **Credential vault** — Tink AES-256-GCM sobre DataStore. `CharArray` + zeroize, nunca en el JVM string pool
- 🛡️ **App lock** — bloqueo delegado al sistema (BiometricPrompt + `DEVICE_CREDENTIAL`). Huella, PIN o patrón del dispositivo. Sin PIN propio de la app
- ⏰ **Auto-lock timeout** configurable: "Igual que el sistema" (lee `Settings.System.SCREEN_OFF_TIMEOUT`), Inmediato, 1/5/15 min, Nunca
- 🚫 **Sin backups inseguros** — `android:allowBackup=false`, data extraction rules restrictivas

### UI / UX
- 🎨 **Skins** — Abyss (default) y Solarized Dark con paleta ANSI de 16 colores. Cambio en vivo
- 🌙 **Dark / Light** — sigue al sistema o forzado. Paleta Abyss completa
- 🌐 **i18n** — español e inglés, configurable desde Settings
- ✨ **Splash screen** — transición limpia desde arranque con el logo Abyss
- 📌 **Foreground service** — sesiones sobreviven en background con notificación i18n, cronómetro y hostname. Acciones "Abrir terminal" / "Desconectar" desde la notificación
- 📄 **Clonar** — duplica hosts, identidades, snippets o port forwards con un tap

## Stack

| Capa | Tecnología |
|------|-----------|
| Lenguaje | Kotlin 2.3.20 |
| UI | Jetpack Compose + Material 3 (BOM 2026.03.01) |
| Terminal | [ConnectBot termlib](https://connectbot.org) 0.0.24 |
| SSH | [libssh](https://www.libssh.org) 0.11.4 nativo (JNI) + mbedTLS 3.6.4 |
| DI | Hilt 2.59.2 |
| DB | Room 2.8.4 |
| Crypto | Tink 1.15.0 |
| Build | AGP 9.1.0, Gradle 9.4.1, KSP 2.3.6 |
| Min SDK | 26 (Android 8.0+) |

## Módulos

```
:app                    → Activity, NavHost, Settings
:core:ssh               → LibSshClient, SshSessionManager, ShellChannel
:core:ssh-native        → JNI bindings (libssh + mbedTLS .so)
:core:db                → Room entities, DAOs, migrations
:core:security          → CredentialVault (Tink), LockManager
:core:ui                → Theme, strings i18n, shared UI
:feature:hosts          → CRUD hosts + Quick Connect
:feature:identities     → CRUD identities (password, keys)
:feature:terminal       → TerminalBridge, TerminalScreen, KeyBar, skins
:feature:snippets       → CRUD snippets
:feature:portforward    → CRUD port forwards
:service:session        → Foreground service + notificación
```

## Build

Todo el build vive en Docker (no se requiere SDK Android en el host):

```bash
./vendor/setup.sh                        # primera vez: clona libssh, mbedtls
./docker-gradle.sh :app:assembleDebug    # APK en app/build/outputs/apk/debug/
./docker-gradle.sh test                  # 200+ tests JVM
```

También disponible vía [Docker Compose](docker-compose.yml) un servidor OpenSSH local en `127.0.0.1:2222` para tests de integración.

## Versión actual

**v0.4.1-alpha** — camino a v1.0 estable. Funcional y listo para uso real; pendientes para el 1.0: REMOTE port forwarding, groups/folders, panic button, release signing, firma y subida a Play Store.

## Sobre el desarrollo

¿Lo he hecho solo? No. He trabajado con Copilot, Gemini, Claude y lo que hiciera falta en cada momento. En 2026 lo importante no es si usas IA o no, es qué eres capaz de construir con ella. ShellDroid es un cliente SSH profesional con libssh nativo vía JNI, Jetpack Compose, 12 módulos y más de 200 tests. Yo puse la dirección, las decisiones y las horas de testing en un Pixel real. La IA puso velocidad.

## Licencia

[GPLv3](LICENSE) — Software libre. Puedes usar, modificar y distribuir ShellDroid bajo los términos de la GNU General Public License v3.0.

Componentes de terceros: ver la pantalla de Licencias en la app (Settings → Acerca de → Licencias).
