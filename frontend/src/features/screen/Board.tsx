import { useEffect, useState, type CSSProperties, type ReactNode } from 'react'
import type { BoardSlot, RoomState, TeamId } from '../../shared/api/client'
import type { GameEvent } from '../../shared/realtime/messages'
import { Countdown } from '../../shared/ui/Countdown'
import { Confetti } from '../../shared/ui/Confetti'
import { useCountUp } from '../../shared/ui/useCountUp'
import {
  activeTeamOf,
  faceOffInfo,
  orderedTeams,
  otherTeam,
  playerName,
  playerTeam,
  strikesOf,
  teamColor,
  teamName,
  timerOf,
  winnerText,
} from '../../shared/game/display'

type Props = {
  state: RoomState
  lastEvent: GameEvent | null
  /** Último evento STRIKE (se pasa aparte para no perderlo si llegan varios eventos juntos). */
  strikeEvent?: GameEvent | null
  /** Spec 005 (AC-86): último FACE_OFF_MISS (fallo sin X en el cara a cara). */
  missEvent?: GameEvent | null
}

/** Duración de la X gigante en pantalla tras un STRIKE. */
const STRIKE_FLASH_MS = 1400
/** Duración del aviso "¡Pasa a …!" tras un fallo en el cara a cara. */
const MISS_FLASH_MS = 1800

type Flash = { team: TeamId | null; count: number }

/**
 * Tablero de la TV (T403, RNF-4, AC-34; spec 002: T711, T712). Solo muestra: sin controles de juego.
 * Las casillas ocultas nunca traen texto ni puntos (AC-25). Resalta el equipo en turno (AC-37),
 * muestra las X de cada equipo y la X gigante con su color (AC-44) y la cuenta regresiva (AC-50).
 * Spec 005 (T1013): sin etiqueta "Bote" (AC-87); cuenta detenida (AC-82); "¡Pasa a …!" sin X en el cara a cara (AC-86).
 * Spec 007 (T1201, T1205): estado con equipos a color y puntos en dorado (AC-110); resultados de fiesta (AC-114).
 */
export function Board({ state, lastEvent, strikeEvent = null, missEvent = null }: Props) {
  const round = state.round ?? null
  const [flash, setFlash] = useState<Flash | null>(null)
  const [passTo, setPassTo] = useState<TeamId | null>(null)

  useEffect(() => {
    if (missEvent?.type !== 'FACE_OFF_MISS' || !missEvent.team) return
    setPassTo(otherTeam(missEvent.team))
    const t = setTimeout(() => setPassTo(null), MISS_FLASH_MS)
    return () => clearTimeout(t)
  }, [missEvent])

  useEffect(() => {
    if (strikeEvent?.type !== 'STRIKE') return
    const team = strikeEvent.team ?? null
    setFlash({ team, count: strikeEvent.strikes ?? (team ? strikesOf(state, team) : round?.strikes ?? 1) })
    const t = setTimeout(() => setFlash(null), STRIKE_FLASH_MS)
    return () => clearTimeout(t)
    // Solo reacciona a eventos nuevos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [strikeEvent])

  if (state.status === 'GAME_OVER') return <GameOver state={state} />

  const teams = orderedTeams(state)
  const slots = [...(round?.board ?? [])].sort((a, b) => a.position - b.position)
  const rows = Math.max(1, Math.ceil(slots.length / 2))
  const active = activeTeamOf(state)
  const flashCount = flash ? Math.min(3, Math.max(1, flash.count)) : 0

  return (
    <div className="board">
      <header className="board__top">
        {teams.map((t, i) => {
          const turn = active === t.id
          const strikes = strikesOf(state, t.id)
          return (
            <div
              key={t.id}
              className={`board__side${turn ? ' board__side--turn' : ''}${active && !turn ? ' board__side--dim' : ''}`}
              style={{ '--team-color': teamColor(t.id), order: i === 0 ? 0 : 2 } as CSSProperties}
              data-testid={`team-panel-${t.id}`}
            >
              <div className="board__team" aria-current={turn ? 'true' : undefined}>
                {turn && <span className="board__turn-tag">EN TURNO</span>}
                <span className="board__team-name">{t.name}</span>
                <span className="board__team-score" aria-label={`Marcador de ${t.name}`}>{t.score}</span>
              </div>
              {round && (
                <div className="board__team-strikes" aria-label={`X de ${t.name}: ${strikes}`}>
                  {[1, 2, 3].map((n) => (
                    <span key={n} className={`strike${strikes >= n ? ' strike--on' : ''}`} aria-hidden="true">✕</span>
                  ))}
                </div>
              )}
            </div>
          )
        })}
        <div className="board__center" style={{ order: 1 }}>
          {round && (
            <>
              <span className="board__round">Ronda {round.number} de {state.totalRounds}</span>
              <span className="board__pot" aria-label="Puntos de la ronda">{round.pot}</span>
              {round.multiplier > 1 && <span className="board__mult" aria-label={`Puntos ×${round.multiplier}`}>×{round.multiplier}</span>}
            </>
          )}
        </div>
      </header>

      <h2 className="board__question">{round?.question ?? ''}</h2>

      <ol className="board__slots" style={{ gridTemplateRows: `repeat(${rows}, 1fr)` }} aria-label="Respuestas">
        {slots.map((s) => <Slot key={s.position} slot={s} />)}
      </ol>

      <div className="board__timer">
        <Countdown {...timerOf(state)} size="tv" />
      </div>

      <p className="board__status" aria-live="polite">
        {passTo && state.status === 'FACE_OFF' ? <>¡Pasa a <TeamTag state={state} id={passTo} />!</> : statusLine(state, lastEvent)}
      </p>
      <p className="sr-only" aria-live="polite">{active ? `En turno: ${teamName(state, active)}` : ''}</p>

      {flash !== null && (
        <div
          className="strike-flash"
          role="img"
          aria-label={flash.team ? `X para ${teamName(state, flash.team)}` : 'X'}
          style={flash.team ? ({ '--flash-color': teamColor(flash.team) } as CSSProperties) : undefined}
          data-team={flash.team ?? undefined}
        >
          {Array.from({ length: flashCount }, (_, i) => <span key={i}>✕</span>)}
        </div>
      )}
    </div>
  )
}

function Slot({ slot }: { slot: BoardSlot }) {
  return (
    <li className={`slot${slot.revealed ? ' slot--revealed' : ''}`}>
      {slot.revealed ? (
        <>
          <span className="slot__text">{slot.text}</span>
          <span className="slot__points">{slot.points}</span>
        </>
      ) : (
        <span className="slot__number" aria-label={`Respuesta ${slot.position} oculta`}>{slot.position}</span>
      )}
    </li>
  )
}

/** AC-110: nombre del equipo con su color. */
function TeamTag({ state, id }: { state: RoomState; id: TeamId }) {
  return (
    <span className="status-team" style={{ color: teamColor(id) }}>
      {teamName(state, id)}
    </span>
  )
}

/** AC-117: nombre del jugador con el color de su equipo. */
function PlayerTag({ state, id }: { state: RoomState; id: string | null | undefined }) {
  const team = playerTeam(state, id)
  return (
    <span className="status-player" style={team ? { color: teamColor(team) } : undefined}>
      {playerName(state, id)}
    </span>
  )
}

/** AC-110: cantidad de puntos en dorado. */
function Pts({ n }: { n: number }) {
  return <span className="status-pts">{n}</span>
}

function statusLine(state: RoomState, lastEvent: GameEvent | null): ReactNode {
  const round = state.round
  switch (state.status) {
    case 'FACE_OFF': {
      const info = faceOffInfo(state) // spec 006 AC-103
      if (info?.attempt === 2) {
        return info.firstAnswerPosition !== null ? (
          <>¡<TeamTag state={state} id={info.secondTeam} /> debe superar la #{info.firstAnswerPosition}!</>
        ) : (
          <>¡<TeamTag state={state} id={info.secondTeam} /> gana el control con cualquier respuesta!</>
        )
      }
      const winner = round?.faceOff?.buzzWinner
      if (winner) return <>¡<PlayerTag state={state} id={winner} /> ganó el buzzer!</>
      const a = round?.faceOff?.playerA
      const b = round?.faceOff?.playerB
      return playerName(state, a) && playerName(state, b) ? (
        <>
          Cara a cara: <PlayerTag state={state} id={a} /> contra <PlayerTag state={state} id={b} />
        </>
      ) : (
        'Cara a cara'
      )
    }
    case 'PLAYING':
      return round?.controllingTeam ? <>Responde <TeamTag state={state} id={round.controllingTeam} /></> : 'Ronda en juego'
    case 'STEAL':
      return round?.controllingTeam ? (
        <>¡Robo! <TeamTag state={state} id={otherTeam(round.controllingTeam)} /> tiene una oportunidad</>
      ) : (
        '¡Robo!'
      )
    case 'ROUND_END':
      if (round?.potWinner) {
        return (
          <>
            Fin de la ronda: <TeamTag state={state} id={round.potWinner} /> se lleva <Pts n={round.pot * round.multiplier} /> puntos
          </>
        )
      }
      if (lastEvent?.type === 'ROUND_ENDED' && lastEvent.team) {
        return lastEvent.points !== undefined ? (
          <>
            Fin de la ronda: <TeamTag state={state} id={lastEvent.team} /> se lleva <Pts n={lastEvent.points} /> puntos
          </>
        ) : (
          <>Fin de la ronda: el bote es para <TeamTag state={state} id={lastEvent.team} /></>
        )
      }
      return 'Fin de la ronda'
    default:
      return ''
  }
}

const TROPHY = (
  <svg viewBox="0 0 64 64" aria-hidden="true" focusable="false">
    <path d="M18 8h28v14a14 14 0 0 1-28 0z" fill="var(--cm-gold)" />
    <path d="M18 12H8v4a10 10 0 0 0 10 10M46 12h10v4a10 10 0 0 1-10 10" fill="none" stroke="var(--cm-gold)" strokeWidth="4" />
    <path d="M28 35h8v9h-8z" fill="var(--cm-gold)" />
    <path d="M20 44h24l3 10H17z" fill="var(--cm-accent)" />
    <path d="M26 14l2 5 5 .5-4 3.5 1.5 5-4.5-3-4.5 3 1.5-5-4-3.5 5-.5z" fill="#fff8e1" opacity="0.9" />
  </svg>
)

/** Spec 007 (AC-114): resultados de fiesta — confeti, trofeo, marcador que cuenta y ganador que brilla. */
function GameOver({ state }: { state: RoomState }) {
  const teams = orderedTeams(state)
  const winner = state.winner === 'A' || state.winner === 'B' ? state.winner : null
  return (
    <div className="game-over" role="status">
      <Confetti />
      <div className="game-over__trophy">{TROPHY}</div>
      <h2 className="game-over__title">
        {winner ? (
          <>
            ¡Gana <span style={{ color: teamColor(winner) }}>{teamName(state, winner)}</span>!
          </>
        ) : (
          winnerText(state)
        )}
      </h2>
      <div className="game-over__scores">
        {teams.map((t) => (
          <FinalScore key={t.id} name={t.name} score={t.score} color={teamColor(t.id)} winner={state.winner === t.id} />
        ))}
      </div>
      <p className="game-over__note">¡Gracias por jugar! El presentador puede iniciar la revancha desde su celular.</p>
    </div>
  )
}

function FinalScore({ name, score, color, winner }: { name: string; score: number; color: string; winner: boolean }) {
  const shown = useCountUp(score)
  return (
    <div className={`game-over__team${winner ? ' game-over__team--winner' : ''}`} style={{ borderColor: color }}>
      {winner && <span className="game-over__crown" aria-hidden="true">★ CAMPEÓN ★</span>}
      <span>{name}</span>
      <strong aria-label={`${name}: ${score} puntos`}>{shown}</strong>
    </div>
  )
}
