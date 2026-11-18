# Spec 004 — Modo "Prueba local" (sin celulares, con pestañas del navegador)

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-18 |
| Depende de | `specs/001-juego-base`, `specs/002-experiencia-de-juego`, `specs/003-pantalla-de-titulo` (siguen vigentes) |
| Contrato | `specs/001-juego-base/contracts/` **v0.5.0** (sin cambios) |

## 1. Resumen
Una herramienta para probar la partida completa en una sola computadora: el presentador y los jugadores se abren en **pestañas o ventanas del mismo navegador**, sin escanear QR con celulares. Se enciende y apaga desde **AJUSTES**.

Es una función **desacoplada**:
- Vive en `frontend/src/features/localtest/`.
- Solo toca el resto de la app en tres puntos: el arranque, el modal de AJUSTES y la pantalla de QR.
- Con el modo apagado, la app se comporta exactamente igual que antes.
- No cambia el backend ni el contrato.
- No modifica la constitución §IV: sigue siendo una red local y los celulares siguen siendo el modo normal de juego.

## 2. Problema que resuelve
- Los tokens de sesión se guardan en `localStorage`, que comparten todas las pestañas del mismo navegador (AC-09). Por eso, la segunda pestaña de jugador "es" el mismo jugador que la primera.
- Los QR apuntan a la IP de la LAN, que es otro origen distinto de `localhost`.
- Hoy, para probar con varios jugadores se necesitan varios celulares o navegadores distintos.

## 3. Historias y criterios de aceptación

### US-19 Prueba local desde AJUSTES
- **AC-73** El modal de AJUSTES tiene la fila **"Prueba local"** (Sí/No). Su valor por defecto es No.
  - Se guarda en ese navegador (localStorage con try/catch) y no se envía al servidor.
  - "Restablecer" no la cambia: solo restablece los ajustes de partida.
- **AC-74** Con el modo encendido, la pantalla de título muestra el distintivo **"PRUEBA LOCAL"**.
- **AC-75** En la pantalla de QR (`/screen/<código>`), con el modo encendido, cada tarjeta (Presentador y Jugadores) muestra en lugar del QR:
  - La URL completa con el origen del navegador (p. ej. `http://localhost:5173/join/<código>`), sin depender de la detección de IP.
  - El botón **"Copiar"**, que copia la URL al portapapeles y confirma "¡Copiada!".
  - El botón **"Abrir en pestaña nueva"**.
  - Arriba, el aviso "Prueba local: pega cada URL en una pestaña distinta de este navegador".
  - "Presentador conectado ✓" sigue reemplazando la tarjeta del presentador (AC-32), y el botón "Nuevo QR de presentador" sigue funcionando (AC-33).
- **AC-76** Mientras el modo está encendido, **cada pestaña tiene su propia sesión** (`sessionStorage`). Así, varias pestañas del mismo navegador pueden ser el presentador y jugadores distintos de la misma sala, y recargar una pestaña conserva su sesión (AC-09). Cerrar la pestaña pierde esa sesión.
- **AC-77** El modo se fija al cargar cada pestaña y al cambiarlo en AJUSTES; no se cambia en las pestañas que ya están abiertas, para no perder sus sesiones a media partida. Las pestañas que se abren con las URL copiadas lo heredan porque comparten origen.
- **AC-78** Con el modo apagado, todo se comporta como antes:
  - La pantalla de QR muestra los QR con la IP de la LAN y su URL (AC-29, AC-68…AC-72).
  - Las sesiones se guardan en `localStorage`.
  - No aparece ningún botón ni aviso de prueba local.

## 4. Diseño
- `features/localtest/mode.ts`:
  - Guarda la bandera `cm:local-test` (`on` cuando está encendido).
  - `installLocalTest()` se llama en `main.tsx`.
  - `setLocalTestMode(on)` cambia la bandera.
  - `useLocalTestMode()` usa `useSyncExternalStore`.
- `shared/session/session.ts` permite elegir dónde se guardan los tokens con `setSessionScope('local' | 'tab')`. No importa nada de `features/`; es la feature la que lo configura.
- `features/localtest/LocalTestLink.tsx` muestra la URL con "Copiar" y "Abrir en pestaña nueva" (`noopener`, para que la pestaña nueva no herede la sesión). `LocalTestBadge.tsx` muestra el distintivo y `LocalTestBanner.tsx` el aviso.
- Copiar usa `navigator.clipboard.writeText`. Si no está disponible, recurre a `document.execCommand('copy')`, y si eso también falla, muestra "Selecciona la URL y cópiala".

## 5. Fuera de alcance
- Bots o jugadores simulados.
- Abrir automáticamente N pestañas de jugadores.
- Migrar sesiones existentes al cambiar el modo.
