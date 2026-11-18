# Tareas — 003 Pantalla de título

Leyenda: `[BE]` backend-dev · `[FE]` frontend-dev · `[QA]` qa-tester · `[P]` en paralelo.

## Fase 0
- [x] T800 Spec, contrato v0.5.0, tipos regenerados

## Backend
- [x] T801 [BE] `Team.captainId` (primer jugador del equipo) en dominio y proyecciones (AC-62)
- [x] T802 [BE] `PUT /api/rooms/{code}/teams/{team}/name` con reglas y errores; evento `TEAM_RENAMED`; revancha conserva nombres y capitanes (AC-63…AC-65, AC-67)

## Frontend
- [x] T811 [FE][P] Pantalla de título `/`: logotipo, fondo animado, menú EMPEZAR/AJUSTES con teclado, mouse y toque (AC-55, AC-56, AC-60)
- [x] T812 [FE][P] Modal de AJUSTES con persistencia y EMPEZAR sin nombres de equipo (AC-57, AC-58)
- [x] T813 [FE][P] Sonidos de menú con `shared/audio` (AC-59)
- [x] T814 [FE] Título "Lo que 100 Mexicanos dicen" en todas las vistas y logotipo reutilizable en `/screen` (AC-61)
- [x] T815 [FE] Celular: capitán nombra a su equipo; nombres actuales en `/join` y en la espera; `renameTeam` en el cliente (AC-66)

## QA
- [x] T821 [QA][P] Aceptación: capitán y cambio de nombre, todos los errores, `TEAM_RENAMED`, revancha (AC-62…AC-65, AC-67)
- [x] T822 [QA] E2E: título y menú con teclado, modal de ajustes que persiste, EMPEZAR → `/screen`, capitán renombra y la TV lo muestra (AC-55…AC-58, AC-61, AC-65, AC-66); ajustar e2e existentes que usaban "Crear sala"
- [x] T823 [QA] Postman y `traceability.md` de la spec 003

## US-18 (2026-11-18)
- [x] T831 [FE] Pantalla de QR sin código de sala, IP ni botón de sonido; URL de los QR visible (AC-68…AC-70)
- [x] T832 [QA] Ajuste de Vitest y e2e; trazabilidad
- [x] T833 [FE] Logotipo más pequeño en la pantalla de QR y botón circular "Regresar" (AC-71, AC-72)
