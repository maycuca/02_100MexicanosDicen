# Spec 003 — Pantalla de título estilo videojuego y equipos nombrados por los jugadores

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-17 |
| Depende de | `specs/001-juego-base`, `specs/002-experiencia-de-juego` (siguen vigentes) |
| Contrato | `specs/001-juego-base/contracts/` **v0.5.0** (aditivo) |

## 1. Resumen
- El juego se llama **"Lo que 100 Mexicanos dicen"** en todas las pantallas.
- La página `/` deja de parecer un formulario web y se convierte en una **pantalla de título de videojuego**: logotipo grande animado, menú con **EMPEZAR** y **AJUSTES**, y los ajustes en un modal.
- La pantalla de título ya **no pide nombres de equipo**: los elige el **capitán** de cada equipo desde su celular.

## 2. Historias y criterios de aceptación

### US-15 Pantalla de título
- **AC-55** La ruta `/` muestra una pantalla de título a pantalla completa con el logotipo "Lo que 100 Mexicanos dicen" (tipografía grande con relieve/sombra, animación de entrada), fondo animado propio (p. ej. papel picado o destellos en movimiento) y un menú vertical con **EMPEZAR** y **AJUSTES**. No hay campos de formulario visibles.
- **AC-56** El menú se maneja como en un videojuego: flechas ↑/↓ (y W/S) mueven la selección, Enter/Espacio la activa, y también funciona con mouse y toque. La opción seleccionada se resalta y "late". Al cargar, la selección está en EMPEZAR.
- **AC-57** **EMPEZAR** crea la sala con los ajustes guardados (sin nombres de equipo; el servidor usa "Equipo Rojo" y "Equipo Verde") y abre la pantalla de inicio `/screen/<código>` (AC-29). Mientras se crea, el botón muestra un estado de carga; si falla, se muestra el error en un aviso estilo juego y se puede reintentar.
- **AC-58** **AJUSTES** abre un modal (diálogo accesible con foco atrapado; Esc o "Volver" lo cierra) con: rondas (3–8), meta de puntos (50–1000), temporizador (Sin temporizador / 10–60 s) y X automática al vencer. Los valores se guardan en el navegador (localStorage con try/catch) y se usan en el siguiente EMPEZAR. Los valores por defecto son los del contrato.
- **AC-59** Con sonido activado en ese navegador (preferencia de AC-52), mover la selección y activar una opción reproduce un sonido corto de menú (Web Audio, original). Sin interacción previa no suena nada.
- **AC-60** Respeta `prefers-reduced-motion` (sin animaciones de entrada ni fondo en movimiento) y funciona desde 360 px de ancho hasta 1920×1080.

### US-16 Nombre del juego
- **AC-61** El título "Lo que 100 Mexicanos dicen" aparece en la pantalla de título, en la cabecera de la pantalla de inicio `/screen/<código>` (con el mismo logotipo, en tamaño menor), en el `<title>` del documento y en las vistas de jugador y presentador donde hoy dice "Cien Mexicanos".

### US-17 Equipos nombrados por los jugadores
- **AC-62** El primer jugador que se une a cada equipo es su **capitán** (`Team.captainId`). Si aún no hay jugadores, `captainId` es null.
- **AC-63** Mientras la sala está en `LOBBY`, el capitán puede cambiar el nombre de su equipo con `PUT /api/rooms/{code}/teams/{team}/name` (`X-Player-Token`). Reglas: 2–20 caracteres tras recortar espacios; no puede ser igual (sin distinguir mayúsculas) al nombre del otro equipo.
- **AC-64** Errores: sin token → `401 UNAUTHORIZED`; token de otra sala o de un jugador que no es capitán de ese equipo → `403 NOT_CAPTAIN`; fuera de `LOBBY` → `409 ROOM_NOT_IN_LOBBY`; nombre repetido → `409 TEAM_NAME_TAKEN`; longitud inválida → `400 VALIDATION_ERROR`.
- **AC-65** El cambio publica `TEAM_RENAMED` (con `team`) y el snapshot nuevo; la pantalla de inicio de la TV y todos los celulares muestran el nombre nuevo en ≤ 1 s.
- **AC-66** En el celular, el capitán ve en la sala de espera "Eres el capitán de <equipo>" y un campo para nombrar al equipo con botón "Guardar nombre"; los demás jugadores ven el nombre actual. Al unirse (`/join`), los equipos se muestran con su nombre actual y su color.
- **AC-67** La revancha (AC-23) conserva los nombres y los capitanes.

### US-18 Pantalla de QR sin datos técnicos (2026-11-18)
La pantalla de la TV donde se escanean los QR (`/screen/<código>` en `LOBBY`) solo muestra el logotipo, los dos QR (con su URL debajo) y los jugadores por equipo.
- **AC-68** No muestra el código de sala en texto. Reemplaza la parte "y el código en texto grande" de AC-03 (spec 001). El código sigue dentro de la URL de cada QR.
- **AC-69** No muestra la IP de la red ni un campo para editarla. Reemplaza la corrección manual de AC-04 (spec 001); si la IP detectada es incorrecta, se arranca el backend con `--app.lan-address=<ip>`.
- **AC-70** No muestra el botón de sonido. El sonido se activa en AJUSTES → Sonido de la pantalla de título; el toque en EMPEZAR desbloquea el audio y la TV lo conserva al abrir la pantalla de QR. El botón pequeño de sonido sigue en el tablero (AC-52) y el presentador puede silenciar (AC-53).
- **AC-71** El logotipo de esta pantalla es más pequeño que en el tablero, de modo que logotipo, QR (con su URL) y equipos se ven completos en 1920×1080 sin desplazarse.
- **AC-72** Un botón circular con icono de flecha circular ("Regresar", arriba a la izquierda) vuelve a la pantalla de título. La sala creada sigue existiendo hasta que expire por inactividad (AC-28).

## 2b. Clarificaciones (2026-11-17, implementación de backend)
| # | Tema | Decisión |
|---|---|---|
| C-21 | Orden de errores en `renameTeam` | 401 `UNAUTHORIZED` → 404 `ROOM_NOT_FOUND` → 403 `NOT_CAPTAIN` (token inválido, de otra sala, de presentador, del otro equipo o no capitán) → 409 `ROOM_NOT_IN_LOBBY` → 400 `VALIDATION_ERROR` (longitud tras recortar; `name` ausente o null) → 409 `TEAM_NAME_TAKEN`. |
| C-22 | Mismo nombre | El mismo nombre exacto responde 200 sin evento ni nueva `version`; cambiar solo mayúsculas del propio nombre sí cuenta como cambio. |
| C-23 | Equipo inválido | `{team}` distinto de A/B → 400 `VALIDATION_ERROR`. |
| C-24 | Nombres al crear sala | `teamAName`/`teamBName` opcionales siguen aceptando 1–24; solo el renombre usa 2–20. |

## 3. Fuera de alcance
- Elegir colores de equipo.
- Transferir la capitanía.
- Música de fondo.
