# Plan técnico — 002 Experiencia de juego

Respeta `../constitution.md` y las decisiones D1…D10 de `../001-juego-base/plan.md`.

## Decisiones
| # | Decisión | Motivo |
|---|---|---|
| D11 | `activeTeam`, `strikesByTeam` y `turnDeadline` viven en el dominio puro (`Round`/`Room`) y se calculan ahí | Mismas reglas probadas sin Spring; el cliente solo dibuja |
| D12 | El dominio recibe el "ahora" como parámetro (`Instant now`) en cada comando que fija `turnDeadline` | Dominio puro y determinista en pruebas |
| D13 | `TurnTimerScheduler` (Spring `TaskScheduler`) agenda un único vencimiento por sala; al disparar ejecuta `room.expireTurn(deadline, now)` bajo el lock de la sala (vía `RoomCommandRunner`). El comando solo actúa si el `turnDeadline` vigente es el mismo que se agendó | Evita X dobles y carreras con acciones manuales (AC-49) |
| D14 | Tras cada comando, `RoomCommandRunner` reagenda o cancela el vencimiento según el `turnDeadline` resultante | Un solo punto que sincroniza dominio y temporizador |
| D15 | Sonidos sintetizados con Web Audio API en `frontend/src/shared/audio/` (osciladores y envolventes; nada de archivos) | Originales, sin CDNs, funcionan sin internet (constitución §IV, §VII) |
| D16 | Disparadores de sonido y vibración = eventos de `/topic/rooms/{code}/events` (no diferencias de snapshot) | Un sonido por evento, sin repeticiones al reconectar |
| D17 | Cuenta regresiva en el cliente con `requestAnimationFrame`/intervalo de 250 ms sobre `turnDeadline`; el desfase de reloj se corrige con el header `Date` de la última respuesta REST | Las laptops y celulares no tienen el reloj sincronizado |

## Cambios de modelo (en memoria)
```
Room:  + turnSeconds (0 | 10..60), autoStrikeOnTimeout, soundEnabled
Round: + activeTeam?, strikesByTeam {A, B}, turnDeadline?
```
Sin cambios en SQLite (no hay migración V3).

## Flujo del temporizador
1. Comando (revelar, X, buzz, asignar control…) → el dominio actualiza `activeTeam` y `turnDeadline = now + turnSeconds` (o null).
2. `RoomCommandRunner` → `TurnTimerScheduler.reschedule(code, turnDeadline)` (cancela el anterior).
3. Al vencer → `expireTurn(expectedDeadline, now)` bajo el lock → publica `TIMER_EXPIRED` y, si corresponde, la X automática con sus eventos (`STRIKE`, `STEAL_STARTED`, `ROUND_ENDED`…).
4. Broadcast del snapshot como cualquier otro comando.

## Archivos
- Backend: `game/domain/{Round,Room,GameRules,ErrorCode}.java`, nuevo `game/TurnTimerScheduler.java`, `room/{RoomCommandRunner,RoomService,RoomController}.java`, `common/api/*` y `RoomProjections`.
- Frontend: `shared/audio/{synth.ts,useGameSounds.ts}`, `shared/game/display.ts`, `features/screen/{Board,StartScreenPage}.tsx`, `features/presenter/{CreateRoomPage,HostPage}.tsx`, `features/player/PlayPage.tsx`, nuevo `shared/ui/Countdown.tsx`.
- Pruebas: dominio (`RoomRulesTest`, `RoomStateMachineTest` o nuevos), `TurnTimerSchedulerTest` con `Clock` controlado, aceptación en `acceptance/`, Vitest, Playwright.
