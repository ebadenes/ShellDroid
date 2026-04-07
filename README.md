# ShellDroid

Cliente SSH moderno para Android (GPLv3) — sucesor espiritual de JuiceSSH con stack actual: Kotlin, Compose, Hilt, Room, libssh nativo y terminal Termux.

## Build

Todo el build vive en Docker (no se requiere SDK Android en el host):

```bash
./docker-gradle.sh assembleDebug
```

La primera ejecución construye la imagen `shelldroid-build:dev` definida en `Dockerfile.dev`.

## Tests de integración SSH

Para levantar un sshd local contra el cual correr tests:

```bash
docker compose up -d sshd
# usuario: testuser  password: testpass  puerto: 2222
docker compose down
```

## Estructura

Multi-módulo Gradle. Ver `settings.gradle.kts`. Detalle completo en `ShellDroid_Especificacion_MVP.md`.

## Licencia

GPLv3 (impuesta por el componente Termux `terminal-emulator` / `terminal-view`).
