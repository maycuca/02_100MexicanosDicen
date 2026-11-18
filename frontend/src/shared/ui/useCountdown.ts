import { useEffect, useState } from 'react'
import { serverNow } from '../api/serverClock'

/** Cada cuánto se recalcula la cuenta regresiva (D17). */
export const COUNTDOWN_TICK_MS = 250
/** Los últimos segundos se muestran en rojo y suenan con tic (AC-50, AC-51). */
export const URGENT_SECONDS = 5

/**
 * Milisegundos que faltan para `deadline` (ISO-8601 del servidor), corregidos con el
 * desfase de reloj (D17). null si no hay temporizador activo.
 */
export function useCountdown(deadline: string | null | undefined): number | null {
  const target = deadline ? Date.parse(deadline) : Number.NaN
  const [now, setNow] = useState(() => serverNow())

  useEffect(() => {
    if (Number.isNaN(target)) return
    const update = () => setNow(serverNow())
    // Primer cálculo en cuanto cambia el vencimiento; luego cada 250 ms.
    const first = setTimeout(update, 0)
    const id = setInterval(update, COUNTDOWN_TICK_MS)
    return () => {
      clearTimeout(first)
      clearInterval(id)
    }
  }, [target])

  if (Number.isNaN(target)) return null
  return Math.max(0, target - now)
}

/** Segundos enteros que se muestran (redondeo hacia arriba: "1" hasta que llega a 0). */
export const secondsLeft = (ms: number) => Math.ceil(ms / 1000)
