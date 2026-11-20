# CLAUDE.md — Lo que 100 Mexicanos dicen

> Nombre del juego en la UI: **"Lo que 100 Mexicanos dicen"** (spec 003). En código, paquetes y carpetas se mantiene el identificador `cien-mexicanos` / `mx.cienmexicanos`.

Juego web de encuestas por equipos para jugar en una red local. La **pantalla** (laptop conectada a la TV) muestra el inicio con dos QR: uno para el **Presentador**, que controla la partida desde su celular, y otro para los **Jugadores**, que se unen desde el suyo.

## Fuente de verdad
Este proyecto sigue **Spec-Driven Development**. Antes de tocar código, lee en este orden:
1. `specs/constitution.md` — principios no negociables.
2. `specs/<NNN-feature>/spec.md` — qué se construye (historias, `AC-xx`).
3. `specs/<NNN-feature>/plan.md` y `data-model.md` — cómo.
4. `specs/<NNN-feature>/contracts/openapi.yaml` (REST) y `asyncapi.yaml` (STOMP).
5. `specs/<NNN-feature>/tasks.md` — qué falta y quién lo hace.

Specs: **`001-juego-base`** (implementada), **`002-experiencia-de-juego`** (implementada), **`003-pantalla-de-titulo`** (implementada), **`004-prueba-local`** (implementada), **`005-control-del-presentador`** (implementada), **`006-reglas-cara-a-cara`** (implementada), **`007-celebracion-y-cierre`** (implementada), **`008-pregunta-del-presentador`** (implementada), **`009-despliegue-aws`** (implementada; Docker + AWS, ver `docs/despliegue-aws.md`). El contrato único del sistema vive en `specs/001-juego-base/contracts/`.

### Reglas de trabajo
- **Sin tarea no hay código.** Cada cambio cita su tarea (`T204`) y los criterios que cubre (`AC-14`).
- **Contrato primero.** Ningún endpoint o mensaje fuera de `contracts/`. Si hace falta uno nuevo: se actualiza el contrato → se regenera `frontend/src/shared/api/schema.d.ts` (`npm run gen:api`) y la colección Postman (`python3 postman/scripts/generate_collection.py`) → después se implementa.
- **Spec desactualizada = se corrige primero la spec.**
- Al terminar una tarea, márcala `[x]` en `tasks.md`.
- El servidor es la autoridad del estado; los jugadores nunca reciben respuestas no reveladas (AC-25).

## Stack
| Capa | Tecnología |
|---|---|
| Backend | Java 25 · Spring Boot 4.1.2 (webmvc, websocket/STOMP, data-jpa, validation, actuator, flyway) |
| BD | SQLite (`backend/data/cien-mexicanos.db`) · `hibernate-community-dialects` · migraciones Flyway |
| Frontend | React 19 · TypeScript 7 · Vite 8 · React Router 8 · `@stomp/stompjs` · `qrcode.react` |
| Pruebas | JUnit 5 + MockMvc · Vitest + Testing Library · Playwright · colección Postman |

## Estructura
```
specs/                      constitución + specs por feature (SDD)
backend/                    Spring Boot (paquete mx.cienmexicanos)
  ├─ config/ network/ room/ player/ question/ realtime/ common/
  └─ game/domain/           reglas del juego puras, SIN Spring/JPA
frontend/                   React + Vite
  ├─ src/app/               router
  ├─ src/features/screen    pantalla de TV: inicio con QR y tablero
  ├─ src/features/presenter crear sala + control del presentador (celular)
  ├─ src/features/player    cliente jugador (mobile-first)
  └─ src/shared/            api (tipos generados), realtime, session, ui
postman/                    colección derivada del contrato + entorno local
.claude/agents/             subagentes frontend-dev, backend-dev, qa-tester
```

## Comandos
El JDK por defecto de la máquina es el 27; **usa siempre el 25**:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```
| Tarea | Comando |
|---|---|
| Backend: correr | `cd backend && ./mvnw spring-boot:run` (:8080, escucha en 0.0.0.0) |
| Backend: pruebas | `cd backend && ./mvnw verify` |
| Frontend: correr | `cd frontend && npm run dev` (:5173, expuesto en la LAN, proxy a :8080) |
| Frontend: build / tipos | `npm run build` · `npm run typecheck` |
| Frontend: unit | `npm test` · `npm run coverage` |
| Frontend: lint | `npm run lint` (oxlint) |
| E2E | `cd frontend && npm run e2e` (necesita el backend arriba) |
| Tipos desde OpenAPI | `cd frontend && npm run gen:api` |
| Imagen Docker (spec 009) | `docker compose up -d --build` (requiere `.env` con `PUBLIC_IP`; puerto 80) |
| Colección Postman | `python3 postman/scripts/generate_collection.py` y luego `postman collection run postman/collections/cien-mexicanos.postman_collection.json -e postman/environments/local.postman_environment.json` |

Nunca hagas `postman workspace push` sin el consentimiento del usuario.

## Probar en la LAN
Ver `specs/001-juego-base/quickstart.md`. En resumen: backend y frontend arriba → abrir `http://localhost:5173/` y crear sala → la pantalla de inicio muestra los QR de presentador y jugadores → escanearlos con celulares en la misma Wi-Fi.

## Probar sin celulares (spec 004)
AJUSTES → **Prueba local: Sí** → EMPEZAR. En vez de QR, la pantalla muestra las URL (`http://localhost:5173/...`) con "Copiar" y "Abrir en pestaña nueva". Cada pestaña es una sesión distinta (`sessionStorage`), así que en un solo navegador puedes tener la TV, el presentador y varios jugadores. El código está aislado en `frontend/src/features/localtest/`.

## Convenciones
- Código, identificadores y commits en **inglés** (Conventional Commits: `feat(backend): … [T204]`).
- UI, specs y documentación en **español** con acentos correctos.
- Java: records para DTOs, `sealed`/pattern matching cuando aplique, inyección por constructor, sin Lombok.
- TS: componentes funcionales, sin `any`, tipos de API solo desde `schema.d.ts`.
- Identidad visual y preguntas **originales** (constitución §VII).

## Subagentes
| Agente | Úsalo para | Dueño de |
|---|---|---|
| `backend-dev` | tareas `[BE]`: dominio, REST, STOMP, SQLite | `backend/` |
| `frontend-dev` | tareas `[FE]`: pantallas de presentador y jugador, hooks, estilos | `frontend/src/` (excepto tests) |
| `qa-tester` | tareas `[QA]`: pruebas unitarias, integración, e2e, Postman y trazabilidad | archivos de prueba, `postman/`, `traceability.md` |

Las tareas `[P]` de distintos agentes pueden ir en paralelo. Ningún agente cambia `contracts/` por su cuenta: propone el cambio y el agente principal lo aplica.
