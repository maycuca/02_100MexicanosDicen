# Tareas — 002 Experiencia de juego

Leyenda: `[BE]` backend-dev · `[FE]` frontend-dev · `[QA]` qa-tester · `[P]` en paralelo · `→` depende de.

## Fase 0 — Especificación
- [x] T700 Spec, plan, contrato v0.4.0, tipos regenerados

## Backend
- [x] T701 [BE] Dominio: `activeTeam` según AC-35 y evento `TURN_CHANGED` (AC-36)
- [x] T702 [BE] Dominio: X al equipo en turno, `strikesByTeam`, `NO_ACTIVE_TEAM`, `STRIKE` con `team`/`strikes` (AC-40…AC-43)
- [x] T703 [BE] Configuración `turnSeconds`/`autoStrikeOnTimeout` en createRoom + `turnDeadline` en el dominio (AC-46, AC-47)
- [x] T704 [BE] `TurnTimerScheduler` + `expireTurn` con X automática y sin X dobles (AC-48, AC-49)
- [x] T705 [BE] `POST /sound`, `soundEnabled`, `SOUND_TOGGLED` (AC-53)
- [x] T706 [BE] Pruebas unitarias de dominio y del temporizador con `Clock` controlado

## Frontend
- [x] T711 [FE][P] TV: resalte del equipo en turno y X por equipo con animación del color del equipo (AC-37, AC-44)
- [x] T712 [FE][P] Componente `Countdown` sobre `turnDeadline` con corrección de reloj; en TV, presentador y jugador (AC-50)
- [x] T713 [FE][P] `shared/audio`: síntesis Web Audio + `useGameSounds` por evento; "Activar sonido" en la pantalla de inicio (AC-51, AC-52)
- [x] T714 [FE] Presentador: equipo en turno, "X para <equipo>", silenciar/activar sonido (AC-39, AC-45, AC-53)
- [x] T715 [FE] Crear sala: configuración del temporizador (AC-46)
- [x] T716 [FE] Jugador: banner de turno y vibración (AC-38, AC-54)

## QA
- [x] T721 [QA][P] Aceptación: equipo en turno por estado y `TURN_CHANGED` (AC-35, AC-36)
- [x] T722 [QA][P] Aceptación: X al equipo en turno, `strikesByTeam`, `NO_ACTIVE_TEAM` (AC-40…AC-43)
- [x] T723 [QA][P] Aceptación: temporizador, auto-X, sin auto-X, sin X dobles (AC-46…AC-49)
- [x] T724 [QA][P] Aceptación: sonido (AC-53)
- [x] T725 [QA] E2E: resalte de turno cara a cara → juego → robo; X en el equipo correcto; temporizador de 10 s que vence
- [x] T726 [QA] Postman y `traceability.md` de la spec 002
- [x] T727 [QA] Regresión: las pruebas de 001 siguen pasando (ajustar solo las que cambian por AC-40, p. ej. X en FACE_OFF sin buzz)
