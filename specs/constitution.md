# Constitución del proyecto — Cien Mexicanos

Principios no negociables. Toda spec, plan, tarea y línea de código debe respetarlos. Cambiar esta constitución requiere una decisión explícita del equipo registrada al final del archivo.

## I. Spec-Driven Development
1. El flujo es `constitution → spec → plan → contracts → tasks → implement → verify`.
2. No se escribe código sin una tarea en `specs/NNN-*/tasks.md` que lo justifique.
3. Cada feature vive en su propia carpeta `specs/NNN-nombre-corto/`.
4. Si la implementación descubre algo que la spec no cubre, **primero se actualiza la spec** y después el código.

## II. Contrato primero
1. Ningún endpoint REST existe sin estar en `contracts/openapi.yaml`.
2. Ningún mensaje WebSocket/STOMP existe sin estar en `contracts/asyncapi.yaml`.
3. Los cambios de contrato los propone cualquier agente, pero se aplican de forma explícita y en un cambio separado.
4. Los tipos del frontend se generan desde el contrato; no se escriben a mano.

## III. El servidor es la autoridad
1. El estado de la partida vive solo en el backend. Los clientes envían intenciones y renderizan snapshots.
2. Las reglas del juego (puntos, strikes, robo, turnos, buzzer) se resuelven en el backend, en un dominio puro sin dependencias de Spring.
3. Un cliente nunca recibe las respuestas ocultas antes de ser reveladas (excepto el presentador autenticado).

## IV. Dos clientes, una red local
1. **Presentador** (admin): pantalla grande, crea la sala y controla la partida. Se autentica con `presenterToken`.
2. **Jugador** (N): celular, mobile-first, se une por QR. Se identifica con `playerToken`.
3. Todo debe funcionar en una LAN sin internet: sin CDNs en tiempo de ejecución ni servicios externos.

## V. Calidad verificable
1. Cada criterio de aceptación de una spec tiene al menos una prueba automatizada que lo referencia por su ID (p. ej. `AC-03`).
2. El dominio de juego tiene cobertura de pruebas unitarias ≥ 90 %.
3. `./mvnw verify`, `npm run build`, `npm test` y la colección Postman deben pasar antes de dar una tarea por terminada.

## VI. Simplicidad
1. Sin dependencias nuevas si la plataforma ya lo resuelve.
2. SQLite como única base de datos mientras sea suficiente.
3. Nada de abstracciones especulativas: se construye lo que pide la spec actual.

## VII. Contenido e identidad originales
1. Nombre, paleta, tipografía, sonidos y preguntas son propios del proyecto.
2. No se copian logos, música, gráficos ni frases distintivas de programas de televisión existentes.

## VIII. Convenciones
- Código, identificadores y commits: inglés. Commits convencionales (`feat:`, `fix:`, `test:`, `docs:`).
- UI, specs y documentación: español con ortografía correcta.

---
_Historial de cambios_
- 2026-11-17 — v1.0 inicial.
