# Trazabilidad — 010

| AC | Prueba | Tipo | Estado |
|---|---|---|---|
| AC-129 | Vitest `keepScreenOn.test.tsx` `AC-129 · AC-130 …`; hook en `StartScreenPage`, `HostPage`, `JoinPage` y `PlayPage` | Unit | ✅ |
| AC-130 | Vitest `AC-129 · AC-130 lo pide al montar y otra vez con cada toque, clic o tecla` · manual en celulares (T1523) | Unit · Manual | ✅ / ⏳ manual |
| AC-131 | Vitest `AC-131 al volver a la pestaña lo pide de nuevo; oculta, no` | Unit | ✅ |
| AC-132 | Vitest `AC-132 al salir de la página lo libera …` y `AC-132 si el navegador lo rechaza o no lo soporta …` | Unit | ✅ |
| AC-133 | Vitest `audio.test.tsx` `spec 010 AC-133 …` y modo privado · `screen.test.tsx` `spec 010 AC-133 sin preferencia guardada …` y `… con el sonido desactivado …` · `title.test.tsx` `AC-59 / AC-52 · spec 010 AC-133 …` | Unit | ✅ |
| AC-134 | JUnit `TurnRulesTest.defaults`, `RoomProjectionsTest.turnFields`, `RestApiTest.timerDefaults` · Vitest `title.test.tsx` `AC-58 …` · Postman `AC-46 AC-53 AC-134 por defecto …` · e2e `title.spec.ts` `DEFAULTS` | Unit · API · Contrato · E2E | ✅ (e2e pendiente de correr) |
