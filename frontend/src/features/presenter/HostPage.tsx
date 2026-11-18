import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router'
import { ApiError, api, errorMessage, type PresenterRoomState, type TimerAction } from '../../shared/api/client'
import { session } from '../../shared/session/session'
import { useRoomState, type RoomStateHook } from '../../shared/realtime/useRoomState'
import {
  STATUS_LABEL,
  activeTeamOf,
  faceOffInfo,
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
import { redeemClaim } from './claim'

const ANSWERING: PresenterRoomState['status'][] = ['FACE_OFF', 'PLAYING', 'STEAL']

/**
 * Control del presentador desde el celular (T404).
 * Canjea `?claim=` (AC-30), guarda el presenterToken y limpia la URL; luego muestra
 * la vista de presentador (con respuestas ocultas) y los controles según el estado.
 * Spec 005 (T1011): Iniciar/Reanudar, Parar y Restablecer del temporizador (AC-80); en el cara a cara el fallo
 * pasa el turno sin X (AC-86); "Menú principal" con advertencia cierra la sala (AC-90, AC-91).
 * Spec 006 (T1111, AC-102): el cara a cara se resuelve solo; sin "Asignar control", con pista del segundo intento.
 */
export function HostPage() {
  const { code = '' } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const claim = params.get('claim')
  const [token, setToken] = useState<string | null>(() => (claim ? null : session.getPresenterToken(code)))
  const [claimError, setClaimError] = useState<string | null>(null)
  const room = useRoomState(code, { view: 'presenter', presenterToken: token })
  const { applySnapshot } = room

  useEffect(() => {
    if (!claim) return
    let cancelled = false
    redeemClaim(code, claim)
      .then((res) => {
        if (cancelled) return
        session.setPresenterToken(code, res.presenterToken)
        applySnapshot(res.state)
        setToken(res.presenterToken)
        navigate(`/host/${code}`, { replace: true })
      })
      .catch((err: unknown) => {
        if (cancelled) return
        const stored = session.getPresenterToken(code)
        // Si este celular ya había canjeado el QR (p. ej. recargó con el claim en la URL), sigue con su token.
        if (err instanceof ApiError && err.code === 'CLAIM_USED' && stored) {
          setToken(stored)
          navigate(`/host/${code}`, { replace: true })
          return
        }
        setClaimError(errorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [claim, code, navigate, applySnapshot])

  const authFailed = room.loadError && (room.loadError.status === 401 || room.loadError.status === 403)
  useEffect(() => {
    if (authFailed) session.clearPresenterToken(code)
  }, [authFailed, code])

  if (claim && !claimError) {
    return (
      <main className="page page--mobile host">
        <h1>Presentador · Sala {code}</h1>
        <p className="muted" role="status">Conectando como presentador…</p>
      </main>
    )
  }

  if (claimError || !token || authFailed || room.loadError?.status === 404) {
    const message =
      claimError ??
      (authFailed
        ? 'Tu sesión de presentador ya no es válida. Escanea el QR de presentador de la pantalla.'
        : room.loadError?.status === 404
          ? room.loadError.message
          : 'Escanea el QR de presentador que aparece en la pantalla.')
    return (
      <main className="page page--mobile host">
        <h1>Presentador · Sala {code}</h1>
        <p role={claimError || authFailed ? 'alert' : undefined} className={claimError || authFailed ? 'alert' : 'muted'}>
          {message}
        </p>
      </main>
    )
  }

  return <ControlPanel code={code} token={token} room={room} />
}

function ControlPanel({ code, token, room }: { code: string; token: string; room: RoomStateHook<PresenterRoomState> }) {
  const { state, connected, applySnapshot } = room
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [confirmMenu, setConfirmMenu] = useState(false)
  const [closing, setClosing] = useState(false)
  const [closeError, setCloseError] = useState<string | null>(null)

  /** AC-91: cierra la sala y regresa al menú principal; si ya no existe, regresa igual. */
  async function goToMainMenu() {
    if (closing) return
    setClosing(true)
    setCloseError(null)
    try {
      await api.closeRoom(code, token)
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 404)) {
        setCloseError(errorMessage(err))
        setClosing(false)
        return
      }
    }
    session.clearRoom(code) // spec 007 AC-113: cierra todas las sesiones de la sala
    navigate('/gracias', { replace: true })
  }

  const menuButton = (
    <button type="button" className="btn btn--ghost btn--small host__menu" onClick={() => setConfirmMenu(true)}>
      Menú principal
    </button>
  )

  const menuDialog = confirmMenu && (
    <ConfirmDialog
      title="¿Regresar al menú principal?"
      description="Se cerrará la sala y el juego se reiniciará; los jugadores tendrán que volver a unirse."
      confirmLabel={closing ? 'Cerrando…' : 'Sí, regresar'}
      busy={closing}
      onConfirm={() => void goToMainMenu()}
      onClose={() => {
        setConfirmMenu(false)
        setCloseError(null)
      }}
    >
      {closeError && <p role="alert" className="alert">{closeError}</p>}
    </ConfirmDialog>
  )

  async function run(action: () => Promise<PresenterRoomState>) {
    if (busy) return
    setBusy(true)
    setActionError(null)
    try {
      applySnapshot(await action())
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!state) {
    return (
      <main className="page page--mobile host">
        <h1>Presentador · Sala {code}</h1>
        <p className="muted" role="status">Cargando la partida…</p>
        {menuButton}
        {menuDialog}
      </main>
    )
  }

  const round = state.round ?? null
  const teams = orderedTeams(state)
  const answering = ANSWERING.includes(state.status)
  const canStart = state.status === 'LOBBY' && teams.every((t) => t.players.length > 0)
  const answers = [...(state.presenterBoard ?? [])].sort((a, b) => a.position - b.position)
  const isLastRound = round !== null && round.number >= state.totalRounds
  const active = activeTeamOf(state)
  const activeName = teamName(state, active)
  const timer = timerOf(state)
  const hasTimer = state.turnSeconds > 0 && answering && round !== null
  const timerRunning = round?.timerRunning ?? false
  const timerFull = (round?.timerRemainingMs ?? 0) >= state.turnSeconds * 1000
  const faceOff = state.status === 'FACE_OFF'
  const faceOffNow = faceOffInfo(state)
  const secondAttempt = faceOffNow?.attempt === 2
  // Spec 008 (AC-121): solo en el cara a cara y antes de revelar alguna respuesta.
  const canChangeQuestion = faceOff && answers.every((a) => !a.revealed)
  const timerAction = (action: TimerAction) => run(() => api.controlTimer(code, token, action))

  return (
    <main className="page page--mobile host">
      <header className="host__header">
        <h1>Sala {code}</h1>
        <span className={`pill${connected ? ' pill--ok' : ''}`} role="status">
          {connected ? 'En vivo' : 'Reconectando…'}
        </span>
        {menuButton}
      </header>
      {/* AC-107: switch nativo (atributo `switch`) con role="switch" y estilo propio. */}
      <label className="switch host__sound">
        <input
          type="checkbox"
          // @ts-expect-error -- atributo HTML `switch` (Safari 17.4+); aún no está en los tipos de React.
          switch=""
          role="switch"
          checked={state.soundEnabled}
          disabled={busy}
          onChange={(e) => run(() => api.setSound(code, token, e.currentTarget.checked))}
        />
        <span>Sonido de la TV</span>
      </label>
      <p className="host__status" aria-live="polite">
        <strong>{STATUS_LABEL[state.status]}</strong>
        {round && ` · Ronda ${round.number} de ${state.totalRounds}`}
      </p>

      <section className="scores" aria-label="Marcador">
        {teams.map((t) => (
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

      {round && answering && (
        <div className="turn-banner" style={active ? { borderColor: teamColor(active) } : undefined}>
          <p aria-live="polite" className="turn-banner__text">
            {active ? <>En turno: <strong style={{ color: teamColor(active) }}>{activeName}</strong></> : 'Ningún equipo en turno'}
          </p>
          <Countdown deadline={timer.deadline} pausedMs={timer.pausedMs} />
          {hasTimer && (
            <div className="timer-controls" role="group" aria-label="Temporizador">
              <IconButton
                label={timerFull ? 'Iniciar' : 'Reanudar'}
                icon="play"
                primary
                disabled={busy || timerRunning}
                onClick={() => timerAction('START')}
              />
              <IconButton label="Parar" icon="pause" disabled={busy || !timerRunning} onClick={() => timerAction('PAUSE')} />
              <IconButton
                label="Restablecer"
                icon="reset"
                disabled={busy || (!timerRunning && timerFull)}
                onClick={() => timerAction('RESET')}
              />
            </div>
          )}
        </div>
      )}

      {actionError && <p role="alert" className="alert">{actionError}</p>}

      {state.status === 'LOBBY' && (
        <section className="card stack">
          {teams.map((t) => (
            <p key={t.id}>
              <strong style={{ color: teamColor(t.id) }}>{t.name}:</strong>{' '}
              {t.players.length === 0 ? <span className="muted">sin jugadores</span> : t.players.map((p) => p.name).join(', ')}
            </p>
          ))}
          {!canStart && <p className="muted">Cada equipo necesita al menos un jugador.</p>}
          <button type="button" className="btn btn--big" disabled={!canStart || busy} onClick={() => run(() => api.startGame(code, token))}>
            Iniciar partida
          </button>
        </section>
      )}

      {round && state.status !== 'GAME_OVER' && (
        <section className="card stack round-card" aria-label="Ronda actual">
          {/* Spec 008 (AC-119): pregunta destacada; (AC-121) "Cambiar pregunta" en la misma sección. */}
          <div className="round-card__question">
            <p className="round-card__eyebrow">Pregunta · Ronda {round.number} de {state.totalRounds}</p>
            <p className="host__question">{round.question}</p>
            <button
              type="button"
              className="btn btn--ghost btn--small round-card__change"
              disabled={busy || !canChangeQuestion}
              aria-describedby={canChangeQuestion ? undefined : 'change-question-help'}
              onClick={() => run(() => api.changeQuestion(code, token))}
            >
              ↻ Cambiar pregunta
            </button>
            {!canChangeQuestion && (
              <small id="change-question-help" className="muted">
                Solo se puede cambiar en el cara a cara, antes de revelar respuestas.
              </small>
            )}
          </div>
          {/* AC-120: indicadores como fichas (etiqueta pequeña, valor grande). */}
          <dl className="host__stats">
            <div className="stat" data-stat="revelados">
              <dt>Revelados</dt>
              <dd>{round.pot}</dd>
            </div>
            <div className="stat">
              <dt>Multiplicador</dt>
              <dd>×{round.multiplier}</dd>
            </div>
            <div className="stat">
              <dt>Control</dt>
              <dd style={round.controllingTeam ? { color: teamColor(round.controllingTeam) } : undefined}>
                {round.controllingTeam ? teamName(state, round.controllingTeam) : '—'}
              </dd>
            </div>
          </dl>
          {faceOffNow && secondAttempt && (
            <p className="host__hint" role="status">
              {faceOffNow.firstAnswerPosition !== null
                ? `${teamName(state, faceOffNow.firstTeam)} abrió la #${faceOffNow.firstAnswerPosition}: ${teamName(state, faceOffNow.secondTeam)} necesita una más alta`
                : `${teamName(state, faceOffNow.firstTeam)} falló: cualquier respuesta de ${teamName(state, faceOffNow.secondTeam)} le da el control`}
            </p>
          )}
          {state.status === 'FACE_OFF' && (
            <p>
              Cara a cara: <strong>{playerName(state, round.faceOff?.playerA) || '—'}</strong> contra{' '}
              <strong>{playerName(state, round.faceOff?.playerB) || '—'}</strong>
              <br />
              {round.faceOff?.buzzWinner
                ? <>Ganó el buzzer: <strong>{playerName(state, round.faceOff.buzzWinner)}</strong></>
                : <span className="muted">Esperando el buzzer…</span>}
            </p>
          )}
          {state.status === 'STEAL' && round.controllingTeam && (
            <p>
              Roba: <strong style={{ color: teamColor(otherTeam(round.controllingTeam)) }}>{teamName(state, otherTeam(round.controllingTeam))}</strong>
            </p>
          )}
        </section>
      )}

      {round && answers.length > 0 && state.status !== 'GAME_OVER' && (
        <section aria-label="Respuestas">
          <h2 className="host__h2">Respuestas</h2>
          <ol className="answers">
            {answers.map((a) => (
              <li key={a.answerId}>
                <button
                  type="button"
                  className={`answer${a.revealed ? ' answer--revealed' : ''}`}
                  disabled={a.revealed || !answering || busy || (faceOff && !active)}
                  onClick={() => run(() => api.revealAnswer(code, token, a.answerId))}
                  aria-label={a.revealed ? `${a.text}, ${a.points} puntos, revelada` : `Revelar ${a.text}, ${a.points} puntos`}
                >
                  <span className="answer__pos">{a.position}</span>
                  <span className="answer__text">{a.text}</span>
                  <span className="answer__pts">{a.points}</span>
                  <span className="answer__state" aria-hidden="true">{a.revealed ? '✓' : 'Revelar'}</span>
                </button>
              </li>
            ))}
          </ol>
        </section>
      )}

      {answering && (
        <section className="host__actions">
          <button
            type="button"
            className={`btn btn--big ${faceOff ? 'btn--miss' : 'btn--danger'}`}
            disabled={busy || !active}
            aria-describedby={active ? undefined : 'strike-help'}
            onClick={() => run(() => api.markStrike(code, token))}
          >
            {faceOff
              ? active
                ? `Falló ${activeName} · ${secondAttempt ? 'termina el cara a cara' : 'pasa el turno'}`
                : 'Falló (sin equipo en turno)'
              : active
                ? `✕ X para ${activeName}`
                : '✕ X (sin equipo en turno)'}
          </button>
          {!active && (
            <p id="strike-help" className="muted">
              {faceOff ? 'Espera a que alguien gane el buzzer.' : 'Espera a que alguien gane el buzzer para marcar una X.'}
            </p>
          )}
          {faceOff && active && faceOffNow && (
            <p className="muted">
              {secondAttempt
                ? `Si falla, el control es de ${teamName(state, faceOffNow.firstTeam)}.`
                : 'La #1 gana el control; otra respuesta o un fallo pasan el turno.'}
            </p>
          )}
        </section>
      )}

      {state.status === 'ROUND_END' && (
        <button type="button" className="btn btn--big" disabled={busy} onClick={() => run(() => api.nextRound(code, token))}>
          {isLastRound ? 'Ver resultado final' : 'Siguiente ronda'}
        </button>
      )}

      {state.status === 'GAME_OVER' && (
        <section className="card stack" role="status">
          <p className="host__winner">{winnerText(state)}</p>
          <button type="button" className="btn btn--big" disabled={busy} onClick={() => run(() => api.rematch(code, token))}>
            Revancha
          </button>
        </section>
      )}
      {menuDialog}
    </main>
  )
}

type ConfirmProps = {
  title: string
  description: string
  confirmLabel: string
  busy: boolean
  onConfirm: () => void
  onClose: () => void
  children?: ReactNode
}

/**
 * AC-90: confirmación con `<dialog>` nativo abierto con `showModal()`: el navegador da la semántica modal,
 * el foco atrapado, Esc/gesto atrás y regresa el foco al botón que lo abrió. El foco inicial va a "Cancelar"
 * (la acción segura).
 */
function ConfirmDialog({ title, description, confirmLabel, busy, onConfirm, onClose, children }: ConfirmProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const safe = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (!d.open) {
      if (typeof d.showModal === 'function') d.showModal()
      else d.setAttribute('open', '') // jsdom (pruebas) no implementa showModal
    }
    // El autoFocus de React corre antes de abrir el diálogo; se enfoca la acción segura ya abierto.
    safe.current?.focus()
    return () => {
      if (d.open && typeof d.close === 'function') d.close()
    }
  }, [])

  // El estado de React decide si el diálogo existe: Esc (evento cancel) y "Cancelar" piden cerrarlo y al
  // desmontarse se cierra. No se escucha `close`: el doble efecto de StrictMode lo dispara al montar.
  return (
    <dialog
      ref={ref}
      className="confirm card stack"
      aria-labelledby="confirm-title"
      aria-describedby="confirm-desc"
      onCancel={(e) => {
        // Esc / gesto atrás: el navegador cierra el diálogo y el estado lo desmonta (mientras se cierra la sala, no).
        if (busy) e.preventDefault()
        else onClose()
      }}
    >
      <h2 id="confirm-title">{title}</h2>
      <p id="confirm-desc">{description}</p>
      {children}
      <div className="confirm__actions">
        <button ref={safe} type="button" className="btn btn--ghost" disabled={busy} onClick={onClose}>
          Cancelar
        </button>
        <button type="button" className="btn btn--danger" disabled={busy} onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}

const ICON_PATHS = {
  play: <path d="M8 5.5v13l11-6.5z" fill="currentColor" />,
  pause: (
    <>
      <rect x="6.5" y="5" width="4" height="14" rx="1" fill="currentColor" />
      <rect x="13.5" y="5" width="4" height="14" rx="1" fill="currentColor" />
    </>
  ),
  reset: (
    <path
      d="M5 12a7 7 0 1 0 2.05-4.95M5 4.5V9h4.5"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  ),
} as const

/** AC-106: botón solo con icono (SVG decorativo); el nombre accesible sale de `aria-label`. */
function IconButton(props: {
  label: string
  icon: keyof typeof ICON_PATHS
  disabled: boolean
  onClick: () => void
  primary?: boolean
}) {
  const { label, icon, disabled, onClick, primary = false } = props
  return (
    <button
      type="button"
      className={`icon-btn${primary ? ' icon-btn--primary' : ''}`}
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
    >
      <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" focusable="false">
        {ICON_PATHS[icon]}
      </svg>
    </button>
  )
}
