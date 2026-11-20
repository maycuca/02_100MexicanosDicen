# Plan — 009 Despliegue en AWS

## Decisiones
- **D1 Un solo proceso.** La imagen Docker copia `frontend/dist` a `classpath:/static/`; Spring Boot sirve `index.html` en `/` y los assets. Sin Nginx.
- **D2 Fallback SPA explícito.** `SpaForwardController` hace `forward:/index.html` solo para las rutas de `frontend/src/app/routes.tsx`. Una lista explícita evita capturar `/api`, `/ws` o `/actuator`. Si se agrega una ruta al router, se agrega aquí.
- **D3 Configuración por entorno.** Sin perfil nuevo: `APP_LAN_ADDRESS`, `APP_FRONTEND_PORT` y `APP_PUBLIC_ORIGIN` (placeholder en `app.allowed-origin-patterns`, por defecto `http://localhost:*`).
- **D4 Docker multi-stage.** `node:22` (build frontend) → `eclipse-temurin:25-jdk` (`mvnw package -DskipTests`) → `eclipse-temurin:25-jre`. `WORKDIR /app`, así `./data/cien-mexicanos.db` queda en el volumen `/app/data`.
- **D5 AWS Lightsail** (o EC2) con IP estática: el QR incrusta la IP, así que no puede cambiar.

## Archivos
- `backend/src/main/java/mx/cienmexicanos/config/SpaForwardController.java` + prueba.
- `backend/src/main/resources/application.yml` (placeholder `APP_PUBLIC_ORIGIN`).
- `Dockerfile`, `.dockerignore`, `docker-compose.yml`, `.env.example` en la raíz.
- `docs/despliegue-aws.md`.
