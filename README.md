# Lo que 100 Mexicanos dicen

Juego de encuestas por equipos para jugar en la sala de tu casa. Una laptop conectada a la **TV** muestra el tablero; el **presentador** controla la partida desde su celular y cada **jugador** se une desde el suyo escaneando un código QR. Todo corre en tu red local: no hace falta internet ni crear cuentas.

> **Versión 1.0** — 100 preguntas originales de cultura popular mexicana, cine, música, TV, fiestas, comida y deportes.

---

## Cómo se juega

1. **Empieza.** En la laptop abre el juego y pulsa **EMPEZAR**. En **AJUSTES** eliges las rondas (3–8), la meta de puntos, el temporizador por turno y si al acabarse el tiempo se marca una X automática.
2. **Escanea.** La TV muestra dos QR:
   - **Presentador**: lo escanea una sola persona; desde su celular controla la partida.
   - **Jugadores**: cada participante lo escanea, escribe su nombre y elige equipo (**Rojo** o **Verde**). El primero de cada equipo es el **capitán** y puede ponerle nombre.
3. **Cara a cara.** En cada ronda, un jugador de cada equipo compite por el **buzzer** en su celular.
   - Si el equipo que ganó el buzzer abre la **respuesta #1**, se queda con el turno.
   - Si abre otra o se le acaba el tiempo, el turno pasa al otro equipo. Ese equipo gana el turno con una respuesta **más alta**; si falla, el turno regresa al primero.
4. **Tres X.** El equipo en turno intenta abrir todas las respuestas. Si lo logra antes de su tercera X, gana los puntos de la ronda.
5. **Robo.** Con tres X, el otro equipo tiene **una** oportunidad: si abre una respuesta correcta, se roba los puntos; si falla, se los queda el equipo de las tres X.
6. **Final.** Las dos últimas rondas valen doble y triple. Gana quien llegue a la meta o tenga más puntos al terminar. La TV festeja con confeti y música, y cada celular muestra si su equipo ganó.

### Lo que hace cada pantalla

| Pantalla | Dónde | Qué hace |
|---|---|---|
| **TV** (`/screen/<código>`) | Laptop conectada a la TV | QR de entrada, jugadores por equipo, tablero con respuestas ocultas, X de cada equipo, cuenta regresiva, sonidos y pantalla de resultados. Sin controles. |
| **Presentador** (`/host/<código>`) | Celular | Ve todas las respuestas. Revela respuestas, marca fallos y X, controla el temporizador (▶ ❚❚ ↺), cambia la pregunta, silencia la TV, pasa de ronda, inicia la revancha y regresa al menú principal. |
| **Jugador** (`/play/<código>`) | Celular de cada participante | Buzzer, equipo en turno, marcador, cuenta regresiva, vibración en los momentos clave y resultado final. Nunca ve respuestas ocultas. |

---

## Requisitos

- **JDK 25** (el backend usa Java 25; si tienes otra versión por defecto, ver más abajo).
- **Node.js 22 o superior** (probado con Node 24).
- La laptop y los celulares en la **misma red Wi-Fi**.

## Inicio rápido

```bash
# Terminal 1 — backend (puerto 8080)
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS: selecciona el JDK 25
./mvnw spring-boot:run

# Terminal 2 — frontend (puerto 5173, visible en la red local)
cd frontend
npm install
npm run dev
```

Abre **http://localhost:5173** en la laptop, pulsa **EMPEZAR** y escanea los QR con los celulares.

- **Si la IP de los QR no es la correcta** (por ejemplo, la laptop tiene varias redes), arranca el backend indicándola:
  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.lan-address=192.168.x.y
  ```
- **Sonido:** se activa en **AJUSTES → Sonido** de la pantalla de título. El presentador puede silenciar la TV con el switch **Sonido de la TV**.

### Probar sin celulares

En **AJUSTES** pon **Prueba local** en **Sí** y pulsa EMPEZAR. En lugar de los QR verás las URL con los botones **Copiar** y **Abrir en pestaña nueva**. Cada pestaña del navegador es una sesión distinta, así que puedes tener la TV, el presentador y varios jugadores en una sola computadora.

---

## Tecnología

| Capa | Tecnología |
|---|---|
| Backend | Java 25 · Spring Boot 4.1 (Web MVC, WebSocket/STOMP, Data JPA, Validation, Actuator, Flyway) |
| Base de datos | SQLite (`backend/data/cien-mexicanos.db`, se crea sola) con migraciones Flyway |
| Frontend | React 19 · TypeScript · Vite 8 · React Router · `@stomp/stompjs` · `qrcode.react` |
| Tiempo real | STOMP sobre WebSocket nativo (`/ws`) |
| Sonido | Web Audio API: todos los sonidos y la música se sintetizan en el navegador, sin archivos de audio |
| Pruebas | JUnit 5 + MockMvc · Vitest + Testing Library · Playwright · colección Postman |

### Cómo está hecho

- **El servidor es la autoridad.** Las reglas del juego viven en el backend (`game/domain`, código puro sin Spring). Los clientes solo muestran el estado que reciben.
- **Tiempo real.** Cada cambio publica por STOMP un snapshot del estado y los eventos (buzzer, respuesta revelada, X, fin de ronda…), con un número de versión para descartar mensajes viejos.
- **Las salas viven en memoria.** Se cierran tras 2 horas sin actividad o cuando el presentador regresa al menú principal. En SQLite se guardan el banco de preguntas y el historial de partidas.
- **Contrato primero.** La API REST está en OpenAPI 3.1 y los mensajes en AsyncAPI 3.0. Los tipos del frontend se generan del contrato.
- **Privacidad de las respuestas.** La TV y los jugadores reciben la vista pública, sin el texto de las respuestas ocultas; solo el presentador ve el tablero completo.

---

## Estructura

```
specs/                       Spec-Driven Development: constitución y una carpeta por feature
  constitution.md            principios del proyecto
  001-juego-base/            spec, plan, modelo de datos, tareas, trazabilidad
    contracts/               openapi.yaml y asyncapi.yaml (contrato único del sistema)
  002 … 008/                 experiencia de juego, título, prueba local, control del presentador,
                             reglas del cara a cara, celebración y cambio de pregunta
backend/                     Spring Boot (paquete mx.cienmexicanos)
  src/main/java/…/game/domain   reglas del juego (sin dependencias de framework)
  src/main/resources/db/migration   esquema y banco de preguntas (V1–V4)
frontend/                    React + Vite
  src/features/              title · screen (TV) · presenter · player · localtest · thanks
  src/shared/                api (tipos generados) · realtime · audio · session · ui
  e2e/                       pruebas Playwright (TV + presentador + jugadores)
postman/                     colección derivada del contrato y su generador
```

---

## Pruebas

```bash
# Backend (usa siempre el JDK 25)
cd backend
./mvnw verify                  # unitarias, integración y API
./mvnw verify -Pacceptance     # aceptación de caja negra (HTTP + STOMP)

# Frontend
cd frontend
npm run lint                   # oxlint
npm run typecheck
npm test                       # Vitest
npm run e2e                    # Playwright (necesita backend y frontend arriba)

# Contrato (con el backend arriba)
python3 postman/scripts/generate_collection.py
postman collection run postman/collections/cien-mexicanos.postman_collection.json \
  -e postman/environments/local.postman_environment.json
```

Cada criterio de aceptación (`AC-xx`) está ligado a sus pruebas en el `traceability.md` de su spec.

---

## Banco de preguntas

Hay **100 preguntas** con 5 o 6 respuestas cada una. Los puntos de cada pregunta suman 100 o menos.

- **Puntajes estimados:** las preguntas y los puntajes son originales del proyecto. Los puntajes son una estimación de las respuestas más comunes, no salen de una encuesta real.
- **Preguntas por partida:** cada partida elige al azar una pregunta distinta por ronda. El presentador puede **cambiar la pregunta** durante el cara a cara si ya salió antes.
- **Agregar o corregir preguntas:** crea una nueva migración en `backend/src/main/resources/db/migration/` (por ejemplo `V5__…sql`). No edites una migración que ya se aplicó, porque Flyway la valida.

---

## Desarrollo

El proyecto sigue **Spec-Driven Development**:

`constitución → spec → plan → contrato → tareas → código → verificación`

Antes de cambiar algo, lee la spec correspondiente en `specs/`. Si el cambio toca la API, actualiza primero el contrato, regenera los tipos (`npm run gen:api`) y la colección Postman. La guía completa para colaborar, incluidos los agentes de IA, está en [CLAUDE.md](CLAUDE.md).

- **Identificadores:** el código, los identificadores y los commits van en inglés (Conventional Commits).
- **Textos:** la interfaz y la documentación van en español.
- **Identidad propia:** nombre, colores, sonidos y preguntas son originales; no se copian de ningún programa de televisión.

### Pendiente

- Prueba manual en una red local real con celulares (sonido en Safari de iOS y vibración en Android).
