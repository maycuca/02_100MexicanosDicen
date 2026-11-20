# Tareas — 010 Pantalla encendida, sonido y turno de 10 s

## Fase 0
- [x] T1500 Spec, plan y contrato v0.9.0 (`turnSeconds.default: 10`); tipos y Postman regenerados; rama `feat/010-pantalla-y-ajustes`

## Frontend
- [x] T1501 [FE] `useKeepScreenOn` con `nosleep.js` en TV, presentador y jugadores (AC-129…AC-132)
- [x] T1502 [FE] Sonido activado por defecto (`cm:sound` opt-out) (AC-133)
- [x] T1503 [FE] AJUSTES con 10 s por defecto (AC-134)

## Backend
- [x] T1511 [BE] `GameRules.DEFAULT_TURN_SECONDS = 10` (AC-134)

## QA
- [x] T1521 [QA] Vitest `keepScreenOn.test.tsx`, `audio.test.tsx`, `screen.test.tsx`, `title.test.tsx`; JUnit `TurnRulesTest`, `RoomProjectionsTest`, `RestApiTest`; Postman; e2e `title.spec.ts` (AC-129…AC-134)
- [x] T1522 [QA] `traceability.md`
- [ ] T1523 [QA] Prueba manual en celulares (Android e iPhone) por HTTP: la pantalla no se apaga (AC-130)
