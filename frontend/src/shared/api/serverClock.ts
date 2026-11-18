// D17: los relojes de la laptop y los celulares no están sincronizados con el servidor.
// Cada respuesta REST trae el header `Date` (resolución de 1 s). Con la hora local de envío y
// de recepción se acota el desfase: offset ∈ [date − recibido, date + 1000 − enviado].
// Se intersectan los intervalos de varias muestras y se usa el valor más cercano a 0
// (si el reloj local ya está bien, no se "corrige" con el redondeo del header).

type Interval = { lo: number; hi: number }

let bounds: Interval | null = null
let offsetMs = 0

/** Registra una muestra del header `Date` de una respuesta REST. */
export function observeServerDate(dateHeader: string | null | undefined, sentAt: number, receivedAt: number): void {
  if (!dateHeader) return
  const server = Date.parse(dateHeader)
  if (Number.isNaN(server) || receivedAt < sentAt) return
  const sample: Interval = { lo: server - receivedAt, hi: server + 1000 - sentAt }
  const merged = bounds ? { lo: Math.max(bounds.lo, sample.lo), hi: Math.min(bounds.hi, sample.hi) } : sample
  // Intersección vacía: el reloj local (o el del servidor) cambió; se empieza de nuevo.
  bounds = merged.lo <= merged.hi ? merged : sample
  offsetMs = Math.min(Math.max(0, bounds.lo), bounds.hi)
}

/** Desfase estimado (servidor − local) en ms. */
export function serverOffsetMs(): number {
  return offsetMs
}

/** "Ahora" según el reloj del servidor. */
export function serverNow(): number {
  return Date.now() + offsetMs
}

/** Solo para pruebas. */
export function resetServerClock(): void {
  bounds = null
  offsetMs = 0
}
