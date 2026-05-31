# Play Store Release Playbook — Prompt para apps nuevas

> Prompt reutilizable destilado del release de ShellDroid 1.0.0.
> Pegáselo a tu agente al preparar el primer release a producción de una app Android nueva.
> Cada sección lleva el ERROR que cometimos y la REGLA para no repetirlo.

---

## Contexto para el agente

Vas a preparar y publicar el primer release de producción de una app Android
(AAB firmado, App Signing de Google Play). NO asumas nada: validá cada paso
contra fuentes reales (el keystore, la API de Play, git). Frená y preguntá ante
cualquier ambigüedad. No publiques a usuarios reales sin confirmación explícita.

---

## Fase 0 — Estado real antes de tocar nada

ERROR cometido: el usuario dijo "está aprobado para producción" pero había
trabajo SIN commitear (un feature a medio terminar) colgando en el working tree.
"Lo aprobado" no era lo que estaba en `main`.

REGLAS:
- Corré `git status` PRIMERO. Si el árbol no está limpio, pará y aclará qué hacer
  con cada cambio antes de seguir.
- Cambios sin probar / a medias → `git stash push -m "descripción"`, NO los metas
  en el release.
- Confirmá que el nombre del proyecto en memoria/herramientas puede diferir del
  nombre de la carpeta. Verificá, no asumas.

---

## Fase 1 — Versionado (validar contra la API, no de memoria)

ERROR cometido: casi bumpeamos el versionCode "a ojo". El usuario insistió en
validar — con razón. El versionName decía `alpha` siendo un release de producción.

REGLAS:
- `versionCode` es lo ÚNICO que Play usa para ordenar releases. Debe ser
  ESTRICTAMENTE mayor que el más alto ya subido (en CUALQUIER track). Nunca se
  reutiliza ni baja.
- NO adivines el último versionCode. Consultalo contra la API real de Play
  (ver Fase 4, consulta read-only). El usuario no siempre recuerda el número.
- `versionName` es cosmético (lo ve el usuario). Para producción no debe decir
  `alpha`/`beta`. Definí el real (`1.0.0`, etc.).
- Bumpeá ambos en `app/build.gradle.kts` (`defaultConfig`).

---

## Fase 2 — Firma del AAB (el campo minado)

ERRORES cometidos:
1. Las contraseñas de firma estaban SOLO en la cabeza del usuario, en ningún env.
2. La `keyPassword` que cargó era DISTINTA a la `storePassword` — pero el
   keystore era PKCS12, donde DEBEN ser iguales. Eso habría roto el build.
3. El `signingConfig` usaba `file("keystore.jks")`, que resuelve relativo al
   módulo `app/`, no a la raíz → `validateSigningRelease` falló buscando el
   keystore en `app/keystore.jks`.

REGLAS:
- Identificá el tipo de firma: si el keystore se llama `*-upload.jks` y el alias
  es `upload`, casi seguro es **App Signing de Google** (vos firmás con upload key,
  Google re-firma con la clave final que custodia).
- Inspeccioná el keystore ANTES de buildear (dentro del Docker de build si keytool
  no está en el host):
  `keytool -list -v -keystore X.jks -storepass "$PASS"`
  → mirá `Keystore type`. Si es **PKCS12**, la key password = store password.
    Forzá `SHELLDROID_KEY_PASS == SHELLDROID_KEYSTORE_PASS`.
- VALIDÁ las contraseñas contra el keystore real antes del build completo (ahorra
  minutos). Una forma certera: `keytool -importkeystore` extrayendo el alias con
  esas pass; si pasa, firman bien.
- En `build.gradle.kts`, resolvé el keystore con `rootProject.file(...)`, NO
  `file(...)`, para que la ruta funcione sea cual sea el módulo que la evalúe.
- Secretos NUNCA en el chat, NUNCA en el repo:
  - `.env` con `KEYSTORE`, `KEYSTORE_PASS`, `KEY_PASS` → gitignoreado.
  - El agente crea el `.env` con las pass VACÍAS; el usuario las completa local.
  - El agente nunca lee ni imprime las contraseñas (enmascarar en cualquier echo).
  - Confirmá `git check-ignore` sobre `.env` y `*.jks` ANTES de cualquier commit.

---

## Fase 3 — Build firmado reproducible

REGLAS:
- Un único script `release.sh` que: sourcea `.env`, valida el keystore/alias,
  y corre el bundle dentro del contenedor de build. Un comando, sin pasos manuales
  que olvidar.
- Si el build corre en Docker con el proyecto montado en `/work`, el keystore
  debe estar DENTRO del proyecto (gitignoreado) para que el contenedor lo vea, y
  la ruta del `.env` debe ser relativa a la raíz.
- Verificá el AAB resultante, no confíes en "BUILD SUCCESSFUL":
  - existe y es reciente
  - firmado: `META-INF/*.RSA` presente; `keytool -printcert -jarfile X.aab`
    muestra el cert correcto (Owner + SHA256)
  - sin rastro de la versión vieja (`strings` del manifest no debe contener
    el versionName anterior → descarta build cacheado)
- Guardá el SHA256 del upload cert: Play lo valida en cada subida.

---

## Fase 4 — Subida a Play (API)

ERROR potencial evitado: el SA puede autenticar contra la API pero NO tener
permiso sobre ESTA app (los permisos en Play Console son POR APP, no globales).

REGLAS:
- Para la app nueva: en Google Play Console → Users & permissions, invitá al
  service account (`client_email` del JSON) y dale permiso sobre esa app.
- Las libs de Google API pueden estar en el Python del SISTEMA (`/usr/bin/python3`),
  no en el venv del proyecto. Verificá qué intérprete las tiene.
- Consulta read-only segura para ver versionCodes ya subidos (resuelve la Fase 1):
  `edits().insert()` → `tracks().list()` → `edits().delete()` (descarta sin tocar).
- Script de subida con red de seguridad:
  - `--dry-run`: sube el bundle y prepara el track pero DESCARTA el edit. Confirma
    que Play acepta el versionCode SIN publicar. Corrélo SIEMPRE primero.
  - rollout gradual (`--status inProgress --user-fraction 0.1`) disponible.
- Tras commitear el edit real, VERIFICÁ con `tracks().get()` que el track quedó
  con el versionCode/status esperados. No confíes solo en "Committed".

---

## Fase 5 — Decisión de riesgo del rollout

ERROR de criterio señalado: el usuario dijo "es la versión que está en beta", pero
el AAB de producción llevaba commits POSTERIORES al alpha que nunca pasaron por
testing. "Mismo número de feature" ≠ "mismo build probado".

REGLAS:
- Antes de publicar, compará: ¿el build de producción es EXACTAMENTE el que se
  probó en testing, o lleva código nuevo encima? Decíselo al usuario explícito.
- Para un PRIMER release de producción, recomendá rollout gradual (mismo AAB,
  mismo esfuerzo, con paracaídas) sobre el 100% directo. Es decisión del usuario,
  pero dejá el riesgo dicho con todas las letras.
- NUNCA ejecutes el upload real (no dry-run) sin OK explícito del usuario.

---

## Fase 6 — Commit de la infra de release

REGLAS:
- Stageá archivos EXPLÍCITAMENTE (lista de paths), nunca `git add .` a ciegas.
- Verificá tres veces que no se cuele ningún secreto:
  `git ls-files | grep -E '\.env$|\.jks$|\.keystore$|\.aab$'` → debe estar vacío.
- Agregá al `.gitignore`: `.env`, `.env.*` (con `!.env.example`), `*.jks`,
  `*.keystore`, `*.aab`, caches de tooling (`__pycache__/`, etc.).
- Commit conventional, SIN atribución AI / Co-Authored-By.
- Ojo con `GIT_SIGN_COMMITS`: si está activo pero falta la GPG secret key, el
  commit queda SIN firmar (avisá al usuario; no es bloqueante).

---

## Checklist rápido (TL;DR)

1. [ ] `git status` limpio (stash lo no probado)
2. [ ] versionCode validado contra la API de Play (> máximo subido)
3. [ ] versionName de producción (sin alpha/beta)
4. [ ] tipo de keystore identificado; si PKCS12 → key pass == store pass
5. [ ] contraseñas validadas con keytool ANTES del build
6. [ ] `rootProject.file()` para el keystore
7. [ ] secretos en `.env` gitignoreado, nunca en chat/repo
8. [ ] AAB verificado (firma + cert + versión, no solo "BUILD SUCCESSFUL")
9. [ ] SA con permiso sobre la app nueva en Play Console
10. [ ] `--dry-run` antes del upload real
11. [ ] riesgo del rollout dicho explícito; gradual recomendado para el 1.0.0
12. [ ] verificar el track post-commit vía API
13. [ ] commit sin secretos, conventional, push con confirmación
