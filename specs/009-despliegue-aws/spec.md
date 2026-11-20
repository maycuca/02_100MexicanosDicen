# Spec 009 — Despliegue en AWS (una instancia, IP pública)

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-20 |
| Depende de | specs 001–008 |
| Contrato | `specs/001-juego-base/contracts/` **sin cambios** |

## 1. Resumen
Hasta ahora el juego solo corre en la red local: Vite (:5173) sirve el frontend y hace proxy a Spring (:8080). Esta spec permite publicarlo en **una sola instancia de AWS** (Lightsail o EC2) y jugar desde Internet con `http://<IP pública>`, sin dominio ni HTTPS.

No cambia el contrato: `GET /api/network-info` ya devuelve `app.lan-address` y `app.frontend-port`, que en producción se configuran con la IP pública y el puerto 80.

## 2. Historias y criterios de aceptación

### US-35 Un solo servidor sirve todo
- **AC-123** El backend sirve el frontend compilado (`frontend/dist`) en el mismo puerto que `/api` y `/ws`.
- **AC-124** Las rutas del cliente (`/screen/{code}`, `/host/{code}`, `/join/{code}`, `/play/{code}`, `/gracias`) responden con `index.html` al abrirse directamente (desde un QR o al recargar). `/api/**`, `/ws` y `/actuator/**` no se ven afectados.

### US-36 Jugar desde Internet
- **AC-125** Con `APP_LAN_ADDRESS=<IP pública>` y `APP_FRONTEND_PORT=80`, los QR de presentador y jugadores apuntan a `http://<IP pública>:80/...`.
- **AC-126** El origen público se puede permitir en REST y STOMP con la variable `APP_PUBLIC_ORIGIN` sin quitar los orígenes de la LAN.
- **AC-127** Una imagen Docker construye frontend y backend y corre el juego; `docker compose up -d --build` lo levanta en el puerto 80 y la base SQLite persiste en un volumen aunque se reinicie el contenedor.
- **AC-128** `docs/despliegue-aws.md` explica cómo crear la instancia, fijar la IP estática, abrir el puerto 80 y levantar el contenedor.

## 3. Fuera de alcance
- HTTPS y dominio propio (sin HTTPS, "Copiar" en prueba local puede no funcionar en celulares).
- Varias instancias o base de datos administrada (SQLite admite un solo servidor).
