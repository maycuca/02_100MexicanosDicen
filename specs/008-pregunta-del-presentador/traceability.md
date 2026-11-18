# Trazabilidad — 008 Pregunta del presentador (T1322)

Matriz `AC → prueba(s) → nivel → estado`. Contrato de referencia: `specs/001-juego-base/contracts/` **v0.8.0**.

**Última verificación (2026-11-18):**

| Suite | Resultado |
|---|---|
| `./mvnw verify` | en verde |
| `./mvnw verify -Pacceptance` | en verde |
| Vitest | 145/145 |
| Playwright | 34 pasan, 16 omitidas a propósito |
| Postman CLI | 122 peticiones, 217 aserciones, 0 fallos |

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-119 | Vitest `presenter.test.tsx` `spec 008 AC-119 · AC-120 …` · captura Pixel 7 | Unit · Manual | ✅ |
| AC-120 | Vitest `spec 008 AC-119 · AC-120 …` (Revelados, ×2, Control con color; sin ficha de temporizador) · e2e `turns.spec.ts` (3 fichas sin desbordes) · capturas a 320, 375 px y Pixel 7 · e2e `game.spec.ts` `bote()` lee la ficha "Revelados" · e2e `turns.spec.ts` ficha "Temporizador" | Unit · E2E | ✅ |
| AC-121 | Vitest `spec 008 AC-121 · AC-122 …` (habilitado en el cara a cara) y `spec 008 AC-121 con una respuesta revelada …` (deshabilitado con nota) · e2e `turns.spec.ts` | Unit · E2E | ✅ |
| AC-122 | `ChangeQuestionTest` (dominio: reinicio del cara a cara, sin repetir, errores) · `RestApiTest.changeQuestion` · `ChangeQuestionAcceptanceTest` (QUESTION_CHANGED por STOMP, 5 cambios sin repetir, 401/403/409) · e2e la TV muestra la pregunta nueva · Postman carpeta "13 spec 008" | Unit · API · E2E · Contrato | ✅ |
