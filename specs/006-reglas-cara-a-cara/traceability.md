# Trazabilidad — 006 Reglas del cara a cara (T1123)

Matriz `AC → prueba(s) → nivel → estado`. Contrato de referencia: `specs/001-juego-base/contracts/` **v0.7.0**.

**Última verificación (2026-11-18):**

| Suite | Resultado |
|---|---|
| `./mvnw verify` | en verde |
| `./mvnw verify -Pacceptance` | en verde |
| Vitest | 134/134 |
| Playwright | 34 pasan, 16 omitidas a propósito |
| Postman CLI | 111 peticiones, 202 aserciones, 0 fallos |

Ubicaciones:
- **DOM** = `backend/src/test/java/mx/cienmexicanos/game/domain/TurnRulesTest.java` (`FaceOffRules`, `ActiveTeam`, `Strikes`) y `RoomRulesTest.java`.
- **ACC-A** = `acceptance/ActiveTeamAcceptanceTest.java`.
- **ACC-R** = `acceptance/RoundFlowAcceptanceTest.java`.
- **ACC-S** = `acceptance/TeamStrikesAcceptanceTest.java`.
- **VT-P** = `frontend/src/features/presenter/presenter.test.tsx`.
- **VT-S** = `frontend/src/features/screen/screen.test.tsx`.
- **VT-J** = `frontend/src/features/player/player.test.tsx`.
- **E2E-T** = `frontend/e2e/turns.spec.ts`.
- **E2E-G** = `frontend/e2e/game.spec.ts`.
- **PM** = colección Postman (carpeta "06 ronda 1").

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-92 | DOM `topAnswerWins` · ACC-R `faceOffTopAnswerGivesControl` · helper `winFaceOff` (todas las suites de aceptación) · E2E-G rondas 1–3 sin "Asignar control" | Unit · API · E2E | ✅ |
| AC-93 | DOM `faceOffAlternates`, `exposesFirstAttempt` · ACC-A `faceOffRevealPassesTurn` · RoomStateMachine `faceOffStays` | Unit · API | ✅ |
| AC-94 | DOM `faceOffStrike`, `secondAnswersAfterMiss` · ACC-A `faceOffStrikePassesTurn` · E2E-T "Falló Equipo Rojo · pasa el turno" | Unit · API · E2E | ✅ |
| AC-95 | DOM `secondBeatsFirst`, `secondAnswersAfterMiss` · ACC-R `faceOffRivalWithHigherAnswerGetsControl`, `fullRound` · E2E-T Verde abre la #1 tras el fallo de Rojo | Unit · API · E2E | ✅ |
| AC-96 | DOM `faceOffAlternates` · ACC-R `faceOffRivalWithHigherAnswerGetsControl` (la #3 no supera la #2) | Unit · API | ✅ |
| AC-97 | DOM `faceOffMissAlternates`, `secondTimesOut`, `faceOffStrikesDoNotCarry` · ACC-A `faceOffStrikePassesTurn` · ACC-S `faceOffStrikesDoNotAccumulate` · E2E-T doble fallo → "Responde Equipo Rojo" | Unit · API · E2E | ✅ |
| AC-98 | DOM `expiryAlwaysMissesInFaceOff`, `secondTimesOut` (con "X automática" apagada) | Unit | ✅ |
| AC-99 | DOM `topAnswerWins` (eventos `CONTROL_ASSIGNED`), `faceOffMissAlternates` · ACC-A `turnChangedOnEveryChange` | Unit · API | ✅ |
| AC-100 | DOM `revealWithoutBuzzKeepsNull` · ACC-R `revealWithoutBuzzRejected` · ACC-S `revealAndControlWithoutBuzzStillWork` · REST `fullGame` · VT-P `AC-100 · AC-102 sin buzzer no se puede revelar` · PM `revealAnswer en cara a cara sin buzz → 409` | Unit · API · Contrato | ✅ |
| AC-101 | DOM `exposesFirstAttempt` · ACC-A `faceOffRevealPassesTurn` (`firstTeam`, `firstAnswerPosition`) · PM `assignControl A` (campos presentes) | Unit · API · Contrato | ✅ |
| AC-102 | VT-P `AC-102 primer intento …`, `AC-102 segundo intento …`, `habilita los controles …` (sin "Asignar control") · E2E-T pista y "· termina el cara a cara" | Unit · E2E | ✅ |
| AC-103 | VT-S `spec 006 AC-103 …` · VT-J `spec 006 AC-103 …` | Unit | ✅ |
| AC-104 | ACC-R `threeStrikesMoveToSteal`, `revealAllEndsRoundForControllingTeam` · E2E-G ronda 2 | API · E2E | ✅ |
| AC-105 | ACC-R `successfulStealGivesPotToRival`, `failedStealKeepsPotForControllingTeam` · E2E-G rondas 1 y 3 · PM rondas 1 y 2 | API · E2E · Contrato | ✅ |
