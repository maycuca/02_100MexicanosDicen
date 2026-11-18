# Tareas — 001 Juego base

Leyenda: `[BE]` backend-dev · `[FE]` frontend-dev · `[QA]` qa-tester · `[P]` puede ir en paralelo · `→` depende de.
Estado: `[x]` hecho · `[ ]` pendiente.

## Fase 0 — Esqueleto (esta entrega)
- [x] T001 Constitución, spec, plan, modelo de datos y contratos
- [x] T002 CLAUDE.md y subagentes `frontend-dev`, `backend-dev`, `qa-tester`
- [x] T003 [BE] Proyecto Spring Boot 4.1.2 / Java 25 con webmvc, websocket, data-jpa, validation, actuator, flyway, sqlite
- [x] T004 [BE] `application.yml` con SQLite + migraciones `V1__init.sql` y `V2__seed_questions.sql`; verificar Flyway sobre SQLite
- [x] T005 [BE] `WebSocketConfig` (endpoint `/ws`, broker `/topic`, `/queue`, prefijo `/app`)
- [x] T006 [BE] `GET /api/network-info` funcional (AC-04)
- [x] T007 [BE] Controladores stub para el resto del contrato → `501 NOT_IMPLEMENTED`
- [x] T008 [FE] Proyecto Vite + React + TS con router, proxy `/api` y `/ws`, `server.host: true`
- [x] T009 [FE] Páginas placeholder para las 4 rutas; generación de tipos desde OpenAPI
- [x] T010 [QA] Vitest + Testing Library + Playwright configurados; prueba de humo de cada lado
- [x] T012 [FE] Pantalla de inicio `/screen/:code` con QR de presentador y de jugadores (AC-29, AC-31; maqueta sin datos en vivo) y ruta `/host/:code`
- [x] T013 [BE] Stubs `claimPresenter` y `regeneratePresenterClaim` del contrato v0.2.0
- [x] T011 [QA] Colección Postman en `postman/` derivada de `openapi.yaml` (ejecutada con Postman CLI)

## Fase 1 — Dominio (backend)
- [x] T101 [BE] Modelo puro `Room/Team/Player/Round/RoomStatus` + `GameException`
- [x] T102 [BE] `GameRules`: multiplicador, bote, strikes, robo, cierre de ronda, fin de partida (AC-17…AC-22)
- [x] T103 [QA][P] Pruebas unitarias de la máquina de estados (tabla de transiciones de `data-model.md`) → T101
- [x] T104 [BE] `RoomCodeGenerator` (sin I/O, únicos) y `RoomRegistry` con expiración (AC-01, AC-28)

## Fase 2 — API REST
- [x] T201 [BE] `POST /api/rooms`, `GET /api/rooms/{code}`, `GET /presenter-view` → T101, T104
- [x] T202 [BE] `POST /players`, `GET /players/me` (AC-05…AC-09)
- [x] T203 [BE] Interceptor `X-Presenter-Token` + `ApiExceptionHandler` con ProblemDetail (AC-26)
- [x] T204 [BE] `start`, `reveal`, `strike`, `control`, `next-round`, `rematch` → T102
- [x] T205b [BE] `presenterClaimToken` de un solo uso: emitir en createRoom, canjear y regenerar; `presenterConnected` en el snapshot (AC-30, AC-32, AC-33)
- [x] T205 [BE] Carga de preguntas desde SQLite y selección aleatoria (AC-11)
- [x] T206 [QA][P] Pruebas MockMvc por endpoint + colección Postman contra el backend real (ejecutada con Postman CLI)

## Fase 3 — Tiempo real
- [x] T301 [BE] `StompAuthInterceptor` (CONNECT/SUBSCRIBE) y `RoomBroadcaster` → T201
- [x] T302 [BE] `@MessageMapping /rooms/{code}/buzz` con "primer buzz gana" (AC-14, AC-27)
- [x] T303 [QA] Prueba de concurrencia: 20 buzz simultáneos → un único ganador
- [x] T304 [FE][P] `stompClient` + hook `useRoomState` con reconexión y descarte por `version`

## Fase 4 — Cliente presentador
- [x] T401 [FE] `CreateRoomPage` con configuración (AC-02)
- [x] T402 [FE] Pantalla de inicio: jugadores en vivo y estado del presentador vía `useRoomState` (AC-08, AC-32)
- [x] T405 [FE] Pantalla de inicio: botón "Nuevo QR de presentador" (AC-33) y transición al tablero al iniciar (AC-34)
- [x] T403 [FE] `BoardPage` tablero 1920×1080 + marcador + bote + X (RNF-4)
- [x] T404 [FE] `HostPage` (celular del presentador): canje del `?claim=` (AC-30) y panel de control — revelar, X, asignar control, siguiente ronda, revancha

## Fase 5 — Cliente jugador
- [x] T501 [FE] `JoinPage` (nombre, equipo, validaciones, errores del contrato) (AC-05…AC-07)
- [x] T502 [FE] `PlayPage`: buzzer grande, estados de espera, marcador (AC-13, AC-24)
- [x] T503 [FE] Recuperación de sesión con `playerToken` (AC-09)

## Fase 6 — Verificación de punta a punta
- [x] T601 [QA] Playwright: pantalla (TV) + presentador móvil + 2 jugadores móviles (viewport iPhone/Pixel) juegan una partida de 3 rondas
- [x] T602 [QA] Matriz de trazabilidad AC → prueba en `specs/001-juego-base/traceability.md`
- [ ] T603 [QA] Prueba manual en LAN con celulares reales según `quickstart.md`
- [x] T604 [BE] Banco ampliado a 50 preguntas de cultura popular (`V3__more_questions.sql`, RNF-5)
- [x] T605 [BE] Banco ampliado a 100 preguntas para la primera versión (`V4__questions_to_100.sql`, RNF-5)
