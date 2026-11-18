# Spec 006 — Reglas del cara a cara y del robo

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-18 |
| Depende de | specs 001–005 (siguen vigentes salvo lo que se reemplaza aquí) |
| Contrato | `specs/001-juego-base/contracts/` **v0.7.0** (aditivo) |

## 1. Resumen
El cara a cara se juega una sola vez por ronda y **se resuelve solo**: el servidor decide quién se queda con el turno según las respuestas y los fallos, sin que el presentador asigne el control. Después vienen las 3 X del equipo en turno y, si las completa sin abrir todo, el robo del otro equipo.

## 2. Historias y criterios de aceptación

### US-24 Cara a cara automático
- **AC-92** El equipo del jugador que gana el buzzer es el **primero**. Si abre la respuesta **#1**, se queda con el turno, termina el cara a cara y empieza la fase de 3 X.
- **AC-93** Si el primero abre una respuesta que **no es la #1**, el turno pasa al otro equipo (**segundo**).
- **AC-94** Si al primero se le acaba el tiempo del temporizador, o el presentador pulsa **"Falló <equipo> · pasa el turno"**, el turno pasa al segundo. Este botón se usa también cuando la respuesta dicha no está en el tablero o cuando no hay temporizador.
- **AC-95** Si el segundo abre una respuesta **más alta** que la del primero, se queda con el turno y termina el cara a cara. "Más alta" significa más puntos, es decir, una posición menor en el tablero. Si el primero no abrió ninguna, cualquier respuesta del segundo gana.
- **AC-96** Si el segundo abre una respuesta más baja que la del primero, el turno pasa al primero y termina el cara a cara.
- **AC-97** Si al segundo se le acaba el tiempo, o el presentador pulsa **"Falló <equipo> · termina el cara a cara"**, el turno pasa al otro equipo (el primero) y termina el cara a cara.
- **AC-98** En el cara a cara, el vencimiento del temporizador **siempre** cuenta como fallo, aunque "X automática" esté apagada. En `PLAYING` y `STEAL` sigue mandando "X automática" (AC-48).
- **AC-99** Al terminar el cara a cara se publican `CONTROL_ASSIGNED` y `TURN_CHANGED` y la sala pasa a `PLAYING` con ese equipo. Lo revelado en el cara a cara suma al bote (AC-17). Si ya se reveló todo, la ronda se cierra a favor de ese equipo (AC-18).
- **AC-100** En `FACE_OFF` no se puede revelar antes del buzzer: responde `409 NO_ACTIVE_TEAM`. Reemplaza la clarificación C-2 de la spec 001.
- **AC-101** `FaceOffState` agrega dos campos para que la TV, el presentador y los celulares muestren lo que hay que superar:
  - `firstTeam`: equipo del buzzer; null antes del buzzer.
  - `firstAnswerPosition`: posición que abrió el primero; null si no abrió ninguna.

### US-25 Vistas
- **AC-102** El presentador ya no ve los botones "Asignar control". En el cara a cara:
  - Revelar está deshabilitado hasta que alguien gane el buzzer.
  - Si el primero abrió una respuesta, ve la pista "<primero> abrió la #N: <segundo> necesita una más alta".
  - El botón de fallo dice "· pasa el turno" en el primer intento y "· termina el cara a cara" en el segundo.
- **AC-103** En el segundo intento, la TV dice "¡<segundo> debe superar la #N!", o "¡<segundo> gana el control con cualquier respuesta!" si el primero no abrió ninguna. Los celulares muestran el mismo texto.

### US-26 Fase de 3 X y robo (sin cambios, se reafirman)
- **AC-104** Después del cara a cara, el equipo en turno juega con 3 X. Abrir todas las respuestas antes de la 3.ª X le da la ronda (AC-18). Con 3 X sin haber abierto todo, el otro equipo solo puede robar (AC-18).
- **AC-105** En el robo, una sola respuesta correcta da el bote y la ronda al equipo que roba. Si el robo falla (X o tiempo con X automática), la ronda la gana el equipo de las 3 X (AC-19).

## 3. Clarificaciones
| # | Tema | Decisión |
|---|---|---|
| C-28 | `assignControl` | Se queda en el contrato como corrección manual del presentador en `FACE_OFF` (no aparece en la UI); lo usan Postman y las pruebas de aceptación. |
| C-29 | Mismo comportamiento para botón y tiempo | "Falló" del presentador y el vencimiento en `FACE_OFF` aplican exactamente la misma regla (AC-94, AC-97). |

## 4. Reemplaza
- AC-15 (el presentador asigna el control) → AC-92…AC-99.
- C-2 de la spec 001 (revelar sin buzzer) → AC-100.
- AC-85 y AC-86 de la spec 005 (fallos alternos sin fin) → AC-94 y AC-97. `FACE_OFF_MISS` se sigue publicando en cada fallo del cara a cara.
