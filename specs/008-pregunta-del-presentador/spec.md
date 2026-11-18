# Spec 008 — Pregunta destacada y "Cambiar pregunta" en el presentador

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-18 |
| Depende de | specs 001–007 |
| Contrato | `specs/001-juego-base/contracts/` **v0.8.0** (aditivo) |

## 1. Resumen
En la pantalla del presentador, la tarjeta de la ronda destaca la pregunta y muestra sus indicadores como fichas fáciles de leer. Además, el presentador puede **cambiar la pregunta** de la ronda si ya había salido en otra partida.

## 2. Historias y criterios de aceptación

### US-33 Tarjeta de la ronda
- **AC-119** La pregunta se muestra en un panel destacado (texto grande, con borde y fondo propios) en la parte superior de la tarjeta de la ronda.
- **AC-120** Los indicadores se muestran como fichas con etiqueta pequeña y valor grande:
  - Revelados.
  - Multiplicador.
  - Control (con el color del equipo; "—" si aún no hay).

  Van en una sola fila de tres. El temporizador no tiene ficha: ya está en el banner de turno (2026-11-18). Las etiquetas y los valores nunca se salen de su ficha: se ajustan al ancho de la ficha y, si hace falta, se parten en otra línea.

### US-34 Cambiar pregunta
- **AC-121** En la tarjeta de la ronda hay un botón **"↻ Cambiar pregunta"**. Solo está habilitado en `FACE_OFF` mientras no se haya revelado ninguna respuesta. Si no se puede usar, una nota explica por qué.
- **AC-122** `POST /api/rooms/{code}/rounds/current/question` (`X-Presenter-Token`, sin cuerpo) reemplaza la pregunta de la ronda por otra activa elegida al azar. La nueva pregunta no puede ser ninguna de esta partida ni ninguna que ya se haya descartado en la sala.
  - La ronda vuelve a empezar su cara a cara con los mismos jugadores designados: sin buzzer, sin equipo en turno y con el temporizador detenido y completo. Los puntos de la ronda, el multiplicador y el número de ronda se conservan.
  - Publica `QUESTION_CHANGED` y el snapshot nuevo, así que la TV y los celulares muestran la nueva pregunta.
  - Errores:
    - 401 sin token.
    - 403 con token inválido.
    - 404 si la sala no existe.
    - `409 INVALID_STATE` fuera de `FACE_OFF` o si ya se reveló una respuesta.
    - `409 NO_MORE_QUESTIONS` si no quedan preguntas disponibles.
