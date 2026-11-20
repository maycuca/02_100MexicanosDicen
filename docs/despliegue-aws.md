# Despliegue en AWS (spec 009)

Una sola instancia con Docker. El contenedor sirve el frontend, `/api` y `/ws` en el puerto 80, y guarda SQLite en `./data`.

## 1. Crear la instancia
**Lightsail** (lo más simple):
1. Lightsail → *Create instance* → Linux/Unix → **Ubuntu 24.04** → plan de 1 GB RAM o más.
2. *Networking* → **Create static IP** y asígnala a la instancia. Es obligatoria: los QR llevan la IP, así que no puede cambiar.
3. *Networking* → *IPv4 Firewall*: deja **HTTP (80)** abierto. Limita **SSH (22)** a tu IP.

**EC2** (alternativa): `t4g.small` o `t3.small` con Ubuntu 24.04, una **Elastic IP** asociada y un Security Group con 80 abierto y 22 solo desde tu IP.

## 2. Instalar Docker
```bash
ssh ubuntu@<IP pública>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && exit   # vuelve a entrar para aplicar el grupo
```

## 3. Levantar el juego
```bash
git clone <url-del-repo> cien-mexicanos && cd cien-mexicanos
git switch feat/009-despliegue-aws        # o main, cuando se haga merge
cp .env.example .env && nano .env         # PUBLIC_IP=<IP pública>
docker compose up -d --build
curl http://localhost/actuator/health     # {"status":"UP",...}
```
Abre `http://<IP pública>/` en la laptop de la TV, crea la sala y escanea los QR con celulares (con datos móviles o cualquier Wi-Fi).

## 4. Operación
| Tarea | Comando |
|---|---|
| Ver logs | `docker compose logs -f` |
| Actualizar | `git pull && docker compose up -d --build` |
| Reiniciar | `docker compose restart` |
| Respaldar la base | `cp data/cien-mexicanos.db ~/respaldo-$(date +%F).db` |

## Limitaciones
- Sin HTTPS: el botón "Copiar" de la prueba local puede fallar en celulares; el juego funciona igual.
- Cualquiera con el código de sala puede unirse; las acciones del presentador siguen protegidas con `X-Presenter-Token`.
- SQLite: una sola instancia; no se puede escalar horizontalmente.
