# Build & Deploy to Device

## Build (debug APK)

```bash
./docker-gradle.sh :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Deploy via ADB over WiFi

### Prerequisites

- Android device with **Wireless debugging** enabled (Settings → Developer Options → Wireless debugging)
- Device and host on the same network
- ADB keys persisted in `.docker-cache/adb/` (generated on first pair)

### 1. Start the ADB container (if not running)

```bash
docker rm -f shelldroid-adb 2>/dev/null

docker run -d \
    -v "$PWD:/work" \
    -v "$PWD/.docker-cache/adb:/root/.android" \
    --network host \
    --name shelldroid-adb \
    -w /work \
    shelldroid-build:dev bash -c "adb start-server && sleep 3600"
```

The `-v .docker-cache/adb:/root/.android` mount persists the ADB keypair so pairing survives across containers.

### 2. Pair (only needed once per device, or after factory reset)

On the phone: **Wireless debugging → Pair device with code** → note the port and 6-digit code.

```bash
docker exec shelldroid-adb bash -c "printf '<CODE>\n' | adb pair 192.168.1.<IP>:<PAIR_PORT>"
```

Example:
```bash
docker exec shelldroid-adb bash -c "printf '959535\n' | adb pair 192.168.1.237:42841"
```

### 3. Connect

The **connection port** is shown on the main Wireless debugging screen (different from the pairing port).

```bash
docker exec shelldroid-adb bash -c "adb connect 192.168.1.237:<CONNECT_PORT>"
```

Example:
```bash
docker exec shelldroid-adb bash -c "adb connect 192.168.1.237:42713"
```

### 4. Install

```bash
docker exec shelldroid-adb bash -c "adb install /work/app/build/outputs/apk/debug/app-debug.apk"
```

### All-in-one (after pairing)

```bash
docker exec shelldroid-adb bash -c "adb connect 192.168.1.237:42713 && adb install /work/app/build/outputs/apk/debug/app-debug.apk"
```

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Connection refused` on connect | The connection port changes on each WiFi reconnect. Check the phone for the new port. |
| `error: protocol fault` on pair | Code expired. Generate a new one on the phone. |
| `no devices/emulators found` | Container died. Re-run step 1. |
| Need to re-pair | Only if you wiped `.docker-cache/adb/` or factory-reset the phone. |

## Notes

- The pairing port and code are ephemeral — they change every time you open the pairing dialog.
- The connection port changes when WiFi reconnects but stays stable during a session.
- ADB keys in `.docker-cache/adb/` are gitignored. Don't commit them.
