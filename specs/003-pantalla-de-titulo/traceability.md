# Trazabilidad — 003 Pantalla de título y equipos nombrados (T823)

Matriz `AC → prueba(s) → nivel → estado`. Contrato de referencia: `specs/001-juego-base/contracts/` **v0.5.0**.

**Última verificación (2026-11-18):** `./mvnw verify` 205/205 · `./mvnw verify -Pacceptance` 213/213 · Vitest 107/107 · Playwright 32 pasan / 14 omitidas a propósito (2 corridas seguidas) · Postman CLI 92 peticiones, 174 aserciones, 0 fallos.

Abreviaturas de ubicación:
- **ACC** = `backend/src/test/java/mx/cienmexicanos/acceptance/TeamCaptainAcceptanceTest.java`: caja negra HTTP/STOMP, `@Tag("acceptance")`, puerto aleatorio.
- **E2E-T** = `frontend/e2e/title.spec.ts` · **E2E-C** = `frontend/e2e/captain.spec.ts` · **E2E-S** = `frontend/e2e/smoke.spec.ts`. Flujo común de la pantalla de título en `frontend/e2e/support/title.ts`.
- **PM** = `postman/collections/cien-mexicanos.postman_collection.json` (carpeta entre paréntesis).
- **UNIT** = Vitest de frontend-dev (`frontend/src/**/*.test.tsx`); aquí solo se referencia.

Niveles: `API` (REST caja negra) · `STOMP` (tiempo real caja negra) · `E2E` (navegador) · `Contrato` (Postman) · `Unit`.

Estado: ✅ pasa · ✅ escrita, compila y está pendiente de ejecutar (E2E/PM).

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-55 | E2E-T `AC-55 · AC-61 logotipo, menú EMPEZAR/AJUSTES y ningún campo de formulario visible` · E2E-S `la página principal carga con el título del juego` | E2E | ✅ |
| AC-56 | E2E-T `AC-56 al cargar la selección está en EMPEZAR; ↓/↑ y S/W la mueven`, `AC-56 · AC-58 ↓ + Enter abre AJUSTES; Esc lo cierra; Espacio también activa`, `AC-56 Enter sobre EMPEZAR crea la sala…`, `AC-56 el menú funciona con toque` (Pixel 7); el mouse lo cubren todos los flujos que usan `createRoomFromTitle` | E2E | ✅ |
| AC-57 | E2E-T `AC-58 · AC-57 los ajustes persisten tras recargar y se aplican al EMPEZAR` (la sala queda con "Equipo Rojo"/"Equipo Verde"), `AC-57 si crear la sala falla se muestra un aviso y se puede reintentar` (POST simulado con `page.route`: "Preparando…" con `aria-busy`, aviso `role=alert`, "Reintentar") · E2E-S `la pantalla de inicio muestra los QR…` · PM (02 sala) `createRoom sin nombres de equipo` | E2E · Contrato | ✅ |
| AC-58 | E2E-T `AC-58 los ajustes tienen los valores por defecto del contrato`, `AC-58 · AC-57 los ajustes persisten tras recargar…` (rondas 4, meta 500, sin temporizador → `GET /api/rooms/{code}`), `AC-58 temporizador de 30 s sin X automática se aplica a la sala`, foco atrapado con Tab/Shift+Tab y ←/→ en el modal · flujos de `game.spec.ts`/`turns.spec.ts` (rondas y temporizador desde el modal; la TV muestra "Ronda 1 de 3") | E2E | ✅ |
| AC-59 | E2E-T `AC-59 con sonido activado, mover y activar el menú suena; sin interacción ni con sonido apagado, no` (cuenta `createOscillator` en el navegador) | E2E | ✅ |
| AC-60 | E2E-T `AC-60 a 360 px de ancho no hay scroll horizontal y el menú es visible`, `AC-60 con prefers-reduced-motion no hay animaciones de entrada ni de fondo` (`document.getAnimations()`; se tolera el resaltado del menú) · TV 1920×1080 en todos los flujos | E2E | ✅ |
| AC-61 | E2E-T `AC-55 · AC-61 …`, `AC-61 la pantalla de inicio /screen muestra el logotipo y el título del documento` · E2E-S `toHaveTitle` · `game.spec.ts` logotipo en `/screen` | E2E | ✅ |
| AC-62 | ACC `captainNullWithoutPlayers` (null en `createRoom`, vista pública y del presentador; la clave siempre presente), `firstPlayerIsCaptain`, `captainInStompSnapshot`, `joinSeesCurrentName` · PM (02 sala) `createRoom…` captainId null; (04b equipos) `getRoom: el primero de cada equipo es capitán`, `getPresenterView también trae captainId`; `expectRoomState` exige `captainId` en cada `Team` · E2E-C | API · STOMP · Contrato · E2E | ✅ ACC · ✅ E2E/PM |
| AC-63 | ACC `captainRenames`, `trimsSpaces`, `validLengthLimits` (2 y 20 caracteres, con espacios), `sameNameIsNoOp` (200 sin cambio de versión), `ownNameDifferentCaseAllowed`, `renameSeveralTimes` · PM (04b equipos) renombrar A (recorte) y B, mismo nombre no-op · E2E-C | API · Contrato · E2E | ✅ ACC · ✅ E2E/PM |
| AC-64 | ACC `missingTokenIs401`, `presenterTokenOnlyIs401`, `presenterTokenAsPlayerTokenIs403`, `sameTeamNonCaptainIs403`, `otherTeamCaptainIs403`, `otherRoomTokenIs403`, `unknownTokenRejected`, `unknownRoomIs404`, `unknownTeamRejected` (C → 400), `outsideLobbyIs409`, `gameOverIs409`, `defaultNameTakenIs409` (4 variantes de mayúsculas/espacios), `renamedNameTakenIs409`, `invalidLengthIs400` (1 y 21 caracteres, vacío, solo espacios), `invalidBodyIs400`, `notCaptainCheckedBeforeValidation` · PM (04b equipos) 401, 403 ×3, 404, 409 TEAM_NAME_TAKEN, 400 ×2; (06 ronda 1) 409 ROOM_NOT_IN_LOBBY · E2E-C (choque con el otro equipo bloquea "Guardar nombre") | API · Contrato · E2E | ✅ ACC · ✅ E2E/PM |
| AC-65 | ACC `teamRenamedEventWithinOneSecond` (`TEAM_RENAMED` con `team`/`version` y snapshot en ≤ 1 s), `playerReceivesNewName` (celular suscrito a `/state`), `rejectedRenamePublishesNothing` · E2E-C TV y celulares muestran el nombre nuevo en ≤ 1 s | STOMP · E2E | ✅ ACC · ✅ E2E |
| AC-66 | E2E-C "Eres el capitán de <equipo>", campo "Ponle nombre a tu equipo" y "Guardar nombre" solo para el capitán; los demás ven el nombre en "Tu equipo"; `/join` muestra los nombres actuales y "Capitán: …" · ACC `joinSeesCurrentName` | E2E · API | ✅ ACC · ✅ E2E |
| AC-67 | ACC `rematchKeepsNamesAndCaptains` (nombres y capitanes en ambas proyecciones; el capitán vuelve a renombrar, el no capitán sigue con 403) · PM (08 ronda 3 y fin) `rematch → LOBBY` y `renameTeam tras la revancha` | API · Contrato | ✅ ACC · ✅ PM |

## Ajustes a pruebas previas (T822/T823)
- `smoke.spec.ts`, `game.spec.ts`, `turns.spec.ts`: la sala ya no se crea con el formulario "Crear sala" sino con `createRoomFromTitle` (AJUSTES → EMPEZAR). Se quitaron las comprobaciones de "Nombre del equipo A/B" y del título "Cien Mexicanos".
- Postman: la sala del flujo se crea sin `teamAName`/`teamBName`; se añadió el jugador "Chuy" (A, no capitán), una carpeta "04b equipos" y el cierre de la revancha espera a Lupita, Chuy y Pancho con los nombres nuevos.

## Decisiones del backend reflejadas
Orden de errores: 401 → 404 → 403 `NOT_CAPTAIN` (también presenterToken como `X-Player-Token`, token de otra sala, del otro equipo o de un no capitán) → 409 `ROOM_NOT_IN_LOBBY` → 400 `VALIDATION_ERROR` (longitud tras recortar; `name` ausente/null; `{team}` inválido) → 409 `TEAM_NAME_TAKEN`. El mismo nombre exacto es 200 sin evento ni nueva versión; cambiar solo las mayúsculas del propio nombre sí es un cambio.

## US-18 Pantalla de QR sin datos técnicos (2026-11-18)
| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-68 | Vitest `app/routes.test.tsx` "AC-68 · AC-69 · AC-70…" · E2E `smoke.spec.ts`, `game.spec.ts` (código validado en la URL del QR) · E2E `turns.spec.ts` "AC-70 · AC-53…" | UNIT · E2E | ✅ pasa |
| AC-69 | Vitest `app/routes.test.tsx` "AC-68 · AC-69 · AC-70…" · E2E `turns.spec.ts` "AC-70 · AC-53…" | UNIT · E2E | ✅ pasa |
| AC-70 | Vitest `app/routes.test.tsx` y `features/screen/screen.test.tsx` (AC-52 ahora en el tablero) · E2E `turns.spec.ts` "AC-70 · AC-53…" | UNIT · E2E | ✅ pasa |
| AC-71 | Revisión visual con captura 1920×1080 (logotipo, QR y equipos sin desplazamiento) | Manual | ✅ revisado |
| AC-72 | Vitest `app/routes.test.tsx` "AC-72…" | UNIT | ✅ pasa |

Verificación US-18: Vitest 108/108 · Playwright 32 pasan / 14 omitidas a propósito.
