# ShellDroid

<p align="center">
  <img src="docs/shelldroid-logo.svg" alt="ShellDroid" width="128" />
</p>

<p align="center">
  SSH client for Android. Native libssh, Compose UI, no ads, no tracking.<br>
  <a href="https://shelldroid.ebadenes.com/">Web</a> · <a href="https://groups.google.com/g/shelldroid-testers">Beta</a> · <a href="#build">Build</a> · <a href="#license">License</a>
</p>

---

## Beta abierta

ShellDroid is in **closed beta** on Google Play:

1. [Join the testers group](https://groups.google.com/g/shelldroid-testers) (one click)
2. [Download from Google Play](https://play.google.com/apps/testing/com.ebadenes.shelldroid)

---

## Why this exists

JuiceSSH disappeared. One day it was there, the next the Pro unlock was gone and the app stopped getting updates. Years of muscle memory — the hacker keyboard, quick connect, snippets — vanished.

The alternatives? ConnectBot hasn't had a meaningful commit since 2019. Termius wants $10/month. I use maybe 20% of what Termius offers.

So I built my own.

**What's different technically:**

Most Android SSH clients sit on JSch or sshlib — pure Java, aging crypto, slow key exchange. ShellDroid uses libssh 0.11.4 compiled natively via JNI with mbedTLS. Ed25519 and ECDSA work properly, and the first connection is noticeably faster because the key exchange isn't fighting the JVM.

The terminal runs on ConnectBot's termlib but as a real `@Composable` — not an AndroidView hacked into Compose. IME resizing, focus, and key handling live in the composition tree. No flickering, no workarounds.

## Screenshots

<p align="center">
  <img src="screenshots/01_hosts_list.png" width="180" alt="Host list" />
  <img src="screenshots/02_quick_connect.png" width="180" alt="Quick Connect" />
  <img src="screenshots/04_terminal.png" width="180" alt="Terminal" />
  <img src="screenshots/05_snippets.png" width="180" alt="Snippets" />
  <img src="screenshots/08_lock_screen.png" width="180" alt="App lock" />
</p>

## What it does

**Connection** — libssh native (password, RSA, Ed25519, ECDSA). Quick Connect (`user@host:port`), TOFU with per-host known hosts, live session indicator.

**Terminal** — Compose-native termlib. Two-row hacker keyboard (ESC, arrows, CTRL/ALT sticky, F1–F12). Snippets (saved commands, one tap). Auto-command on connect. Volume keys for font size.

**Port forwarding** — LOCAL (`ssh -L`), DYNAMIC/SOCKS5 (`ssh -D`, RFC 1928 in Kotlin). Auto-connect on play. REMOTE (`ssh -R`) coming next.

**Security** — Tink AES-256-GCM credential vault (`CharArray` + zeroize, never in the string pool). App lock via BiometricPrompt + system credential. Configurable auto-lock timeout. `allowBackup=false`.

**UI** — Two skins (Abyss, Solarized Dark). Dark/Light follows system. English + Spanish. Foreground service with notification actions. Clone anything with one tap.

## Build

Everything runs in Docker — no Android SDK on the host:

```bash
./vendor/setup.sh                        # first time: clones libssh, mbedtls
./docker-gradle.sh :app:assembleDebug    # APK in app/build/outputs/apk/debug/
./docker-gradle.sh test                  # unit tests
```

Local OpenSSH server via [Docker Compose](docker-compose.yml) at `127.0.0.1:2222` for integration tests.

## Architecture

12 Gradle modules. Kotlin, Jetpack Compose + Material 3, Hilt, Room, libssh native + mbedTLS. Min SDK 26.

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
:service:session        → Foreground service + notification
```

## Status

**v0.4.2-alpha** — functional, I use it daily. What's left for 1.0: REMOTE forwarding, host groups, panic button, public Play Store release.

## License

[GPLv3](LICENSE). Third-party licenses: Settings → About → Licenses in the app.
