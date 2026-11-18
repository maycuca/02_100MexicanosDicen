# Tareas — 004 Prueba local

Leyenda: `[BE]` backend-dev · `[FE]` frontend-dev · `[QA]` qa-tester · `[P]` en paralelo.

## Fase 0
- [x] T900 Spec 004 (sin cambios de contrato ni de backend)

## Frontend
- [x] T901 [FE] `shared/session`: `setSessionScope('local' | 'tab')` (AC-76)
- [x] T902 [FE] `features/localtest`: bandera, `installLocalTest`, `useLocalTestMode`, `LocalTestLink`, `LocalTestBadge`, `LocalTestBanner` (AC-73…AC-77)
- [x] T903 [FE] Fila "Prueba local" en AJUSTES y distintivo en la pantalla de título (AC-73, AC-74)
- [x] T904 [FE] Pantalla de QR en modo prueba local: URL con origen del navegador, Copiar, Abrir en pestaña nueva y aviso (AC-75, AC-78)

## QA
- [x] T911 [QA] Vitest de `features/localtest` y de la pantalla de QR (AC-73…AC-78)
- [x] T912 [QA] E2E: en un mismo contexto del navegador, presentador y dos jugadores en pestañas distintas; recargar una pestaña conserva su sesión (AC-75, AC-76)
- [x] T913 [QA] `traceability.md`
