# Trazabilidad — 001 Juego base (T602)

Matriz `AC → prueba(s) → nivel → estado`. Contrato de referencia: `openapi.yaml` / `asyncapi.yaml` **v0.3.0**.

**Última verificación (2026-11-18, tras la spec 003):** `./mvnw verify` 205/205 · `./mvnw verify -Pacceptance` 213/213 · Vitest 107/107 · Playwright 32 pasan / 14 omitidas a propósito (2 corridas seguidas) · Postman CLI 92 peticiones, 174 aserciones, 0 fallos.

Abreviaturas de ubicación:
- **ACC** = `backend/src/test/java/mx/cienmexicanos/acceptance/` — caja negra HTTP/STOMP, `@Tag("acceptance")`, servidor en puerto aleatorio.
- **E2E** = `frontend/e2e/` — Playwright (TV 1920×1080 + presentador móvil + 2 jugadores Pixel 7 / iPhone).
- **PM** = `postman/collections/cien-mexicanos.postman_collection.json` — flujo encadenado (carpeta entre paréntesis).
- **UNIT** = pruebas de dominio / componentes (dueños: T103 y pruebas Vitest), no las escribe este encargo.

Niveles: `API` (REST caja negra) · `STOMP` (tiempo real caja negra) · `E2E` (navegador) · `Contrato` (Postman).

Estado: **✅ pasa (ACC/E2E/PM)** = la prueba existe y compila; se valida al terminar backend/frontend.

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-01 | ACC `RoomCreationAcceptanceTest`: `createRoomReturnsCodeAndTokens`, `codesAreUniqueAmongActiveRooms`, `createdRoomIsRetrievable`, `unknownRoomIs404` · PM (02 sala) `createRoom`, `createRoom con valores por defecto` · E2E `game.spec.ts` (código en la URL) · `smoke.spec.ts` (AC-29) | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-02 | ACC `RoomCreationAcceptanceTest`: `defaultsWithoutBody`, `defaultsWithEmptyBody`, `customSettings`, `roundsBoundariesAccepted`, `invalidSettingsRejected` · PM (02 sala) `createRoom`, `createRoom con valores por defecto`, `createRoom inválida → 400` · E2E `game.spec.ts` (nombres por defecto y "Rondas") | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-03 | E2E `game.spec.ts` (URL del QR de jugadores `http://<ip>:<puerto>/join/<code>` y código grande) | E2E | ✅ pasa (ACC/E2E/PM) |
| AC-04 | ACC `RoomCreationAcceptanceTest.networkInfo` · `network/NetworkInfoControllerTest` (existente) · PM (01 red) `getNetworkInfo` | API · Contrato | ✅ pasa (ACC + E2E + PM) |
| AC-05 | ACC `JoinRoomAcceptanceTest`: `joinReturnsPlayerAndToken`, `nameBoundariesAccepted`, `twentyCharsAccepted`, `invalidNameRejected`, `invalidTeamRejected`, `joinUnknownRoom` · PM (04 jugadores) `joinRoom Lupita`, `joinRoom Pancho`, `joinRoom nombre corto` · E2E `game.spec.ts` (unirse), `AC-05 · AC-06 errores claros al unirse` | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-06 | ACC `JoinRoomAcceptanceTest`: `duplicateNameCaseInsensitive`, `sameNameInOtherRoom` · PM `joinRoom nombre repetido → 409` · E2E `AC-05 · AC-06 errores claros al unirse` | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-07 | ACC `JoinRoomAcceptanceTest.cannotJoinAfterStart` · PM (06 ronda 1) `joinRoom tras iniciar → 409` | API · Contrato | ✅ pasa (ACC/E2E/PM) |
| AC-08 | ACC `RealtimeAcceptanceTest`: `joinIsBroadcastWithinOneSecond` (≤ 1 s, agrupado por equipo), `broadcastVersionsIncrease`, `onlyFirstBuzzWinsUnderConcurrency` (20 jugadores en el lobby) · E2E `game.spec.ts` (lista por equipo en la TV con timeout de 1 s) | STOMP · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-09 | ACC `JoinRoomAcceptanceTest`: `recoverSessionWithToken`, `recoverSessionAfterStart`, `meWithoutOrBadToken` · PM `getMe`, `getMe sin token → 401` · E2E `game.spec.ts` (recarga de `/play/<code>`) | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-10 | ACC `StartGameAcceptanceTest`: `cannotStartWithEmptyTeam`, `cannotStartEmptyRoom` · PM `startGame sin jugadores / con un equipo vacío → 409 TEAM_EMPTY` · E2E `game.spec.ts` (botón "Iniciar partida" deshabilitado) | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-11 | ACC `StartGameAcceptanceTest`: `startMovesToFaceOffRoundOne`, `distinctQuestionsPerRound` (8 rondas sin repetir) · PM `startGame (AC-11)` · E2E `game.spec.ts` ("Ronda 1 de 3") | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-12 | ACC `StartGameAcceptanceTest.faceOffRotatesInArrivalOrder` (3 A + 2 B, 4 rondas) · PM `startGame` (playerA = primer jugador de A) | API · Contrato | ✅ pasa (ACC/E2E/PM) |
| AC-13 | E2E `game.spec.ts` (buzzer visible solo a los designados; desaparece tras el buzz) · UNIT `PlayPage` (Vitest, frontend-dev) | E2E | ✅ pasa (ACC/E2E/PM) |
| AC-14 | ACC `RealtimeAcceptanceTest.onlyFirstBuzzWinsUnderConcurrency` (20 sesiones STOMP, buzz simultáneo con latch, un único `buzzWinner` y un solo `BUZZ_WON`, todos lo ven en ≤ 500 ms) · E2E `game.spec.ts` | STOMP · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-15 | ACC `RoundFlowAcceptanceTest`: `faceOffTopAnswerGivesControl`, `faceOffRivalWithHigherAnswerGetsControl`, `faceOffStrikeStaysInFaceOff`, `assignControlInvalidTeam`, `fullRound` · PM (06 ronda 1) `revealAnswer #1 en cara a cara`, `assignControl A` · E2E `game.spec.ts` | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-16 | ACC `RoundFlowAcceptanceTest`: `revealsAccumulateInPotNotScore`, `strikeIncrementsCounter`, `fullRound` · PM `revealAnswer #2`, `markStrike 1/2` · E2E `game.spec.ts` | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-17 | ACC `RoundFlowAcceptanceTest`: `faceOffTopAnswerGivesControl`, `revealsAccumulateInPotNotScore`, `fullRound` · PM `revealAnswer #2 (AC-16, AC-17)` · E2E `game.spec.ts` (bote en la TV, marcador en 0) | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-18 | ACC `RoundFlowAcceptanceTest`: `threeStrikesMoveToSteal`, `revealAllEndsRoundForControllingTeam` · PM `markStrike 3 → STEAL` · E2E `game.spec.ts` (3 X → robo; ronda 2 revela todo) | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-19 | ACC `RoundFlowAcceptanceTest`: `successfulStealGivesPotToRival`, `failedStealKeepsPotForControllingTeam` (con `potWinner`) · `AcceptanceTestBase.playRoundKeepingPot` · PM `revealAnswer #3 en robo`, `markStrike en robo` · E2E `game.spec.ts` (robo fallido ronda 1, acertado ronda 3) | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-20 | ACC `RoundFlowAcceptanceTest`: `roundEndRevealsWholeBoardPublicly`, `revealAllEndsRoundForControllingTeam` · `GameEndAcceptanceTest.multipliersPerRound` (bote × multiplicador al cierre) · PM `revealAnswer #3 en robo` · E2E `game.spec.ts` ("Fin de la ronda: … se lleva N puntos") | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-21 | ACC `GameEndAcceptanceTest.multipliersPerRound` (3 → 1-2-3, 4 → 1-1-2-3, 5 → 1-1-1-2-3) · PM (07/08) `nextRound → ×2 / ×3` · E2E `game.spec.ts` ("Puntos ×2/×3") · UNIT T103 `GameRules.multiplier` | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-22 | ACC `GameEndAcceptanceTest`: `gameOverWhenTargetReached`, `gameOverAfterLastRound`, `tieAtTheEnd`, `winnerNullBeforeGameOver` · PM `nextRound tras la última → GAME_OVER` · E2E `game.spec.ts` ("¡Gana …!" / "¡Empate!") | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-23 | ACC `GameEndAcceptanceTest.rematchReturnsToLobby` · `StateTransitionsAcceptanceTest` (rematch fuera de GAME_OVER → 409) · PM `rematch → LOBBY`, `rematch antes de GAME_OVER → 409` · E2E `game.spec.ts` ("Revancha") | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-24 | E2E `game.spec.ts` ("¡Tu equipo responde!", "Responde el otro equipo…", "¡Robo! Tu equipo tiene una sola oportunidad.", "Fin de la ronda", marcador) · UNIT `PlayPage` (Vitest) | E2E | ✅ pasa (ACC/E2E/PM) |
| AC-25 | ACC `DataLeakAcceptanceTest`: `restSnapshotHasNoHiddenAnswers` (FACE_OFF/PLAYING/STEAL), `stompSnapshotsHaveNoHiddenAnswers` (todos los snapshots de `/state`; un jugador no recibe `/presenter`) · `RealtimeAcceptanceTest.stompPresenterTopicRequiresPresenterToken` · PM `getRoom sin fuga de respuestas` · E2E `game.spec.ts` (`expectNoHiddenAnswers` en TV y celulares) | API · STOMP · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-26 | ACC `PresenterSecurityAcceptanceTest`: `missingTokenIs401`, `otherRoomTokenIs403`, `bogusTokenIsRejected` (las 8 operaciones protegidas), `playerTokenIsNotPresenterToken`, `questionBankRequiresPresenter`, `validTokenWorks` · `RealtimeAcceptanceTest.stompPresenterTopicRequiresPresenterToken` · PM (05 seguridad) | API · STOMP · Contrato | ✅ pasa (ACC/E2E/PM) |
| AC-27 | ACC `RealtimeAcceptanceTest`: `nonDesignatedBuzzIsRejected` (`NOT_YOUR_TURN`), `designatedBuzzOutsideFaceOffIsRejected` y `buzzInLobbyIsRejected` (`INVALID_STATE`), `errorsArePrivate`, `onlyFirstBuzzWinsUnderConcurrency` (ganador ∈ designados) | STOMP | ✅ pasa (ACC/E2E/PM) |
| AC-28 | UNIT `room/RoomExpiryJobTest` (Clock manual: 119 min abierta, 121 min cerrada) — sin caja negra (C-7) | UNIT | ✅ pasa |
| AC-29 | E2E `smoke.spec.ts` "la pantalla de inicio muestra los QR…", `game.spec.ts` (título, código y QR "Presentador"/"Jugadores") | E2E | ✅ pasa (ACC/E2E/PM) |
| AC-30 | ACC `PresenterClaimAcceptanceTest`: `claimReturnsWorkingPresenterToken`, `claimIsSingleUse`, `invalidClaim`, `claimFromOtherRoom`, `claimUnknownRoom` · PM (03 presentador) `claimPresenter`, `… otra vez → 409 CLAIM_USED`, `… inválido → 403` · E2E `game.spec.ts` (canje, URL limpia, "Presentador conectado ✓", segundo dispositivo rechazado) | API · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-31 | E2E `game.spec.ts` (QR de jugadores visible tras unirse los jugadores y de nuevo tras la revancha) | E2E | ✅ pasa (ACC/E2E/PM) |
| AC-32 | ACC `PresenterClaimAcceptanceTest.presenterConnectedFlag` · `RealtimeAcceptanceTest.presenterConnectedIsBroadcast` (true al canjear + `PRESENTER_CLAIMED`) · PM `getRoom` (false), `claimPresenter` (true) · E2E `game.spec.ts` | API · STOMP · Contrato · E2E | ✅ pasa (ACC/E2E/PM) |
| AC-33 | ACC `PresenterClaimAcceptanceTest`: `regenerateInvalidatesPrevious`, `screenKeepsItsToken`, `regenerateRevokesPreviousPhone` (token del celular anterior → 403, `presenterConnected=false`), `screenTokenSurvivesAnyRegeneration` · `RealtimeAcceptanceTest.presenterConnectedIsBroadcast` (false por STOMP al regenerar) · PM `regeneratePresenterClaim`, `getRoom tras regenerar`, `getPresenterView con el celular anterior → 403`, `… laptop sigue OK`, `claimPresenter con el QR nuevo` · E2E `game.spec.ts` "AC-33 la TV genera un QR de presentador nuevo…" | API · STOMP · Contrato · E2E | ✅ pasa (ACC/E2E/PM) (aclarado en spec v0.3.0) |
| AC-34 | E2E `game.spec.ts` (misma ruta `/screen/<code>`, desaparecen los QR, aparece el tablero "Respuestas") | E2E | ✅ pasa (ACC/E2E/PM) |

## Tabla de transiciones (data-model.md)
| Transición | Prueba |
|---|---|
| Válidas (todas) | `RoundFlowAcceptanceTest`, `GameEndAcceptanceTest`, `StateTransitionsAcceptanceTest.stealTransitionsAreValid` |
| Inválidas por estado → 409 `INVALID_STATE`, sin cambiar `version` | `StateTransitionsAcceptanceTest`: `lobby`, `faceOff`, `playing`, `revealTwice`, `steal`, `roundEnd`, `gameOver` |
| `version` crece en cada cambio | `StateTransitionsAcceptanceTest.versionIncreasesOnEveryChange`, `RealtimeAcceptanceTest.broadcastVersionsIncrease` |

## Cómo ejecutar
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
(cd backend && ./mvnw -B verify -Pacceptance)          # solo las de aceptación (@Tag("acceptance"))
(cd frontend && npm run e2e)                           # backend :8080 + frontend :5173 arriba
python3 postman/scripts/generate_collection.py
postman collection run postman/collections/cien-mexicanos.postman_collection.json \
  -e postman/environments/local.postman_environment.json   # backend recién levantado, en orden
```
