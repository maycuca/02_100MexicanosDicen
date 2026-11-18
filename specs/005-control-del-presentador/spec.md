# Spec 005 — Control del presentador: temporizador manual, cara a cara sin X y menú principal

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-18 |
| Depende de | specs 001–004 (siguen vigentes salvo lo que se reemplaza aquí) |
| Contrato | `specs/001-juego-base/contracts/` **v0.6.0** (aditivo) |

## 1. Resumen
- El temporizador del turno ya **no arranca ni se reinicia solo**: lo maneja el presentador con **Iniciar / Parar / Restablecer**.
- En el **cara a cara** fallar **no es X**: el turno pasa al otro equipo y, si este también falla, regresa al primero. Las X solo cuentan cuando termina el cara a cara.
- La TV se limpia:
  - Sin la etiqueta "Bote", sin botón de sonido y sin botón "QR de presentador" en el tablero.
  - Suena un **júbilo** cuando se revela una respuesta correcta.
- El presentador puede **regresar al menú principal**. Antes ve una advertencia; si confirma, se cierra la sala y el juego se reinicia.

## 2. Historias y criterios de aceptación

### US-20 Temporizador manual
- **AC-79** El temporizador ya no arranca ni se reinicia de forma automática. Ni el buzzer, ni una respuesta, ni una X, ni asignar el control, ni el vencimiento lo inician. Al empezar cada ronda queda **detenido con la duración completa** (`turnSeconds`). Reemplaza el "reinicia su vencimiento" de AC-47 y AC-49.
- **AC-80** Si la sala tiene temporizador (`turnSeconds > 0`) y el estado es `FACE_OFF`, `PLAYING` o `STEAL`, el presentador ve tres botones junto a "En turno":
  - **Iniciar**: dice **Reanudar** si está parado a medias. Corre desde el tiempo que queda; si queda 0, desde la duración completa.
  - **Parar**: congela el tiempo que queda.
  - **Restablecer**: lo deja detenido con la duración completa, listo para el siguiente concursante.
- **AC-81** `POST /api/rooms/{code}/rounds/current/timer` con `{ "action": "START" | "PAUSE" | "RESET" }` (`X-Presenter-Token`) devuelve `PresenterRoomState` y publica `TIMER_CHANGED`. Errores:
  - Sin token → `401 UNAUTHORIZED`.
  - Token inválido → `403 FORBIDDEN`.
  - Sala inexistente → `404`.
  - Fuera de `FACE_OFF`/`PLAYING`/`STEAL` → `409 INVALID_STATE`.
  - Sala sin temporizador → `409 TIMER_DISABLED`.
  - `action` ausente o inválida → `400 VALIDATION_ERROR`.

  `START` con el temporizador corriendo, `PAUSE` con él detenido y `RESET` cuando ya está completo y detenido responden 200 sin evento ni nueva `version`.
- **AC-82** `RoundState` agrega dos campos:
  - `timerRunning` (boolean).
  - `timerRemainingMs` (entero ≥ 0): con el temporizador detenido, el tiempo que queda; mientras corre, lo que quedaba al iniciarlo. La cuenta regresiva usa `turnDeadline`.

  `turnDeadline` solo trae valor mientras corre. La TV, el presentador y los celulares muestran el tiempo detenido como número fijo y la cuenta regresiva mientras corre.
- **AC-83** Al vencer, el temporizador se detiene en 0 (no se reinicia) y se publica `TIMER_EXPIRED`. Si "X automática" está activa, se aplica el fallo del equipo en turno con las reglas de AC-85 (cara a cara) o de AC-41…AC-43 (X).
- **AC-84** Al terminar la ronda, al pasar a la siguiente, en `GAME_OVER` y en la revancha, el temporizador queda detenido y completo.

### US-21 Cara a cara sin X
- **AC-85** En `FACE_OFF`, el fallo del equipo en turno **no es X**, ya sea con el botón del presentador o al vencer el tiempo con X automática:
  - No cuenta en `strikes` ni en `strikesByTeam`.
  - Se publica `FACE_OFF_MISS` (con `team`) en lugar de `STRIKE`.
  - El turno pasa al rival. Si el rival también falla, el turno vuelve al primero, y así hasta que el presentador asigne el control.

  Reemplaza AC-42 y C-3. Las X solo existen en `PLAYING` y `STEAL`.
- **AC-86** En `FACE_OFF`, el botón del presentador dice **"Falló <equipo> · pasa el turno"** (mismo endpoint `markStrike`). La TV no muestra la X gigante: su línea de estado dice "¡Pasa a <rival>!" y suena un aviso corto distinto al de la X.

### US-22 TV más limpia y con júbilo
- **AC-87** En el tablero, los puntos de la ronda se muestran como un número sin la etiqueta "Bote" (`aria-label` "Puntos de la ronda").
- **AC-88** El tablero ya no tiene botón de sonido ni botón "QR de presentador". Esto reemplaza la parte de AC-52 y AC-70 sobre el tablero y AC-33 durante la partida; en `LOBBY` el "Nuevo QR de presentador" sigue.
  - El sonido se activa en AJUSTES y el presentador puede silenciar la TV (AC-53).
  - Tras recargar, el primer toque o tecla en la TV reanuda el audio.
- **AC-89** Al revelar una respuesta correcta, la TV toca un **júbilo**: fanfarria corta más aplausos y gritos sintetizados con Web Audio (original). Sustituye al sonido de revelar anterior y respeta la preferencia de sonido y el silencio del presentador.

### US-23 Regresar al menú principal
- **AC-90** La pantalla del presentador tiene el botón **"Menú principal"**. Al pulsarlo aparece un diálogo de advertencia: "¿Regresar al menú principal? Se cerrará la sala y el juego se reiniciará; los jugadores tendrán que volver a unirse." Tiene dos botones: **Cancelar** y **Sí, regresar**.
- **AC-91** Al confirmar se llama `DELETE /api/rooms/{code}` (`X-Presenter-Token`), que responde **204**. La sala se elimina, se cancela su temporizador y antes se publica el evento `ROOM_CLOSED`. Errores: 401 sin token, 403 con token inválido, 404 si la sala no existe. Después:
  - El presentador va a `/` (pantalla de título).
  - La TV, al recibir `ROOM_CLOSED`, vuelve a `/`.
  - Los jugadores ven "El presentador cerró la partida" con el botón "Salir", que los lleva a `/`.

### US-27 Ajustes visuales (2026-11-18)
- **AC-106** Los controles del temporizador del presentador son **solo iconos en una sola fila**:
  - ▶ Iniciar / Reanudar.
  - ❚❚ Parar.
  - ↺ Restablecer.

  Cada botón tiene nombre accesible (`aria-label`) y `title` con el texto de la acción; reemplaza los botones con texto de AC-80.
- **AC-107** "Silenciar la TV" se reemplaza por un **switch** "Sonido de la TV": encendido = suena; apagado = silenciada (AC-53). Es un `<input type="checkbox" switch role="switch">` con su `<label>`, con estilo propio (`appearance: none`), así que se ve igual en todos los navegadores sin dependencias nuevas.
- **AC-108** El número de la cuenta regresiva queda **centrado** en un círculo perfecto, en la TV, el presentador y el celular: `aspect-ratio: 1` y `text-box: trim-both cap alphabetic`. Este último es una mejora progresiva: donde no existe, conserva el centrado normal.
- **AC-109** En el celular del jugador, los puntos de la ronda se muestran sin la etiqueta "Bote" (`aria-label` "Puntos de la ronda"), igual que en la TV (AC-87).

## 3. Clarificaciones
| # | Tema | Decisión |
|---|---|---|
| C-25 | Cambio de turno con el temporizador corriendo | No lo toca: el presentador decide si lo para o lo restablece. |
| C-26 | `START` sin equipo en turno (cara a cara antes del buzzer) | Se permite. Si vence sin equipo en turno, solo se publica `TIMER_EXPIRED` (sin `team`). |
| C-27 | Parar / Reanudar | Solo afectan al temporizador. Revelar, X y control siguen disponibles. |

## 4. Fuera de alcance
- Pausar la partida completa.
- Reiniciar la ronda actual.
- Confirmación desde la TV.
