# Tareas — 006 Reglas del cara a cara

Leyenda: `[BE]` backend-dev · `[FE]` frontend-dev · `[QA]` qa-tester · `[P]` en paralelo.

## Fase 0
- [x] T1100 Spec y contrato v0.7.0 (`FaceOffState.firstTeam`, `firstAnswerPosition`); tipos regenerados

## Backend
- [x] T1101 [BE] `FaceOff` con primer/segundo intento y resolución automática; `reveal`, fallo y vencimiento en `FACE_OFF` (AC-92…AC-100)
- [x] T1102 [BE] Proyecciones con los campos nuevos (AC-101)

## Frontend
- [x] T1111 [FE] Presentador sin "Asignar control"; pista, etiquetas del botón de fallo y revelar bloqueado sin buzzer (AC-102)
- [x] T1112 [FE] TV y celulares: textos del segundo intento (AC-103)

## QA
- [x] T1121 [QA] Unit y aceptación de backend; ajustar las pruebas que revelaban sin buzzer o alternaban fallos
- [x] T1122 [QA] Vitest y e2e
- [x] T1123 [QA] Postman y `traceability.md`
