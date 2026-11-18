# Trazabilidad — 002 Experiencia de juego (T726)

Matriz `AC → prueba(s) → nivel → estado`. Contrato de referencia: `specs/001-juego-base/contracts/` **v0.4.0**.

**Última verificación (2026-11-18, tras la spec 003):** `./mvnw verify` 205/205 · `./mvnw verify -Pacceptance` 213/213 · Vitest 107/107 · Playwright 32 pasan / 14 omitidas a propósito (2 corridas seguidas) · Postman CLI 92 peticiones, 174 aserciones, 0 fallos.

Abreviaturas de ubicación:
- **ACC** = `backend/src/test/java/mx/cienmexicanos/acceptance/`: caja negra HTTP/STOMP, `@Tag("acceptance")`, puerto aleatorio.
- **E2E** = `frontend/e2e/turns.spec.ts`: Playwright (TV 1920×1080 + presentador Pixel 7 + jugadores Pixel 7 / iPhone).
- **PM** = `postman/collections/cien-mexicanos.postman_collection.json` (carpeta entre paréntesis).
- **UNIT** = dominio/temporizador (`backend/src/test/java/mx/cienmexicanos/game/**`, T706) y Vitest (`frontend/src/**/*.test.tsx`). Los escriben backend-dev y frontend-dev; aquí solo se referencian.

Niveles: `API` (REST caja negra) · `STOMP` (tiempo real caja negra) · `E2E` (navegador) · `Contrato` (Postman) · `Unit`.

Estado: ✅ pasa · 🟡 escrita, compila y está pendiente de ejecutar (E2E/PM).

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-35 | ACC `ActiveTeamAcceptanceTest`: `lobbyHasNoActiveTeam`, `faceOffWithoutBuzzHasNoActiveTeam`, `faceOffBuzzWinnerTeamIsActive`, `faceOffRevealPassesTurn`, `faceOffStrikePassesTurn`, `playingActiveIsControllingTeam`, `stealActiveIsRival`, `roundEndAndGameOverHaveNoActiveTeam`, `nextRoundResetsActiveTeam` (vista pública y del presentador; claves requeridas de `RoundState`) · PM (06 ronda 1) `startGame`, `assignControl A`, `markStrike 3 → STEAL`, robo · E2E resalte cara a cara → juego → robo · UNIT `TurnRulesTest` | API · Contrato · E2E · Unit | ✅ ACC · 🟡 E2E/PM |
| AC-36 | ACC `ActiveTeamAcceptanceTest`: `turnChangedOnEveryChange` (secuencia A, B, A, B, A con `team` y `version` creciente), `noTurnChangedWithoutChange` | STOMP · Unit | ✅ ACC |
| AC-37 | E2E: "EN TURNO" en `team-panel-{A,B}`, clase `board__side--turn` / `board__side--dim`, ninguno resaltado sin equipo en turno y en ROUND_END · UNIT `screen.test.tsx` | E2E · Unit | 🟡 E2E |
| AC-38 | E2E: "¡Le toca a tu equipo!" / "Turno de <equipo>" en ambos celulares en cada cambio · UNIT `player.test.tsx` | E2E · Unit | 🟡 E2E |
| AC-39 | E2E: `.turn-banner__text` "En turno: <equipo>" / "Ningún equipo en turno" en el presentador · UNIT `presenter.test.tsx` | E2E · Unit | 🟡 E2E |
| AC-40 | ACC `TeamStrikesAcceptanceTest`: `strikeWithoutBuzzIsNoActiveTeam`, `noActiveTeamIsProblemJson`, `revealAndControlWithoutBuzzStillWork`, `playingStrikesGoToControllingTeam`, `stealStrikeGoesToRival`, `stealStrikeTargetsActiveNotControlling` · PM (06 ronda 1 y 08 ronda 3) `markStrike en cara a cara sin buzz → 409 NO_ACTIVE_TEAM` · E2E X en el equipo correcto · UNIT `RoomRulesTest` | API · Contrato · E2E · Unit | ✅ ACC · 🟡 E2E/PM |
| AC-41 | ACC `TeamStrikesAcceptanceTest`: `strikesByTeamStartsAtZero`, `playingStrikesGoToControllingTeam`, `stealStrikeGoesToRival`, `faceOffStrikesDoNotAccumulate`, `nextRoundResetsStrikesByTeam` (invariante `strikesByTeam[controllingTeam] == strikes` en cada respuesta) · ACC `TurnTimerAcceptanceTest` (invariante tras la X automática) · PM (invariante en todas las respuestas; `strikesByTeam` en ronda 1 y 2) · UNIT | API · Contrato · Unit | ✅ ACC · 🟡 PM |
| AC-42 | ACC `TeamStrikesAcceptanceTest`: `faceOffStrikeEvent`, `faceOffStrikesDoNotAccumulate` · ACC `ActiveTeamAcceptanceTest.faceOffStrikePassesTurn` · ACC `TurnTimerAcceptanceTest.expiryInFaceOffStrikesAndPassesTurn` · E2E X a Lupita en el cara a cara · UNIT | STOMP · API · E2E · Unit | ✅ ACC · 🟡 E2E |
| AC-43 | ACC `TeamStrikesAcceptanceTest`: `faceOffStrikeEvent`, `strikeEventsCarryTeamAndCount` (PLAYING 1…3, STEAL 1, un STRIKE por X) · ACC `TurnTimerAcceptanceTest` (STRIKE automático con `team`/`strikes`) · UNIT | STOMP · Unit | ✅ ACC |
| AC-44 | E2E: "X de <equipo>: n" bajo cada panel; X gigante `role=img` "X para <equipo>" con `data-team` · UNIT `screen.test.tsx` | E2E · Unit | 🟡 E2E |
| AC-45 | E2E: "✕ X para <equipo en turno>" habilitado; "✕ X (sin equipo en turno)" deshabilitado en FACE_OFF sin buzz; sin botón en ROUND_END · UNIT `presenter.test.tsx` | E2E · Unit | 🟡 E2E |
| AC-46 | ACC `TurnTimerAcceptanceTest`: `defaults` (20 s, `true`), `validTurnSeconds` (0, 10, 35, 60), `invalidTurnSecondsRejected` (−1, 1, 9, 61, 120, no numérico, booleano inválido → 400 problem+json), `autoStrikeCanBeDisabled` · PM (02 sala) `turnSeconds 9 / 61 / -1 → 400`, `turnSeconds 10 sin X automática`, valores por defecto · E2E selector "Tiempo por turno" (0 y 10 s) · UNIT | API · Contrato · E2E · Unit | ✅ ACC · 🟡 E2E/PM |
| AC-47 | ACC `TurnTimerAcceptanceTest`: `deadlineFollowsActiveTeam` (null en LOBBY, en FACE_OFF sin buzz y en ROUND_END; `ahora + turnSeconds` en UTC tras el buzz, revelar, control, X y el paso a STEAL; igual en la vista pública), `noTimerMeansNoDeadline`, renovación tras la X automática · PM (formato UTC; null con `turnSeconds = 0`) · E2E sin `role=timer` sin equipo en turno · UNIT `TurnRulesTest` | API · STOMP · Contrato · E2E · Unit | ✅ ACC · 🟡 E2E/PM |
| AC-48 | ACC `TurnTimerAcceptanceTest`: `expiryInFaceOffStrikesAndPassesTurn`, `expiryInStealEndsRound`, `expiryWithoutAutoStrike` (`TIMER_EXPIRED` con `team`, nunca antes de `turnDeadline`; X automática con las reglas de AC-40…43; sin X y `turnDeadline` null con `autoStrikeOnTimeout=false`) · E2E temporizador de 10 s que vence → "X de Equipo Rojo: 1" · UNIT `TurnTimerSchedulerTest` | STOMP · API · E2E · Unit | ✅ ACC · 🟡 E2E |
| AC-49 | ACC `TurnTimerAcceptanceTest`: `manualActionBeforeExpiryPreventsDoubleStrike` (X manual 750 ms antes de vencer: sin `TIMER_EXPIRED` ni X extra; el vencimiento siguiente dispara una sola vez), `revealBeforeExpiryCancelsTimer` · UNIT `TurnTimerSchedulerTest` | STOMP · API · Unit | ✅ ACC |
| AC-50 | E2E: `role=timer` "Tiempo del turno: n segundos" en TV y en ambos celulares; clase `countdown--urgent` en los últimos 5 s · UNIT `Countdown.test.tsx`, `serverClock.test.ts` | E2E · Unit | 🟡 E2E |
| AC-51 | UNIT `audio.test.tsx` (síntesis Web Audio, un sonido por evento). Sin prueba de caja negra: el audio no es observable con Playwright sin instrumentar el código | Unit | (de frontend-dev) |
| AC-52 | E2E "Activar sonido" visible en la pantalla de inicio → "Sonido activado"; tras recargar, la preferencia se recuerda y basta una interacción · UNIT `audio.test.tsx` | E2E · Unit | 🟡 E2E |
| AC-53 | ACC `SoundAcceptanceTest`: `soundEnabledByDefault`, `toggleSound`, `toggleDuringRound`, `soundToggledEventAndBroadcast`, `missingTokenIs401`, `otherRoomTokenIs403`, `playerTokenCannotToggle`, `unknownRoomIs404`, `invalidBodyIs400` · ACC `PresenterSecurityAcceptanceTest` (fila `setSound`) · ACC `TurnTimerAcceptanceTest.defaults` · PM (05b sonido) · E2E "Silenciar la TV" → "Silenciado por el presentador" → "Activar sonido de la TV" | API · STOMP · Contrato · E2E | ✅ ACC · 🟡 E2E/PM |
| AC-54 | E2E: `navigator.vibrate` sustituido con `addInitScript`; vibra al ganar el buzzer y cuando el turno pasa a su equipo · UNIT `player.test.tsx` | E2E · Unit | 🟡 E2E |

## Regresión de la spec 001 (T727)

| Prueba de 001 | Ajuste | Motivo |
|---|---|---|
| ACC `AcceptanceTestBase.createRoom(rounds, targetScore)` (lo usan todas las partidas de 001) | Ahora crea la sala con `turnSeconds=0`. Hay sobrecargas nuevas con `turnSeconds`/`autoStrikeOnTimeout` para la 002 | AC-46: el valor por defecto pasó a 20 s con X automática; una X automática alteraría strikes/versiones en pruebas que no son del temporizador. `createRoom("{}")` sigue usando los valores por defecto (salas que no inician partida) |
| ACC `PresenterSecurityAcceptanceTest.presenterOperations` | Fila nueva `setSound` (401 / 403 / token inventado) | AC-53 agrega una operación protegida al contrato |
| ACC con X en FACE_OFF | Sin cambios: todas hacían buzz antes de la X (`playRoundWithEmptyPot`, `faceOffStrikeStaysInFaceOff`) | C-2 actualizada: solo la X **sin** buzz cambia (→ `NO_ACTIVE_TEAM`) |
| ACC `StateTransitionsAcceptanceTest` (X en LOBBY / ROUND_END / GAME_OVER → `INVALID_STATE`) | Sin cambios | C-6 sigue vigente; el backend responde `INVALID_STATE` (ver ambigüedad 1) |
| PM (08 ronda 3) `markStrike en cara a cara` (200) | Ahora `→ 409 NO_ACTIVE_TEAM` (Postman no puede hacer buzz por STOMP) | AC-40 / C-2 |
| PM `createRoom` del flujo | `turnSeconds: 0` | Igual que en ACC |
| E2E `game.spec.ts` | Selecciona "Sin temporizador"; el botón pasa de "✕ Marcar X" a `/X para /`; el contador "Strikes: n de 3" pasa a "X de Equipo Rojo: n" | AC-45, AC-44, AC-46 |

## Ambigüedades detectadas (no se cambió la spec)
1. **AC-40 frente a C-6**: AC-40 dice "si `activeTeam` es null → `409 NO_ACTIVE_TEAM`", pero en LOBBY / ROUND_END / GAME_OVER `activeTeam` también es null y C-6 pide `INVALID_STATE`. Las pruebas asumen que manda C-6 (el backend lo hace así). Conviene escribirlo en la spec.
2. **AC-35, cara a cara**: "cada respuesta pasa el turno al otro equipo hasta asignar el control" se probó literalmente (buzz A → revelar → B → revelar → A). No dice qué pasa con revelar **sin** buzz (C-2): ¿sigue `activeTeam` null? No se probó.
3. **AC-36 al pasar a null**: "cada cambio de `activeTeam` publica `TURN_CHANGED` (con `team`)", pero `GameEvent.team` no admite null. No se verifica si hay `TURN_CHANGED` al llegar a ROUND_END ni con qué forma.
4. **AC-41 en ROUND_END**: no dice si `strikesByTeam` se conserva al cerrar la ronda. Las pruebas asumen que se conserva (p. ej. `{A:3, B:1}` tras el robo fallido) y que se reinicia en `nextRound`.
5. **AC-53**: no dice si `POST /sound` con el mismo valor que ya tiene publica `SOUND_TOGGLED` o sube `version`. No se probó.
6. **AC-48 con `autoStrikeOnTimeout=false`**: no dice si `TIMER_EXPIRED` sube `version` (se asume que sí, porque `turnDeadline` cambia a null).
7. **AC-52**: "la TV recuerda la preferencia", pero el navegador exige una interacción en cada carga. La E2E sigue lo implementado: tras recargar basta cualquier toque o tecla.
