# Tareas — 005 Control del presentador

Leyenda: `[BE]` backend-dev · `[FE]` frontend-dev · `[QA]` qa-tester · `[P]` en paralelo.

## Fase 0
- [x] T1000 Spec y contrato v0.6.0 (`timer`, `DELETE /rooms/{code}`, `timerRunning`, `timerRemainingMs`, eventos `TIMER_CHANGED`, `FACE_OFF_MISS`, `ROOM_CLOSED`, error `TIMER_DISABLED`); tipos y colección Postman regenerados

## Backend
- [x] T1001 [BE] Temporizador manual en `Round`/`Room`: start/pause/reset, vencimiento sin reinicio, reinicio al cambiar de ronda (AC-79…AC-84)
- [x] T1002 [BE] Fallo en cara a cara = `FACE_OFF_MISS` sin X, turno alterno (AC-85)
- [x] T1003 [BE] `DELETE /api/rooms/{code}` con `ROOM_CLOSED` (AC-91)

## Frontend
- [x] T1011 [FE] Presentador: Iniciar/Reanudar, Parar, Restablecer; botón "Falló … · pasa el turno"; "Menú principal" con advertencia (AC-80, AC-86, AC-90, AC-91)
- [x] T1012 [FE] Cuenta regresiva detenida/corriendo en TV, presentador y celular (AC-82)
- [x] T1013 [FE] TV: sin "Bote", sin botón de sonido ni QR de presentador en el tablero; "¡Pasa a …!"; vuelve a `/` con `ROOM_CLOSED`; jugadores ven la sala cerrada (AC-86…AC-88, AC-91)
- [x] T1014 [FE] Sonido de júbilo al revelar y aviso de fallo en cara a cara (AC-86, AC-89)

## US-27 (ajustes visuales)
- [x] T1031 [FE] Iconos del temporizador en una fila, switch de sonido, círculo centrado y jugador sin "Bote" (AC-106…AC-109)
- [x] T1032 [QA] Vitest, e2e y trazabilidad (AC-106…AC-109)

## QA
- [x] T1021 [QA] Unit y aceptación de backend (AC-79…AC-85, AC-91)
- [x] T1022 [QA] Vitest y e2e; ajustar pruebas existentes (X en cara a cara, botón de sonido del tablero, "Bote")
- [x] T1023 [QA] Postman y `traceability.md`
