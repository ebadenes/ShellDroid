# ShellDroid

<p align="center">
  <img src="internal/shelldroid-logo.svg" alt="ShellDroid" width="128" />
</p>

**Cliente SSH profesional para Android.** Código abierto, sin anuncios, sin tracking.

Construido con **libssh nativo** (JNI + mbedTLS), **Jetpack Compose** (Material 3) y el emulador de terminal **termlib** de ConnectBot. 12 módulos, más de 200 tests, diseñado para funcionar.

## Features

- 🔐 **SSH nativo** — libssh 0.11.4 vía JNI, no wrappers Java. Auth por password y clave pública (RSA, Ed25519, ECDSA)
- 📱 **Terminal Compose-native** — termlib de ConnectBot, sin AndroidView. IME, selección, hyperlinks integrados
- ⚡ **Quick Connect** — `user@host:port` y listo. Opción de guardar
- ⌨️ **Hacker keyboard** — 2 filas estilo JuiceSSH con ESC, flechas, CTRL/ALT sticky, F1-F12, haptic feedback
- 📋 **Snippets** — comandos guardados ejecutables desde el terminal con un tap
- 🔀 **Port Forwards** — gestión de LOCAL, REMOTE y DYNAMIC (UI completa, wire a libssh pendiente)
- 🔑 **TOFU** — Trust On First Use con known hosts manager individual
- 🎨 **Skins** — Abyss (default) y Solarized Dark con paleta ANSI de 16 colores. Cambio en vivo
- 🔊 **Volume zoom** — sube y baja el tamaño de fuente con las teclas de volumen (persistido)
- 🌙 **Dark / Light** — sigue al sistema o forzado. Paleta Abyss completa
- 🌐 **i18n** — español e inglés, configurable desde Settings
- 📌 **Foreground service** — sesiones sobreviven en background con notificación, cronómetro y hostname
- 🔔 **Notificación inteligente** — "Abrir terminal" para volver, "Desconectar" para cerrar
- ⚙️ **Settings** — tema, fuente, idioma, known hosts, licencias, auto-lock (pendiente)
- 🧬 **Auto-command** — ejecuta un comando automáticamente al conectar
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
./docker-gradle.sh test                  # 207+ tests JVM
```

## Sobre el desarrollo

¿Lo he hecho solo? No. He trabajado con Copilot, Gemini, Claude y lo que hiciera falta en cada momento. En 2026 lo importante no es si usas IA o no, es qué eres capaz de construir con ella. ShellDroid es un cliente SSH profesional con libssh nativo vía JNI, Jetpack Compose, 12 módulos y más de 200 tests. Yo puse la dirección, las decisiones y las horas de testing en un Pixel real. La IA puso velocidad.

## Licencia

[GPLv3](LICENSE) — Software libre. Puedes usar, modificar y distribuir ShellDroid bajo los términos de la GNU General Public License v3.0.

Componentes de terceros: ver la pantalla de Licencias en la app (Settings → Acerca de → Licencias).
