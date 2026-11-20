# Tareas — 009 Despliegue en AWS

## Fase 0
- [x] T1400 Spec, plan y tareas; rama `feat/009-despliegue-aws`

## Backend
- [x] T1401 [BE] `SpaForwardController`: rutas del cliente → `index.html` (AC-123, AC-124)
- [x] T1402 [BE] `APP_PUBLIC_ORIGIN` en `app.allowed-origin-patterns` (AC-126)

## Infraestructura
- [x] T1410 Dockerfile multi-stage, `.dockerignore`, `docker-compose.yml`, `.env.example` (AC-123, AC-125, AC-127)
- [x] T1411 `docs/despliegue-aws.md` (AC-128)

## QA
- [x] T1421 [QA] Prueba MockMvc del fallback SPA y de `network-info` con IP pública/puerto 80 (AC-124, AC-125)
