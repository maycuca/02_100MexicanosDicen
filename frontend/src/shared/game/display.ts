import type { RoomState, Team, TeamId } from '../api/client'

// Ayudas de presentación (sin reglas de juego: todo sale del snapshot del servidor).

export const otherTeam = (id: TeamId): TeamId => (id === 'A' ? 'B' : 'A')

export const teamColor = (id: TeamId) => (id === 'A' ? 'var(--cm-team-a)' : 'var(--cm-team-b)')

/** Equipo en turno según el servidor (spec 002, AC-35); null si nadie responde. */
export function activeTeamOf(state: RoomState | null): TeamId | null {
  return state?.round?.activeTeam ?? null
}

/** X que cuentan para un equipo en la ronda (AC-41). */
export function strikesOf(state: RoomState | null, id: TeamId): number {
  return state?.round?.strikesByTeam?.[id] ?? 0
}

export function findTeam(state: RoomState | null, id: TeamId | null | undefined): Team | undefined {
  return id ? state?.teams.find((t) => t.id === id) : undefined
}

export function teamName(state: RoomState | null, id: TeamId | null | undefined): string {
  return findTeam(state, id)?.name ?? (id ? `Equipo ${id}` : '')
}

/** Spec 007 (AC-117): equipo del jugador, o null si no se encuentra. */
export function playerTeam(state: RoomState | null, playerId: string | null | undefined): TeamId | null {
  if (!playerId || !state) return null
  return state.teams.find((t) => t.players.some((p) => p.id === playerId))?.id ?? null
}

export function playerName(state: RoomState | null, playerId: string | null | undefined): string {
  if (!playerId || !state) return ''
  for (const t of state.teams) {
    const p = t.players.find((x) => x.id === playerId)
    if (p) return p.name
  }
  return ''
}

/** Equipos en orden A, B (o los nombres por defecto si aún no hay snapshot). */
export function orderedTeams(state: RoomState | null): Team[] {
  if (!state) {
    return [
      { id: 'A', name: 'Equipo Rojo', captainId: null, score: 0, players: [] },
      { id: 'B', name: 'Equipo Verde', captainId: null, score: 0, players: [] },
    ]
  }
  return [...state.teams].sort((a, b) => a.id.localeCompare(b.id))
}

export const STATUS_LABEL: Record<RoomState['status'], string> = {
  LOBBY: 'Sala de espera',
  FACE_OFF: 'Cara a cara',
  PLAYING: 'Ronda en juego',
  STEAL: 'Robo',
  ROUND_END: 'Fin de la ronda',
  GAME_OVER: 'Fin del juego',
}

export function winnerText(state: RoomState): string {
  if (state.winner === 'TIE') return '¡Empate!'
  if (state.winner === 'A' || state.winner === 'B') return `¡Gana ${teamName(state, state.winner)}!`
  return 'Fin del juego'
}

const TIMED: readonly RoomState['status'][] = ['FACE_OFF', 'PLAYING', 'STEAL']

/**
 * Spec 005 (AC-82): datos del temporizador para `<Countdown>`. Corriendo → `deadline`; detenido → `pausedMs`
 * (número fijo). Sin temporizador o fuera de FACE_OFF/PLAYING/STEAL no se muestra nada.
 */
export function timerOf(state: RoomState | null): { deadline: string | null; pausedMs: number | null } {
  const round = state?.round
  if (!state || !round || state.turnSeconds <= 0 || !TIMED.includes(state.status)) return { deadline: null, pausedMs: null }
  return round.timerRunning && round.turnDeadline
    ? { deadline: round.turnDeadline, pausedMs: null }
    : { deadline: null, pausedMs: round.timerRemainingMs }
}

/**
 * Spec 006 (AC-101…AC-103): en qué va el cara a cara. null fuera de FACE_OFF o antes del buzzer.
 * `attempt` 1 = responde el equipo del buzzer; 2 = responde el otro, que debe superar `firstAnswerPosition`.
 */
export function faceOffInfo(
  state: RoomState | null,
): { attempt: 1 | 2; firstTeam: TeamId; secondTeam: TeamId; firstAnswerPosition: number | null } | null {
  const round = state?.round
  const firstTeam = round?.faceOff?.firstTeam ?? null
  if (!state || state.status !== 'FACE_OFF' || !round || !firstTeam) return null
  const secondTeam = otherTeam(firstTeam)
  return {
    attempt: round.activeTeam === secondTeam ? 2 : 1,
    firstTeam,
    secondTeam,
    firstAnswerPosition: round.faceOff?.firstAnswerPosition ?? null,
  }
}

/** AC-103: texto del segundo intento del cara a cara para la TV y los celulares. */
export function faceOffSecondText(state: RoomState | null): string | null {
  const info = faceOffInfo(state)
  if (!info || info.attempt !== 2) return null
  const second = teamName(state, info.secondTeam)
  return info.firstAnswerPosition !== null
    ? `¡${second} debe superar la #${info.firstAnswerPosition}!`
    : `¡${second} gana el control con cualquier respuesta!`
}
