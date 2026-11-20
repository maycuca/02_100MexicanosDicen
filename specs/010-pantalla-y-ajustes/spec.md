# Spec 010 — Pantalla siempre encendida, sonido activado y turno de 10 s

| Campo | Valor |
|---|---|
| Estado | Aprobada para implementar |
| Fecha | 2026-11-20 |
| Depende de | specs 001–008 |
| Contrato | `specs/001-juego-base/contracts/` **v0.9.0** (solo cambia el valor por defecto de `turnSeconds`) |

## 1. Resumen
Tres ajustes de experiencia para jugar sin interrupciones:
1. La TV y los celulares no apagan ni bloquean la pantalla durante la partida.
2. El sonido de la TV viene activado.
3. El turno por pregunta dura 10 s por defecto.

## 2. Historias y criterios de aceptación

### US-37 Pantalla encendida
- **AC-129** La TV (`/screen/{code}`), el presentador (`/host/{code}`) y los jugadores (`/join/{code}`, `/play/{code}`) piden mantener la pantalla encendida mientras la página está abierta. La pantalla de título y `/gracias` no lo piden.
- **AC-130** Si el navegador tiene `navigator.wakeLock` (HTTPS o `localhost`), se usa. Si no (HTTP en la LAN o en AWS), se usa un video mudo en bucle. Como el navegador exige una interacción para reproducirlo, se activa con el primer toque, clic o tecla.
- **AC-131** Al volver a la pestaña (`visibilitychange` → `visible`) se pide de nuevo, porque el navegador lo suelta al ocultarla.
- **AC-132** Al salir de la página se libera. Si el navegador no lo soporta o lo rechaza, no pasa nada: sin errores ni avisos.

### US-38 Sonido activado
- **AC-133** La TV tiene el sonido activado por defecto, y también los sonidos del menú de título. Solo queda apagado si alguien lo desactivó en AJUSTES de ese navegador (`localStorage['cm:sound'] = 'off'`). El navegador exige una interacción para reproducir audio, así que suena desde el primer toque o clic.

### US-39 Turno de 10 s
- **AC-134** El tiempo de turno por defecto es **10 s**, al crear una sala sin `turnSeconds` (`POST /api/rooms`) y en AJUSTES de la pantalla de título. Un navegador que ya guardó otro valor en AJUSTES lo conserva.

## 3. Limitaciones
- En iPhone, Apple puede limitar el truco del video. Si se bloquea, hay que desactivar el bloqueo durante la partida en *Ajustes → Pantalla y brillo → Bloqueo automático → Nunca*.
- El navegador no evita que la computadora de la TV se suspenda: eso se configura en el sistema operativo.
