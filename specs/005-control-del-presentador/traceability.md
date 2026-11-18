# Trazabilidad — 005 Control del presentador (T1023)

Matriz `AC → prueba(s) → nivel → estado`. Contrato de referencia: `specs/001-juego-base/contracts/` **v0.6.0**.

**Última verificación (2026-11-18):**

| Suite | Resultado |
|---|---|
| `./mvnw verify` | en verde |
| `./mvnw verify -Pacceptance` | en verde |
| Vitest | 129/129 |
| Playwright | 34 pasan, 16 omitidas a propósito |
| Postman CLI | 110 peticiones, 200 aserciones, 0 fallos |

Ubicaciones:
- **DOM** = `backend/src/test/java/mx/cienmexicanos/game/domain/TurnRulesTest.java` (clases anidadas `ManualTimer`, `Expiry`, `Strikes`).
- **RUN** = `backend/src/test/java/mx/cienmexicanos/room/RoomTurnTimerTest.java`.
- **REST** = `backend/src/test/java/mx/cienmexicanos/api/RestApiTest.java`.
- **ACC-T** = `backend/src/test/java/mx/cienmexicanos/acceptance/TurnTimerAcceptanceTest.java`.
- **ACC-S** = `backend/src/test/java/mx/cienmexicanos/acceptance/TeamStrikesAcceptanceTest.java`.
- **VT-P** = `frontend/src/features/presenter/presenter.test.tsx`.
- **VT-S** = `frontend/src/features/screen/screen.test.tsx`.
- **VT-A** = `frontend/src/shared/audio/audio.test.tsx`.
- **E2E** = `frontend/e2e/turns.spec.ts`.
- **PM** = colección Postman, carpeta "12 spec 005".

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-79 | DOM `neverStartsByItself` · RUN `onlyStartSchedules` · ACC-T `deadlineFollowsActiveTeam` · REST `turnAndStrikes` · E2E temporizador manual (no arranca tras buzz/control) · PM `startGame: temporizador detenido y completo` | Unit · API · E2E · Contrato | ✅ |
| AC-80 | DOM `startPauseResumeReset`, `startAfterExpiry` · RUN `manualActionCancels` · ACC-T `manualTimerControls` · VT-P `AC-80 …` (3 pruebas) · E2E Iniciar/Parar/Reanudar/Restablecer · PM START/PAUSE/RESET | Unit · API · E2E · Contrato | ✅ |
| AC-81 | DOM `noOps`, `errors` · REST `timerEndpoint` · ACC-T `timerErrors` · PM errores 400/401/403/409 | Unit · API · Contrato | ✅ |
| AC-82 | Proyecciones `turnFields` · VT-S `AC-82 … número fijo` · VT-P `… muestra 20 detenido` · E2E nombre accesible "…, detenido" | Unit · E2E | ✅ |
| AC-83 | DOM `autoStrikePlaying`, `withoutAutoStrike`, `expiryWithoutActiveTeam` · RUN `expiresAndAutoStrikes` · ACC-T `expiryInFaceOffStrikesAndPassesTurn`, `expiryWithoutAutoStrike` · E2E se queda en 0 | Unit · API · E2E | ✅ |
| AC-84 | DOM `roundEndResets` · ACC-T `expiryInStealEndsRound` | Unit · API | ✅ |
| AC-85 | DOM `faceOffStrike`, `faceOffMissAlternates`, `autoStrikeFaceOff` · ACC-S `faceOffStrikeEvent` · E2E "Falló … · pasa el turno" | Unit · API · E2E | ✅ (la spec 006 cambia la alternancia) |
| AC-86 | VT-P `AC-39 · spec 005 AC-86 …` · VT-S `AC-86 … "¡Pasa a …!"` · E2E `¡Pasa a Equipo Verde!` sin X gigante | Unit · E2E | ✅ |
| AC-87 | VT-S `AC-87 … sin la etiqueta "Bote"` · e2e `game.spec.ts` (`Puntos de la ronda`) | Unit · E2E | ✅ |
| AC-88 | VT-S `AC-88 …` y `AC-51 … el primer toque reanuda el audio` · E2E tablero sin sonido ni QR | Unit · E2E | ✅ |
| AC-89 | VT-S `… júbilo al revelar …` (`reveal` con ruido filtrado) · VT-A `AC-51 cada sonido …` (incluye `miss`) | Unit | ✅ |
| AC-90 | VT-P `AC-90 "Menú principal" pide confirmación …` (foco en Cancelar) · E2E diálogo nativo, Esc lo cierra | Unit · E2E | ✅ |
| AC-91 | DOM `close` · RUN `closeRoom` · REST `closeRoom` · ACC-T `closeRoomBroadcasts` · VT-P `AC-91 …` · VT-S `AC-91 …` · E2E TV y presentador vuelven a `/`, el jugador ve la sala cerrada · PM `closeRoom` 401/403/204/404 | Unit · API · E2E · Contrato | ✅ |
| AC-106 | VT-P `AC-80 · AC-82 detenido y completo …` (botones sin texto, `aria-label` = `title`) · E2E temporizador manual (botones por nombre accesible) | Unit · E2E | ✅ |
| AC-107 | VT-P `AC-53 · AC-107 el switch "Sonido de la TV" …` · E2E `AC-70 · AC-53 …` (switch on/off y `soundEnabled`) | Unit · E2E | ✅ |
| AC-108 | Revisión visual en capturas 1920×1080 (TV) y Pixel 7 (presentador y jugador): círculo perfecto y número centrado | Manual | ✅ |
| AC-109 | VT-J `spec 005 AC-109 …` | Unit | ✅ |
