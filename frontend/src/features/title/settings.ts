import type { CreateRoomRequest } from '../../shared/api/client'

/** Ajustes de partida de la pantalla de título (spec 003, AC-58). Sin nombres de equipo (AC-57). */
export type GameSettings = Pick<CreateRoomRequest, 'rounds' | 'targetScore' | 'turnSeconds' | 'autoStrikeOnTimeout'>

/** Valores por defecto del contrato (openapi.yaml, CreateRoomRequest). */
export const DEFAULT_SETTINGS: GameSettings = { rounds: 5, targetScore: 300, turnSeconds: 20, autoStrikeOnTimeout: true }

const range = (from: number, to: number, step: number) =>
  Array.from({ length: Math.floor((to - from) / step) + 1 }, (_, i) => from + i * step)

export const ROUND_OPTIONS = range(3, 8, 1)
export const TARGET_OPTIONS = range(50, 1000, 50)
/** AC-46: 0 = sin temporizador; si no, de 10 a 60 s. */
export const TURN_OPTIONS = [0, ...range(10, 60, 5)]

const KEY = 'cm:settings'

function pick<T>(value: unknown, options: readonly T[], fallback: T): T {
  return options.includes(value as T) ? (value as T) : fallback
}

/** Lee los ajustes guardados en este navegador; ignora valores fuera de rango. */
export function loadSettings(): GameSettings {
  let raw: unknown = null
  try {
    raw = JSON.parse(localStorage.getItem(KEY) ?? 'null')
  } catch {
    raw = null
  }
  const v = (typeof raw === 'object' && raw !== null ? raw : {}) as Partial<Record<keyof GameSettings, unknown>>
  return {
    rounds: pick(v.rounds, ROUND_OPTIONS, DEFAULT_SETTINGS.rounds),
    targetScore: pick(v.targetScore, TARGET_OPTIONS, DEFAULT_SETTINGS.targetScore),
    turnSeconds: pick(v.turnSeconds, TURN_OPTIONS, DEFAULT_SETTINGS.turnSeconds),
    autoStrikeOnTimeout: typeof v.autoStrikeOnTimeout === 'boolean' ? v.autoStrikeOnTimeout : DEFAULT_SETTINGS.autoStrikeOnTimeout,
  }
}

export function saveSettings(s: GameSettings): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(s))
  } catch {
    /* modo privado: los ajustes no se recordarán */
  }
}

export const turnLabel = (n: number) => (n === 0 ? 'Sin temporizador' : `${n} s`)
