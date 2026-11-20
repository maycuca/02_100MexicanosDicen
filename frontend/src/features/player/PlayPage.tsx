import { useEffect, useRef, useState } from 'react'
import { useKeepScreenOn } from '../../shared/device/keepScreenOn'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, api, messageForCode, type Player, type RoomState } from '../../shared/api/client'
import { session } from '../../shared/session/session'
import { useRoomState } from '../../shared/realtime/useRoomState'
import { destinations, type GameEvent } from '../../shared/realtime/messages'
import {
  activeTeamOf,
  faceOffSecondText,
  orderedTeams,
  otherTeam,
  playerName,
  strikesOf,
  teamColor,
  teamName,
  timerOf,
  winnerText,
} from '../../shared/game/display'
import { Countdown } from '../../shared/ui/Countdown'
import { Confetti } from '../../shared/ui/Confetti'
import { VIBRATION, vibrate } from '../../shared/device/vibrate'
import { TeamNamePanel } from './TeamNamePanel'

/** Evita dos vibraciones seguidas por un mismo comando (p. ej. BUZZ_WON + TURN_CHANGED). */
const VIBRATE_GAP_MS = 800

/**
 * Celular del jugador (T502, T503): recupera la sesión con getMe (AC-09), muestra equipo,
 * marcador, pregunta y estado (AC-24) y el buzzer solo al jugador designado en FACE_OFF (AC-13).
 * Spec 002 (T716): banner de turno (AC-38), cuenta regresiva (AC-50) y vibración por evento (AC-54, D16).
 * Spec 003 (T815): en la sala de espera el capitán nombra a su equipo (AC-66).
 * Spec 005: cuenta regresiva detenida (AC-82). Spec 007: sala cerrada → /gracias (AC-113); resultados de fiesta (AC-116).
 */
export function PlayPage() {
  useKeepScreenOn() // spec 010, AC-129
  const { code = '' } = useParams()
  const navigate = useNavigate()
  const [token] = useState(() => session.getPlayerToken(code))
  const [me, setMe] = useState<Player | null>(null)
  const [sessionError, setSessionError] = useState<string | null>(null)
  // Identidad vigente del jugador para decidir si un evento le toca (se actualiza en cada render).
  const selfRef = useRef<Player | null>(null)
  const lastVibration = useRef(0)
  const { state, connected, lastError, clearError, publish, applySnapshot } = useRoomState(code, {
    credentials: token ? { playerToken: token } : null,
    onEvent: (e) => {
      if (e.type === 'ROOM_CLOSED') {
        session.clearRoom(code) // spec 007 AC-113
        navigate('/gracias', { replace: true })
        return
      }
      const pattern = vibrationFor(e, selfRef.current)
      if (!pattern) return
      const now = Date.now()
      if (now - lastVibration.current < VIBRATE_GAP_MS) return
      lastVibration.current = now
      vibrate(pattern)
    },
  })
  useEffect(() => {
    selfRef.current = me && state ? (state.teams.flatMap((t) => t.players).find((p) => p.id === me.id) ?? me) : me
  })
  // Marca local "ya presioné" hasta que llegue un snapshot nuevo o un error del servidor.
  const [pressed, setPressed] = useState<{ version: number; error: unknown } | null>(null)

  useEffect(() => {
    if (!token) {
      navigate(`/join/${code}`, { replace: true })
      return
    }
    let cancelled = false
    api
      .getMe(code, token)
      .then((p) => {
        if (!cancelled) setMe(p)
      })
      .catch((err: unknown) => {
        if (cancelled || !(err instanceof ApiError)) return
        if (err.status === 401 || err.status === 404) {
          session.clearPlayerToken(code)
          setSessionError(err.status === 404 && err.code === 'ROOM_NOT_FOUND' ? err.message : 'Tu sesión ya no es válida.')
        }
      })
    return () => {
      cancelled = true
    }
  }, [code, token, navigate])

  if (sessionError) {
    return (
      <main className="page page--mobile play">
        <h1>Sala {code}</h1>
        <p role="alert" className="alert">{sessionError}</p>
        <Link className="btn btn--big" to={`/join/${code}`}>Volver a entrar</Link>
      </main>
    )
  }

  if (!me || !state) {
    return (
      <main className="page page--mobile play">
        <h1>Sala {code}</h1>
        <p className="muted" role="status">Conectando…</p>
      </main>
    )
  }

  // El snapshot trae el nombre/equipo vigente del jugador.
  const self = state.teams.flatMap((t) => t.players).find((p) => p.id === me.id) ?? me
  const round = state.round ?? null
  const active = activeTeamOf(state)
  const designated =
    state.status === 'FACE_OFF' && (round?.faceOff?.playerA === self.id || round?.faceOff?.playerB === self.id)
  const buzzWinner = round?.faceOff?.buzzWinner ?? null
  const isPressed = pressed !== null && pressed.version === state.version && pressed.error === lastError
  const canBuzz = designated && !buzzWinner && !isPressed

  function buzz() {
    if (!canBuzz || !state) return
    clearError()
    if (publish(destinations.buzz(code))) {
      setPressed({ version: state.version, error: null })
    }
  }

  return (
    <main className="page page--mobile play" style={{ borderTopColor: teamColor(self.team) }}>
      <header className="play__header">
        <div>
          <strong className="play__name">{self.name}</strong>
          <span className="play__team" style={{ background: teamColor(self.team) }}>{teamName(state, self.team)}</span>
        </div>
        <span className={`pill${connected ? ' pill--ok' : ''}`} role="status">{connected ? 'En vivo' : 'Reconectando…'}</span>
      </header>

      <section className="scores" aria-label="Marcador">
        {orderedTeams(state).map((t) => (
          <div
            key={t.id}
            className={`scores__team${active === t.id ? ' scores__team--active' : ''}`}
            style={{ borderColor: teamColor(t.id) }}
          >
            <span>{t.name}</span>
            <strong>{t.score}</strong>
            {round && (
              <span aria-label={`X de ${t.name}: ${strikesOf(state, t.id)}`}>
                {[1, 2, 3].map((n) => (
                  <span key={n} className={`mini-x${strikesOf(state, t.id) >= n ? ' mini-x--on' : ''}`} aria-hidden="true">✕</span>
                ))}
              </span>
            )}
          </div>
        ))}
      </section>

      {active && (
        <div
          className={`turn-banner${active === self.team ? ' turn-banner--mine' : ''}`}
          style={{ borderColor: teamColor(active), ...(active === self.team ? { background: teamColor(active) } : {}) }}
        >
          <p className="turn-banner__text" aria-live="polite">
            {active === self.team ? '¡Le toca a tu equipo!' : `Turno de ${teamName(state, active)}`}
          </p>
          <Countdown {...timerOf(state)} />
        </div>
      )}

      {round && state.status !== 'GAME_OVER' && (
        <section className="card play__round" aria-label="Pregunta actual">
          <p className="play__round-meta">
            Ronda {round.number} de {state.totalRounds}
            {round.multiplier > 1 && ` · ×${round.multiplier}`}
            {/* AC-109: los puntos de la ronda sin la etiqueta "Bote". */}
            <span className="play__pot" aria-label="Puntos de la ronda">{round.pot}</span>
          </p>
          <p className="play__question">{round.question}</p>
        </section>
      )}

      {state.status === 'LOBBY' && token && (
        <TeamNamePanel code={code} playerToken={token} state={state} self={self} onSaved={applySnapshot} />
      )}

      {state.status === 'GAME_OVER' && <PlayerResult state={state} self={self} />}

      {state.status === 'GAME_OVER' ? null : canBuzz ? (
        <button type="button" className="buzzer" onClick={buzz} aria-label="Buzzer: presiona para responder primero">
          ¡YO!
        </button>
      ) : (
        <p className="play__status" aria-live="polite">{statusText(state, self, designated, isPressed)}</p>
      )}

      {lastError && <p role="alert" className="alert">{messageForCode(lastError.code, lastError.message)}</p>}
    </main>
  )
}

/** AC-54: el celular vibra al ganar el buzzer, cuando el turno pasa a su equipo y con una X a su equipo. */
function vibrationFor(e: GameEvent, self: Player | null): readonly number[] | null {
  if (!self) return null
  if (e.type === 'BUZZ_WON' && e.playerId === self.id) return VIBRATION.buzzWon
  if (e.type === 'TURN_CHANGED' && e.team === self.team) return VIBRATION.turn
  if (e.type === 'STRIKE' && e.team === self.team) return VIBRATION.strike
  if (e.type === 'GAME_OVER' && e.team === self.team) return VIBRATION.win // spec 007 AC-116
  return null
}

/** Spec 007 (T1206, AC-116): resultado en el celular; los ganadores festejan con confeti. */
function PlayerResult({ state, self }: { state: RoomState; self: Player }) {
  const won = state.winner === self.team
  const tie = state.winner === 'TIE'
  const title = tie ? '¡Empate!' : won ? '¡Ganaron!' : '¡Buen juego!'
  const note = tie
    ? 'Nadie se rinde: ¡pidan la revancha!'
    : won
      ? `¡${teamName(state, self.team)} es el campeón!`
      : `Ganó ${teamName(state, otherTeam(self.team))}. ¡La próxima es suya!`
  return (
    <section className={`play-result${won ? ' play-result--win' : ''}`} role="status" aria-label="Resultado final">
      {(won || tie) && <Confetti pieces={36} />}
      <p className="play-result__title">{title}</p>
      <p className="play-result__note">{note}</p>
      <div className="play-result__scores">
        {orderedTeams(state).map((t) => (
          <div key={t.id} className="play-result__team" style={{ borderColor: teamColor(t.id) }}>
            <span>{t.name}</span>
            <strong>{t.score}</strong>
          </div>
        ))}
      </div>
    </section>
  )
}

function statusText(state: RoomState, self: Player, designated: boolean, pressed: boolean): string {
  const round = state.round
  const control = round?.controllingTeam ?? null
  switch (state.status) {
    case 'LOBBY':
      return 'Espera a que el presentador inicie la partida…'
    case 'FACE_OFF': {
      const second = faceOffSecondText(state) // spec 006 AC-103
      if (second) return second
      const winner = round?.faceOff?.buzzWinner
      if (winner === self.id) return '¡Ganaste el buzzer! Di tu respuesta en voz alta.'
      if (winner) return `${playerName(state, winner) || 'Alguien'} ganó el buzzer.`
      if (designated && pressed) return 'Enviando…'
      return 'Espera tu turno'
    }
    case 'PLAYING':
      return control === self.team ? '¡Tu equipo responde!' : 'Responde el otro equipo. Prepárense para robar.'
    case 'STEAL':
      return control && otherTeam(control) === self.team
        ? '¡Robo! Tu equipo tiene una sola oportunidad.'
        : 'El otro equipo intenta robar el bote.'
    case 'ROUND_END':
      return 'Fin de la ronda'
    case 'GAME_OVER':
      return winnerText(state)
  }
}
