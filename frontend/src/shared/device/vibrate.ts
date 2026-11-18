// AC-54: vibración del celular del jugador. En navegadores sin `navigator.vibrate` no pasa nada.

export const VIBRATION = {
  buzzWon: [80, 40, 80],
  turn: [200],
  strike: [400],
  /** Spec 007 (AC-116): festejo al ganar la partida. */
  win: [120, 60, 120, 60, 320],
} as const

export function vibrate(pattern: readonly number[]): boolean {
  try {
    if (typeof navigator === 'undefined' || typeof navigator.vibrate !== 'function') return false
    return navigator.vibrate([...pattern])
  } catch {
    return false
  }
}
