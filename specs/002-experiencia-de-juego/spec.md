# Spec 002 — Experiencia de juego: equipo en turno, strikes por equipo, temporizador y sonidos

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-17 |
| Depende de | `specs/001-juego-base` (reglas, estados y clarificaciones C-1…C-10 siguen vigentes) |
| Contrato | `specs/001-juego-base/contracts/` **v0.4.0** (aditivo) |

## 1. Resumen
Hacer más clara y emocionante la partida en la TV y en los celulares:
- **Equipo en turno** siempre visible.
- **Strikes (X) del equipo en turno**: las X del presentador se asignan y se muestran en el equipo que está respondiendo.
- **Temporizador** de turno con X automática al vencer.
- **Sonidos** en la TV y **vibración** en los celulares.

## 2. Historias y criterios de aceptación

### US-11 Equipo en turno visible
- **AC-35** El servidor calcula `round.activeTeam` (A, B o null) según esta tabla y lo incluye en todos los snapshots:

  | Estado | `activeTeam` |
  |---|---|
  | `FACE_OFF` sin buzz | null (ambos jugadores designados pueden presionar) |
  | `FACE_OFF` con buzz | equipo del ganador del buzz; cada respuesta (revelar o X) en el cara a cara pasa el turno al otro equipo hasta que el presentador asigna el control |
  | `PLAYING` | `controllingTeam` |
  | `STEAL` | el equipo rival (el que roba) |
  | `LOBBY`, `ROUND_END`, `GAME_OVER` | null |
- **AC-36** Cada cambio de `activeTeam` publica el evento `TURN_CHANGED` (con `team`) y sube `version`.
- **AC-37** En la TV el panel del equipo en turno se resalta (borde y brillo de su color y la etiqueta "EN TURNO") y el otro se atenúa. Con `activeTeam` null ninguno se resalta.
- **AC-38** En el celular del jugador se muestra "¡Le toca a tu equipo!" si su equipo está en turno, o "Turno de <nombre del equipo>" si no.
- **AC-39** En el panel del presentador se muestra el equipo en turno.

### US-12 Strikes del equipo en turno
- **AC-40** `POST .../rounds/current/strike` aplica la X a `activeTeam`. Si `activeTeam` es null → `409 NO_ACTIVE_TEAM` (incluye `FACE_OFF` sin buzz).
- **AC-41** `round.strikesByTeam` (`{ A, B }`) cuenta las X **que cuentan** en la ronda: las del equipo con control en `PLAYING` (0–3) y la del equipo que roba en `STEAL` (0–1). Siempre `strikesByTeam[controllingTeam] == round.strikes`.
- **AC-42** Una X en `FACE_OFF` no se acumula (C-3), pero publica `STRIKE` con `team` = equipo que falló y pasa el turno al rival (AC-35).
- **AC-43** El evento `STRIKE` incluye `team` (quién recibió la X) y `strikes` (las X de ese equipo en `strikesByTeam`).
- **AC-44** La TV muestra las X debajo del panel de cada equipo y la animación grande de X con el color del equipo que la recibió.
- **AC-45** En el panel del presentador el botón dice "X para <nombre del equipo en turno>" y está deshabilitado si no hay equipo en turno.

### US-13 Temporizador de turno
- **AC-46** Al crear la sala se configura `turnSeconds` (0 = sin temporizador; si no, 10–60; por defecto 20) y `autoStrikeOnTimeout` (por defecto `true`).
- **AC-47** Si `turnSeconds > 0` y hay `activeTeam`, el servidor fija `round.turnDeadline = ahora + turnSeconds` (ISO-8601 UTC) al cambiar el equipo en turno y después de cada revelar o X. Si no hay equipo en turno, `turnDeadline` es null.
- **AC-48** Al vencer `turnDeadline` el servidor publica `TIMER_EXPIRED` (con `team`). Si `autoStrikeOnTimeout` es `true`, aplica una X al equipo en turno con las mismas reglas que una X manual (AC-40…AC-43); si es `false`, solo deja `turnDeadline` en null y espera al presentador.
- **AC-49** Una acción del presentador que llegue antes del vencimiento cancela el temporizador pendiente; nunca se aplican dos X por el mismo vencimiento.
- **AC-50** TV y celulares muestran la cuenta regresiva calculada con `turnDeadline` (el servidor es la autoridad); los últimos 5 s se muestran en rojo.

### US-14 Sonidos y vibración
- **AC-51** Solo la TV reproduce sonido. Los sonidos son originales, sintetizados en el navegador (Web Audio API), sin archivos de terceros ni CDNs: buzzer ganado, respuesta revelada, X, inicio de robo, fin de ronda, ganador de la partida y un tic en cada uno de los últimos 5 s del temporizador.
- **AC-52** La pantalla de inicio muestra el botón "Activar sonido" (los navegadores bloquean el audio hasta que alguien interactúa). Una vez activado, la TV recuerda la preferencia en ese navegador.
- **AC-53** El presentador puede silenciar o reactivar el sonido de la TV con `POST /api/rooms/{code}/sound`; el estado viaja en `RoomState.soundEnabled` (por defecto `true`) y publica `SOUND_TOGGLED`.
- **AC-54** El celular del jugador vibra (`navigator.vibrate`, si existe) cuando gana el buzzer, cuando el turno pasa a su equipo y cuando su equipo recibe una X. En navegadores sin vibración no pasa nada.

## 2b. Clarificaciones (2026-11-17, revisión de QA y backend)
| # | Tema | Decisión |
|---|---|---|
| C-11 | X fuera de ronda | En `LOBBY`, `ROUND_END` y `GAME_OVER` la X sigue dando `409 INVALID_STATE` (C-6). `NO_ACTIVE_TEAM` solo aplica en `FACE_OFF` sin buzz. |
| C-12 | Revelar sin buzz | En `FACE_OFF` sin buzz, revelar (permitido por C-2) no asigna turno: `activeTeam` sigue en null. |
| C-13 | `TURN_CHANGED` a null | También se publica cuando el turno pasa a null (p. ej. fin de ronda), sin el campo `team`. |
| C-14 | `strikesByTeam` al cerrar | Se conserva en `ROUND_END` y se reinicia a `{A:0, B:0}` en la ronda siguiente. |
| C-15 | `/sound` sin cambio | Enviar el mismo valor que ya tiene no publica evento ni sube `version`. Se permite en cualquier estado. |
| C-16 | Vencimiento sin auto-X | Publica `TIMER_EXPIRED`, pone `turnDeadline` en null y sube `version`. |
| C-17 | Asignar control | Siempre reinicia `turnDeadline`, aunque el equipo ya estuviera en turno (sin `TURN_CHANGED` si no cambia). |
| C-18 | Inactividad | Un vencimiento del temporizador no cuenta como actividad para el cierre por inactividad (AC-28). |
| C-19 | `ANSWER_REVEALED` en el cara a cara | Su `team` es el equipo en turno al momento de revelar. |
| C-20 | Recordar sonido (AC-52) | La TV guarda la preferencia; tras recargar, el primer toque o tecla reanuda el audio (exigencia de los navegadores). |

## 3. Fuera de alcance
- Ronda final rápida (propuesta para la spec 003).
- Sonido en los celulares.
- Elegir o subir sonidos personalizados.
