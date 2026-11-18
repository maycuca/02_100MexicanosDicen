# Spec 007 — Estado a color, cierre con "¡Gracias por jugar!" y celebración de resultados

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-18 |
| Depende de | specs 001–006 (siguen vigentes salvo lo que se reemplaza aquí) |
| Contrato | sin cambios (v0.7.0) |

## 1. Resumen
- La línea de estado de la TV resalta a los equipos con su color y los puntos en dorado.
- El capitán puede guardar el nombre sugerido sin cambiarlo.
- En el presentador, "Bote" pasa a llamarse "Revelados".
- Al regresar al menú principal se cierran todas las sesiones y todos ven "¡Gracias por jugar!".
- La pantalla de resultados es una fiesta: confeti, trofeo, marcador que cuenta hacia arriba y música de dicha.

## 2. Historias y criterios de aceptación

### US-28 Estado a color en la TV
- **AC-110** En la línea de estado del tablero, cada nombre de equipo se muestra con su color y cada cantidad de puntos en dorado. Aplica a todos los mensajes de estado:
  - "Responde <equipo>".
  - "¡Robo! <equipo> tiene una oportunidad".
  - "Fin de la ronda: <equipo> se lleva <N> puntos".
  - "¡Pasa a <equipo>!".
  - "¡<equipo> debe superar la #N!".
  - "¡<equipo> gana el control con cualquier respuesta!".

  El texto completo se mantiene igual para lectores de pantalla.

- **AC-117** (2026-11-18) En el cara a cara, los nombres de los jugadores de la línea de estado se muestran con el color de su equipo. Aplica a "Cara a cara: <jugador> contra <jugador>" y a "¡<jugador> ganó el buzzer!".

### US-29 Nombre sugerido
- **AC-111** El botón **Guardar nombre** del capitán está habilitado desde el principio, aunque el campo tenga el nombre sugerido ("Equipo Rojo" o "Equipo Verde"). Guardar el mismo nombre confirma "¡Nombre guardado!"; el servidor no lo trata como cambio (C-22). Sigue deshabilitado si el nombre no mide 2–20 caracteres o coincide con el del otro equipo (AC-63).

### US-30 Presentador
- **AC-112** En la tarjeta de la ronda del presentador, "Bote" se llama **"Revelados"**: es la suma de los puntos de las respuestas reveladas.

### US-31 Cierre con agradecimiento
- **AC-113** Al confirmar **"Sí, regresar"** (AC-90) se cierra la sala (AC-91). Cada cliente borra **todas sus sesiones de esa sala** (token de presentador, QR de presentador y token de jugador) y muestra la pantalla **"¡Gracias por jugar!"** (`/gracias`), con el logotipo y el botón **Menú principal**, que lleva a `/`. Esto aplica al presentador, a la TV y a cada jugador, y reemplaza los destinos de AC-91.
- **AC-118** (2026-11-18) La pantalla "¡Gracias por jugar!" ya no tiene "Menú principal". En su lugar tiene el botón **Cerrar ventana**, que intenta cerrar la pestaña (`window.close()`). El navegador solo permite cerrar pestañas abiertas por la propia página; si no se cierra, aparece el aviso "Tu navegador no permite cerrarla desde aquí: cierra esta pestaña o ventana." Reemplaza el botón de AC-113.
- **AC-123** (2026-11-18) Al cerrarse la sala, **la TV no muestra "¡Gracias por jugar!"**: borra sus sesiones de esa sala y vuelve al **menú principal** (pantalla de título `/`), lista para EMPEZAR otra partida. "¡Gracias por jugar!" con "Cerrar ventana" queda solo para el presentador y los jugadores. Ajusta AC-113 y AC-118.

### US-32 Resultados de fiesta
- **AC-114** La pantalla de resultados de la TV (`GAME_OVER`) celebra:
  - Confeti que cae (CSS, original).
  - Un trofeo sobre el título del ganador, con el nombre del equipo en su color.
  - El marcador cuenta de 0 al puntaje final.
  - La tarjeta del ganador brilla.

  Con `prefers-reduced-motion` no hay confeti ni animaciones y el marcador aparece directo.
- **AC-115** Al llegar a `GAME_OVER`, la TV toca una **música de dicha**: una melodía original de unos 6 segundos, alegre y en tono mayor, con bajo, aplausos y silbidos (Web Audio). Reemplaza el sonido corto de ganador y respeta la preferencia de sonido y el silencio del presentador (AC-52, AC-53).
- **AC-116** En el celular de cada jugador, al terminar la partida:
  - Los ganadores ven **"¡Ganaron!"** con confeti y su celular vibra en festejo.
  - Los demás ven **"¡Buen juego!"**.
  - Si hay empate, todos ven **"¡Empate!"**.
  - En los tres casos se muestran los puntos finales de los dos equipos.
