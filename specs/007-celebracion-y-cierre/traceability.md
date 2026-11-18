# Trazabilidad — 007 Celebración y cierre (T1212)

Matriz `AC → prueba(s) → nivel → estado`. Sin cambios de contrato (v0.7.0) ni de backend.

**Última verificación (2026-11-18):**

| Suite | Resultado |
|---|---|
| Vitest | 142/142 |
| Playwright | 34 pasan, 16 omitidas a propósito |
| lint y build | limpios |

También hay revisión visual en capturas de la TV (1920×1080) y del celular (Pixel 7).

Ubicaciones:
- **VT-S** = `frontend/src/features/screen/screen.test.tsx`.
- **VT-P** = `frontend/src/features/presenter/presenter.test.tsx`.
- **VT-J** = `frontend/src/features/player/player.test.tsx`.
- **VT-A** = `frontend/src/shared/audio/audio.test.tsx`.
- **E2E-G** = `frontend/e2e/game.spec.ts`.
- **E2E-T** = `frontend/e2e/turns.spec.ts`.
- **E2E-C** = `frontend/e2e/captain.spec.ts`.

| AC | Prueba(s) | Nivel | Estado |
|---|---|---|---|
| AC-110 | VT-S `spec 007 AC-110 …` (span del equipo con `var(--cm-team-b)`, puntos en `.status-pts`) · VT-S `AC-86`/`AC-103` (texto completo) · E2E-G y E2E-T (`Responde …`, `Fin de la ronda: … se lleva N puntos`, `¡Pasa a …!`) · captura | Unit · E2E · Manual | ✅ |
| AC-111 | VT-J `AC-66 …` (habilitado con el nombre sugerido), `spec 007 AC-111 …` · E2E-C (sigue deshabilitado con nombre repetido) | Unit · E2E | ✅ |
| AC-112 | VT-P `spec 007 AC-112 …` · E2E-G `bote()` lee "Revelados:" | Unit · E2E | ✅ |
| AC-113 | VT-P `AC-91 · spec 007 AC-113 …` · VT-S `… AC-113 …` (TV borra token y QR) · VT-J `spec 007 AC-113 …` (jugador borra su token) · E2E-T presentador, TV y jugador en `/gracias`; "Menú principal" lleva al título | Unit · E2E | ✅ |
| AC-114 | VT-S `spec 007 AC-114 …` (confeti, trofeo, ganador a color, marcador final, "★ CAMPEÓN ★") · E2E-G marcador final por `aria-label` · captura | Unit · E2E · Manual | ✅ |
| AC-115 | VT-A `AC-51 cada sonido …` (incluye la música `winner` con ruido filtrado) · `SOUND_FOR_EVENT.GAME_OVER = 'winner'` | Unit | ✅ |
| AC-116 | VT-J `spec 007 AC-116 …` ("¡Ganaron!" / "¡Buen juego!" con puntos) · E2E-G resultado en ambos celulares · vibración `VIBRATION.win` en `GAME_OVER` del propio equipo | Unit · E2E | ✅ |
| AC-117 | VT-S `spec 007 AC-117 …` (Ana en rojo, Beto en verde, "¡Beto ganó el buzzer!" en verde) · VT-S `AC-08`/`AC-37` (texto completo "Cara a cara: Ana contra Beto") · E2E-G "¡Lupita ganó el buzzer!" | Unit · E2E | ✅ |
| AC-118 | VT-J `spec 007 AC-113 …` (sin "Menú principal"; "Cerrar ventana" llama a `window.close()` y muestra el aviso) · E2E-T (la TV muestra el aviso porque su pestaña no la abrió un script) | Unit · E2E | ✅ |
| AC-123 | VT-S `spec 005 AC-91 · spec 007 AC-113 · AC-123 …` (la TV va a `/`, borra token y QR, sin "¡Gracias por jugar!") · E2E-T (TV en `/` con EMPEZAR; presentador y jugador en `/gracias`; "Cerrar ventana" en el presentador) | Unit · E2E | ✅ |
