import { URGENT_SECONDS, secondsLeft, useCountdown } from './useCountdown'

type Props = {
  /** `round.turnDeadline` del snapshot (el servidor es la autoridad, AC-50). */
  deadline: string | null | undefined
  /** Spec 005 (AC-82): con el temporizador detenido, lo que queda (se muestra fijo). */
  pausedMs?: number | null
  /** Variante de tamaño: TV o celular. */
  size?: 'tv' | 'mobile'
  label?: string
}

/** Texto para lectores de pantalla: solo cambia al entrar a los últimos segundos y al terminar. */
function announcement(secs: number): string {
  if (secs <= 0) return '¡Se acabó el tiempo!'
  if (secs <= URGENT_SECONDS) return `Quedan ${URGENT_SECONDS} segundos o menos`
  return ''
}

/**
 * Cuenta regresiva del turno (T712, AC-50). Spec 005 (T1012, AC-82): detenida muestra el número fijo
 * ("en pausa"). No se muestra si no hay temporizador.
 */
export function Countdown({ deadline, pausedMs = null, size = 'mobile', label = 'Tiempo del turno' }: Props) {
  const ms = useCountdown(deadline)
  const running = ms !== null
  const value = running ? ms : pausedMs
  if (value === null) return null
  const secs = secondsLeft(value)
  const urgent = secs <= URGENT_SECONDS
  return (
    <div
      className={`countdown countdown--${size}${urgent ? ' countdown--urgent' : ''}${running ? '' : ' countdown--paused'}`}
      role="timer"
      aria-label={`${label}: ${secs} segundos${running ? '' : ', detenido'}`}
    >
      <span className="countdown__value" aria-hidden="true">{secs}</span>
      {running && <span className="sr-only" aria-live="polite" aria-atomic="true">{announcement(secs)}</span>}
    </div>
  )
}
