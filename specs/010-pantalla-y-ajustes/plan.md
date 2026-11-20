# Plan — 010 Pantalla encendida, sonido y turno de 10 s

## Decisiones
- **D1 `nosleep.js` 0.12.0 (MIT).** Usa `navigator.wakeLock` cuando existe y, si no, un video mudo en bucle. Es necesario porque el juego corre por HTTP, donde la API estándar no está disponible.
- **D2 Hook `useKeepScreenOn`** (`frontend/src/shared/device/keepScreenOn.ts`). Intenta al montar y con cada gesto, que es inofensivo si ya está activo. Vuelve a pedirlo en `visibilitychange` y lo libera al desmontar. Los fallos se ignoran.
- **D3 Gestos compartidos.** `GESTURES` pasa a `shared/device/gestures.ts` y lo usan el audio (AC-52) y la pantalla (AC-130).
- **D4 Sonido opt-out.** `getSoundPreference()` devuelve `true` salvo con `'off'`, y `setSoundPreference(false)` guarda `'off'`. `useSoundActivation` no cambia: con sonido "deseado" ya desbloquea en el primer gesto.
- **D5 Turno de 10 s.** Cambia en `GameRules.DEFAULT_TURN_SECONDS`, `DEFAULT_SETTINGS.turnSeconds` y `openapi.yaml` (`default: 10`, v0.9.0). Se regeneraron `schema.d.ts` y la colección Postman.
