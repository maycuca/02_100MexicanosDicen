# Spec 001 — Juego base

| Campo | Valor |
|---|---|
| Estado | Borrador aprobado para planear |
| Fecha | 2026-11-17 |
| Alcance | Partida local con un presentador y N jugadores conectados por QR en la misma red |

## 1. Resumen
"Cien Mexicanos" es un juego de encuestas por equipos. Se plantea una pregunta que "se les hizo a cien mexicanos" y los equipos deben adivinar las respuestas más populares. Cada respuesta vale tantos puntos como personas la dieron.

Hay dos clientes web:
- **Presentador**: corre en una laptop conectada a una TV o proyector. Crea la sala, muestra el QR y el tablero, y controla la partida.
- **Jugador**: corre en el celular de cada participante. Sirve para unirse, elegir equipo y presionar el buzzer.

Las respuestas se dicen en voz alta; el presentador decide si coinciden con alguna del tablero.

## 2. Actores
| Actor | Dispositivo | Credencial |
|---|---|---|
| Pantalla (TV) | Laptop conectada a TV/proyector | `presenterToken` (lo emite el servidor al crear la sala) |
| Presentador | Celular (o la misma laptop) | `presenterToken` obtenido al canjear el QR de presentador (AC-30) |
| Jugador | Celular | `playerToken` (lo emite el servidor al unirse) |

## 3. Historias de usuario y criterios de aceptación

### US-1 Crear sala
Como presentador quiero crear una sala para que los jugadores se unan.
- **AC-01** Al crear una sala, el servidor devuelve un `roomCode` de 4 letras mayúsculas (sin I ni O), único entre las salas activas, y un `presenterToken`.
- **AC-02** El presentador puede configurar el nombre de los equipos (por defecto "Equipo Rojo" y "Equipo Verde"), el número de rondas (3–8, por defecto 5) y la meta de puntos (por defecto 300).
- **AC-03** La pantalla de inicio (US-10) muestra el QR de jugadores, que apunta a `http://<IP-LAN>:<puerto-frontend>/join/<roomCode>`, y el código en texto grande.
- **AC-04** La IP de la LAN la detecta el servidor (`GET /api/network-info`); el presentador puede corregirla a mano si se detecta mal.

### US-2 Unirse por QR
Como jugador quiero escanear el QR y unirme con mi nombre y equipo.
- **AC-05** Al abrir `/join/<roomCode>` el jugador escribe un nombre (2–20 caracteres) y elige uno de los dos equipos.
- **AC-06** No puede haber dos jugadores con el mismo nombre (sin distinguir mayúsculas) en la misma sala.
- **AC-07** Solo se puede unir mientras la sala está en `LOBBY`; después recibe un error claro.
- **AC-08** El lobby del presentador muestra en tiempo real (≤ 1 s) a cada jugador que se une, agrupado por equipo.
- **AC-09** Si el jugador recarga la página o pierde la conexión, recupera su sesión con el `playerToken` guardado en el navegador.

### US-3 Iniciar partida
- **AC-10** El presentador solo puede iniciar si cada equipo tiene al menos un jugador.
- **AC-11** Al iniciar, el servidor elige al azar `rounds` preguntas distintas y la sala pasa a `FACE_OFF` de la ronda 1.

### US-4 Cara a cara (buzzer)
- **AC-12** En cada ronda hay un jugador designado por equipo, rotando en orden de llegada al lobby.
- **AC-13** En `FACE_OFF` el celular del jugador designado muestra un botón de buzzer grande; los demás ven "Espera tu turno".
- **AC-14** El servidor registra solo el primer buzz recibido; los posteriores se ignoran. Todos los clientes ven quién ganó en ≤ 500 ms en la LAN.
- **AC-15** El presentador puede revelar una respuesta o marcar una "X" para el jugador que presionó primero. Si su respuesta es la de mayor puntaje, su equipo gana el control; si no, el rival tiene una oportunidad y gana el control quien tenga la respuesta más alta (el presentador lo confirma con "Asignar control").

### US-5 Jugar la ronda
- **AC-16** En `PLAYING` el equipo con control da respuestas; el presentador revela la respuesta acertada o marca un strike.
- **AC-17** Cada respuesta revelada suma sus puntos al **bote de la ronda** (no al marcador aún).
- **AC-18** Con 3 strikes la sala pasa a `STEAL`. Si se revelan todas las respuestas antes, la ronda termina y el bote es del equipo con control.

### US-6 Robo
- **AC-19** En `STEAL` el equipo rival da una sola respuesta. Si el presentador la revela (acierto), el bote es del rival; si marca X, el bote es del equipo con control.
- **AC-20** Al terminar la ronda (`ROUND_END`) se muestran todas las respuestas restantes al tablero y el bote, multiplicado según la ronda, se suma al marcador del equipo ganador.

### US-7 Multiplicadores y fin de partida
- **AC-21** Multiplicador: ×1 en las rondas normales, ×2 en la penúltima y ×3 en la última.
- **AC-22** La partida termina (`GAME_OVER`) cuando un equipo alcanza la meta al cierre de una ronda o al terminar todas las rondas. Se muestra al ganador; un empate se muestra como empate.
- **AC-23** Desde `GAME_OVER` el presentador puede iniciar una revancha con los mismos jugadores (vuelve a `LOBBY`).

### US-8 Vista de jugadores
- **AC-24** El celular de cada jugador muestra su equipo, el marcador, la pregunta actual y el estado (buzzer, "tu equipo responde", robo, fin de ronda).
- **AC-25** Los jugadores **nunca** reciben el texto ni los puntos de una respuesta no revelada.

### US-9 Seguridad mínima
- **AC-26** Toda acción de presentador exige `X-Presenter-Token` válido para esa sala; si falta o es inválido → `401`/`403`.
- **AC-27** Un buzz solo se acepta del jugador designado de esa ronda y en estado `FACE_OFF`.
- **AC-28** Las salas sin actividad durante 2 horas se cierran.

### US-10 Pantalla de inicio con QR de presentador y jugadores
Como anfitrión quiero que la TV muestre una pantalla de inicio con un QR para el presentador y otro para los jugadores, para que cada quien entre desde su celular sin escribir direcciones.
- **AC-29** Al crear la sala, el navegador de la laptop abre la **pantalla de inicio** `/screen/<roomCode>`: nombre del juego, código de sala y dos QR lado a lado con las etiquetas "Presentador" y "Jugadores".
- **AC-30** El QR de presentador apunta a `http://<IP-LAN>:<puerto-frontend>/host/<roomCode>?claim=<presenterClaimToken>`. El `presenterClaimToken` es de **un solo uso**: al canjearlo, el servidor entrega un `presenterToken` y la pantalla reemplaza ese QR por "Presentador conectado ✓". Un segundo intento de canje → `409 CLAIM_USED`.
- **AC-31** El QR de jugadores (AC-03) permanece visible durante todo el `LOBBY`.
- **AC-32** La pantalla de inicio muestra en tiempo real los jugadores de cada equipo y si el presentador ya está conectado (`presenterConnected`).
- **AC-33** La laptop que creó la sala conserva su `presenterToken`; desde la pantalla de inicio puede generar un QR de presentador nuevo, lo que invalida el anterior y los `presenterToken` emitidos por canjes previos (los del celular anterior), y pone `presenterConnected` en `false` hasta el siguiente canje. El token de la laptop sigue siendo válido.
- **AC-34** Al iniciar la partida, la pantalla de inicio pasa a ser el tablero de juego (misma ruta `/screen/<roomCode>`).

## 4. Máquina de estados de la sala
```
LOBBY ──start──▶ FACE_OFF ──assign-control──▶ PLAYING ──3 strikes──▶ STEAL
  ▲                 ▲                            │                       │
  │                 │                   all revealed                reveal / strike
  │                 │                            ▼                       ▼
  │                 └──────next-round──────── ROUND_END ◀────────────────┘
  │                                              │
  └───────────rematch────────── GAME_OVER ◀──goal reached / last round
```

## 5. Requisitos no funcionales
- **RNF-1** Funciona en una LAN sin internet.
- **RNF-2** Hasta 20 jugadores conectados por sala sin degradación visible.
- **RNF-3** El cliente jugador funciona en Safari iOS y Chrome Android recientes, en pantallas desde 360 px.
- **RNF-4** El cliente presentador está diseñado para 1920×1080, legible a 3 m.
- **RNF-5** Contenido inicial: al menos 10 preguntas originales con 3–8 respuestas cada una, cuyos puntos suman ≤ 100.

## 5b. Clarificaciones (2026-11-17, a partir de la revisión de QA)
| # | Tema | Decisión |
|---|---|---|
| C-1 | Puntos en el robo | Revelar en `STEAL` **no** suma esa respuesta al bote; el rival se lleva el bote acumulado (AC-19). |
| C-2 | Buzz previo | El buzz es opcional: en `FACE_OFF` el presentador puede revelar o asignar control aunque nadie haya presionado (respaldo si falla un celular). **Desde la spec 002 (AC-40)** marcar X sin buzz devuelve `409 NO_ACTIVE_TEAM`, porque no hay equipo en turno. |
| C-3 | X en el cara a cara | Una X en `FACE_OFF` **no** cuenta en `round.strikes`; los strikes empiezan en `PLAYING`. |
| C-4 | Claim anterior tras regenerar | `403 CLAIM_INVALID`. `409 CLAIM_USED` es solo para un claim vigente que ya se canjeó. |
| C-5 | Tokens de presentador | Ausente → `401 UNAUTHORIZED`; presente pero inválido o de otra sala → `403 FORBIDDEN` (en `getMe` y `listQuestions`, que solo declaran 401, el token inválido da `401 UNAUTHORIZED`). Sala inexistente → `404 ROOM_NOT_FOUND`. |
| C-6 | Revelar sin ronda / respuesta ajena | `reveal`/`strike`/`control` en `LOBBY`, `ROUND_END` o `GAME_OVER` → `409 INVALID_STATE`; `answerId` que no pertenece a la ronda actual → `404 ANSWER_NOT_FOUND`. |
| C-7 | AC-28 | Se verifica con prueba unitaria usando un `Clock` inyectable (no es probable como caja negra). |
| C-8 | CONNECT STOMP | Sin token (`UNAUTHORIZED`) o con token inválido (`FORBIDDEN`) se rechaza en `CONNECT` (frame `ERROR`); la TV sin token usa sondeo REST. Suscripciones con comodines o fuera de `/topic/rooms/**` y `/user/queue/**` se rechazan. |
| C-9 | Revelar dos veces | Revelar una respuesta ya revelada → `409 INVALID_STATE`. |
| C-10 | Código de sala | Se acepta sin distinguir mayúsculas y se normaliza. |

## 6. Fuera de alcance (v1)
- Ronda final de "dinero rápido".
- Partidas por internet / salas públicas.
- Editor de preguntas en la UI (se cargan por migración).
- Reconocimiento automático de respuestas por texto o voz.
- Cuentas de usuario.

## 7. Preguntas abiertas
- ¿Se agrega sonido (buzzer, acierto, X)? Propuesta: sí, con sonidos propios, en la spec 002.
