# Plan técnico — 001 Juego base

Cómo se implementa `spec.md`. Respeta `../constitution.md`.

## 1. Arquitectura
```
 ┌──────────────── LAN ─────────────────────────────────────────────┐
 │                                                                  │
 │  Laptop/TV: Presentador (React)        Celulares: Jugador (React)│
 │        │  REST + STOMP                        │ REST + STOMP     │
 │        ▼                                      ▼                  │
 │   Vite dev server :5173  ── proxy /api, /ws ──▶ Spring Boot :8080│
 │   (prod: build estático servido por Spring)        │             │
 │                                                    ▼             │
 │                                             SQLite ./data/*.db   │
 └──────────────────────────────────────────────────────────────────┘
```
- Los celulares solo hablan con el origen del frontend (`:5173` en desarrollo); Vite hace proxy a `:8080`, así que no hay CORS en desarrollo.
- En producción (spec futura) `npm run build` se copia a `backend/src/main/resources/static` y todo sale por `:8080`.

## 2. Stack y versiones
| Capa | Tecnología |
|---|---|
| Backend | Java 25, Spring Boot 4.1.2 (Spring Framework 7, Hibernate 7, Jackson 3), Maven Wrapper |
| Tiempo real | `spring-boot-starter-websocket`, STOMP con broker simple en memoria |
| Persistencia | SQLite (`org.xerial:sqlite-jdbc`), `hibernate-community-dialects` (`SQLiteDialect`), Flyway |
| Frontend | React 19 + TypeScript + Vite, React Router, `@stomp/stompjs`, `qrcode.react` |
| Tipos de API | `openapi-typescript` generado desde `contracts/openapi.yaml` |
| Pruebas | JUnit 5, AssertJ, MockMvc, Spring STOMP client · Vitest + Testing Library · Playwright · Postman CLI |

## 3. Decisiones
| # | Decisión | Motivo |
|---|---|---|
| D1 | Estado de salas activas **en memoria** (`ConcurrentHashMap<RoomCode, Room>`); SQLite solo para banco de preguntas e historial de partidas | Salas efímeras; menos I/O y sin problemas de escritura concurrente en SQLite |
| D2 | Cada `Room` se modifica bajo su propio lock (`ReentrantLock`); comandos serializados por sala | Garantiza "primer buzz gana" (AC-14) sin condiciones de carrera |
| D3 | Broadcast de snapshot completo + `version` en vez de diffs | Simplicidad y reconexión trivial (AC-09) |
| D4 | Dos proyecciones del estado: `RoomState` (pública) y `PresenterRoomState` | Imposibilita filtrar respuestas ocultas a jugadores (AC-25) |
| D5 | Dominio puro en `mx.cienmexicanos.game.domain` (sin Spring, sin JPA) | Pruebas unitarias rápidas y cobertura ≥ 90 % |
| D6 | Tokens = UUID aleatorios opacos; se validan en un `HandlerInterceptor` (REST) y en un `ChannelInterceptor` (STOMP CONNECT/SUBSCRIBE) | Seguridad mínima suficiente en LAN (AC-26, AC-27) |
| D7 | Errores REST con `ProblemDetail` (RFC 9457) y campo `code` | Contrato estable para el frontend |
| D8 | IP LAN detectada recorriendo `NetworkInterface` (IPv4 site-local, interfaz activa, no loopback); sobreescribible con `app.lan-address` | AC-04 |
| D9 | WebSocket nativo sin SockJS | Navegadores móviles actuales lo soportan |
| D10 | El QR de presentador lleva un `presenterClaimToken` de un solo uso, distinto del `presenterToken` | El QR queda a la vista de todos en la TV; tras el canje ya no sirve (AC-30) |

## 4. Estructura del backend
```
backend/src/main/java/mx/cienmexicanos/
├── CienMexicanosApplication.java
├── config/          WebConfig (interceptores), WebSocketConfig, AppProperties
├── network/         NetworkInfoController, LanAddressResolver
├── room/            RoomController, RoomService, RoomRegistry, RoomCodeGenerator
├── player/          PlayerController
├── game/
│   ├── domain/      Room, Team, Player, Round, RoomStatus, GameRules, GameException (puro)
│   ├── GameController (acciones del presentador)
│   └── GameService
├── question/        Question, Answer (JPA), QuestionRepository, QuestionController
├── realtime/        BuzzController (@MessageMapping), RoomBroadcaster, StompAuthInterceptor
└── common/          ApiExceptionHandler, mappers a DTOs (records)
```

## 5. Estructura del frontend
```
frontend/src/
├── app/             router.tsx, App.tsx
├── features/
│   ├── screen/      StartScreenPage (TV: QR presentador + QR jugadores → tablero)
│   ├── presenter/   CreateRoomPage, HostPage (control desde el celular)
│   └── player/      JoinPage, PlayPage (buzzer / espera / marcador)
├── shared/
│   ├── api/         schema.d.ts (generado), client.ts
│   ├── realtime/    stompClient.ts, useRoomState.ts
│   ├── session/     tokens en localStorage
│   └── ui/          componentes y tema (paleta propia)
└── main.tsx
```
Rutas: `/` (crear sala) · `/screen/:code` (TV) · `/host/:code?claim=…` (presentador) · `/join/:code` · `/play/:code`.

## 6. Flujo de una acción (ej. buzz)
1. Celular envía `SEND /app/rooms/KDMZ/buzz`.
2. `StompAuthInterceptor` ya asoció el `playerToken` a la sesión en CONNECT.
3. `BuzzController` → `GameService.buzz(code, playerId)` → toma el lock de la sala → `Round.registerBuzz` (dominio).
4. `RoomBroadcaster` publica `/topic/rooms/KDMZ/state`, `/topic/rooms/KDMZ/presenter` y el evento `BUZZ_WON`.
5. Si el buzz es inválido → mensaje a `/user/queue/errors`.

## 7. Riesgos
| Riesgo | Mitigación |
|---|---|
| Flyway con SQLite | Verificado en T004; si falla, `spring.sql.init` con `schema.sql` |
| La red bloquea tráfico entre dispositivos (aislamiento de clientes en Wi-Fi) | Documentado en `quickstart.md`; usar hotspot propio |
| Firewall de macOS bloquea :5173/:8080 | Documentado en `quickstart.md` |
| La IP detectada es la de una VPN/Docker | Selección de IP editable en el lobby (AC-04) |
