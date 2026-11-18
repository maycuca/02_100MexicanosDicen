# Quickstart — 001 Juego base

## Requisitos
- JDK 25 (`/usr/libexec/java_home -v 25`)
- Node.js ≥ 20
- Laptop y celulares en la **misma red Wi-Fi**

## Levantar
```bash
# Terminal 1 — backend (:8080)
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw spring-boot:run

# Terminal 2 — frontend (:5173, expuesto en la LAN)
cd frontend
npm install
npm run dev        # muestra "Network: http://192.168.x.y:5173/"
```

## Jugar en la LAN
1. En la laptop conectada a la TV abre `http://localhost:5173/` y crea una sala.
2. Se abre la **pantalla de inicio** (`/screen/<CÓDIGO>`) con dos QR. Si la IP de la URL no es correcta, reinicia el backend con `./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.lan-address=192.168.x.y`.
3. El presentador escanea el QR **Presentador** (un solo uso) para controlar la partida desde su celular.
4. Cada jugador escanea el QR **Jugadores**, escribe su nombre y elige equipo.

## Probar sin celulares (spec 004)
1. En `http://localhost:5173/` abre **AJUSTES**, pon **Prueba local** en **Sí** y pulsa **EMPEZAR**.
2. En lugar de los QR verás dos URL con los botones **Copiar** y **Abrir en pestaña nueva**.
3. Abre la URL de **Presentador** en una pestaña y la de **Jugadores** en una pestaña por jugador. Pueden ser pestañas del mismo navegador, porque cada una tiene su propia sesión.
4. Para volver al modo con celulares, pon **Prueba local** en **No**.

## Comprobaciones rápidas
```bash
curl http://localhost:8080/actuator/health        # {"status":"UP"}
curl http://localhost:8080/api/network-info       # {"lanAddress":"192.168.x.y",...}
```

## Problemas comunes
| Síntoma | Solución |
|---|---|
| El celular no abre la página | macOS → Ajustes → Red → Firewall: permitir `node` y `java`, o desactivarlo temporalmente |
| Funciona en la laptop pero no en el celular | La red tiene "aislamiento de clientes" (común en redes de invitados/oficina). Usa el hotspot del celular o un router propio |
| El QR apunta a una IP rara (10.x, 172.x) | Es una VPN o Docker. Arranca el backend con `--app.lan-address=192.168.x.y` |
| Se cae el WebSocket al bloquear el celular | Es esperado; al volver la app se reconecta y recupera la sesión |
