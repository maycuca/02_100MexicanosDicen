import { useState } from 'react'
import type { GameEvent } from '../../shared/realtime/messages'
import { useGameSounds, useSoundActivation } from '../../shared/audio/useGameSounds'
import { Link, useNavigate, useParams } from 'react-router'
import { api, errorMessage } from '../../shared/api/client'
import { QrCard } from '../../shared/ui/QrCard'
import { session } from '../../shared/session/session'
import { useRoomState } from '../../shared/realtime/useRoomState'
import { orderedTeams, teamColor } from '../../shared/game/display'
import { useJoinBaseUrl } from './useJoinBaseUrl'
import { Board } from './Board'
import { GameLogo } from '../../shared/ui/GameLogo'
import { LocalTestBanner, LocalTestLink, useLocalTestMode } from '../localtest'

/**
 * Pantalla de la TV (US-10): inicio con QR de presentador y jugadores (AC-29, AC-31),
 * jugadores en vivo y estado del presentador (AC-08, AC-32, T402), nuevo QR de presentador
 * (AC-33, T405) y, al salir de LOBBY, el tablero en la misma ruta (AC-34, T403).
 * Spec 003 (T814, AC-61): cabecera con el logotipo en pequeño; nombres de equipo en vivo (AC-65).
 * Spec 002 (T713): solo la TV suena; sonidos por evento (AC-51, AC-53). "Activar sonido" solo en el tablero (AC-52).
 * Spec 003 US-18: en LOBBY no se muestran código de sala, IP ni botón de sonido (AC-68…AC-70).
 * Spec 004 (T904): en "Prueba local" las tarjetas muestran URL con el origen del navegador,
 * "Copiar" y "Abrir en pestaña nueva" en vez del QR (AC-75); apagado, nada cambia (AC-78).
 * Spec 005 (T1013): el tablero no tiene botón de sonido ni de QR de presentador (AC-88); "¡Pasa a …!" tras un
 * fallo en el cara a cara (AC-86); con ROOM_CLOSED la TV vuelve a la pantalla de título (AC-91).
 */
export function StartScreenPage() {
  const { code = '' } = useParams()
  const navigate = useNavigate()
  const { baseUrl: lanBaseUrl, error: lanError } = useJoinBaseUrl()
  const localTest = useLocalTestMode().enabled
  // AC-75: en prueba local las URL usan el origen de esta pestaña y no dependen de la IP detectada.
  const baseUrl = localTest ? window.location.origin : lanBaseUrl
  const error = localTest ? null : lanError
  const presenterToken = session.getPresenterToken(code)
  const [claimToken, setClaimToken] = useState(() => session.getPresenterClaim(code))
  // Tras generar un QR nuevo se muestra aunque el presentador anterior siga conectado,
  // hasta que alguien lo canjee (evento PRESENTER_CLAIMED).
  const [awaitingClaim, setAwaitingClaim] = useState(false)
  const [regenerating, setRegenerating] = useState(false)
  const [regenError, setRegenError] = useState<string | null>(null)
  const [strikeEvent, setStrikeEvent] = useState<GameEvent | null>(null)
  const [missEvent, setMissEvent] = useState<GameEvent | null>(null)
  const sound = useSoundActivation()
  const { state, loadError, lastEvent } = useRoomState(code, {
    credentials: presenterToken ? { presenterToken } : null,
    // Se invoca después del render (mensajes STOMP), cuando `handleEvent` ya existe.
    onEvent: (e) => {
      handleEvent(e)
      if (e.type === 'STRIKE') setStrikeEvent(e)
      if (e.type === 'FACE_OFF_MISS') setMissEvent(e)
      if (e.type === 'PRESENTER_CLAIMED') setAwaitingClaim(false)
      if (e.type === 'ROOM_CLOSED') {
        session.clearRoom(code) // spec 007 AC-113
        navigate('/', { replace: true }) // AC-123: la TV vuelve al menú principal (sin "¡Gracias por jugar!")
      }
    },
  })

  const serverSound = state?.soundEnabled ?? true
  const { handleEvent } = useGameSounds({ enabled: sound.active && serverSound, deadline: state?.round?.turnDeadline })

  async function regenerate() {
    if (!presenterToken || regenerating) return
    setRegenerating(true)
    setRegenError(null)
    try {
      const { claimToken: next } = await api.regeneratePresenterClaim(code, presenterToken)
      session.setPresenterClaim(code, next)
      setClaimToken(next)
      setAwaitingClaim(true)
    } catch (err) {
      setRegenError(errorMessage(err))
    } finally {
      setRegenerating(false)
    }
  }

  const presenterConnected = (state?.presenterConnected ?? false) && !awaitingClaim
  const hostUrl = baseUrl && claimToken ? `${baseUrl}/host/${code}?claim=${encodeURIComponent(claimToken)}` : ''
  const joinUrl = baseUrl ? `${baseUrl}/join/${code}` : ''

  const regenButton = presenterToken ? (
    <button type="button" className="btn btn--ghost" onClick={regenerate} disabled={regenerating}>
      {regenerating ? 'Generando…' : 'Nuevo QR de presentador'}
    </button>
  ) : null

  const presenterCard = (
    <QrCard
      title="Presentador"
      subtitle={localTest ? 'Abre esta URL en otra pestaña para controlar la partida' : 'Escanea para controlar la partida'}
      url={hostUrl}
      accent="var(--cm-gold)"
      replacement={
        presenterConnected ? (
          <strong className="qr-card__done">Presentador conectado ✓</strong>
        ) : localTest ? (
          <LocalTestLink label="Presentador" url={hostUrl} />
        ) : undefined
      }
      footer={
        <>
          {regenButton}
          {regenError && <p role="alert" className="alert">{regenError}</p>}
        </>
      }
    />
  )

  if (loadError?.code === 'ROOM_NOT_FOUND' || loadError?.status === 404) {
    return (
      <main className="screen screen--message">
        <GameLogo size="small" />
        <p role="alert" className="alert">{loadError.message}</p>
        <Link className="btn" to="/">Volver al inicio</Link>
      </main>
    )
  }

  if (state && state.status !== 'LOBBY') {
    return (
      <main className="screen screen--board">
        <Board state={state} lastEvent={lastEvent} strikeEvent={strikeEvent} missEvent={missEvent} />
      </main>
    )
  }

  const teams = orderedTeams(state)

  return (
    <main className="screen screen--lobby">
      <button type="button" className="back-btn" aria-label="Regresar" title="Regresar" onClick={() => navigate('/')}>
        <svg viewBox="0 0 24 24" width="28" height="28" aria-hidden="true" focusable="false">
          <path
            d="M9 4 4 9l5 5M4.5 9H14a6 6 0 1 1 0 12h-3"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
      <header className="screen__header">
        <GameLogo size="small" />
        {localTest && <LocalTestBanner />}
      </header>

      <div className="screen__qrs">
        {presenterCard}
        <QrCard
          title="Jugadores"
          subtitle={localTest ? 'Abre esta URL en una pestaña por jugador' : 'Escanea para unirte a un equipo'}
          url={joinUrl}
          accent="var(--cm-accent)"
          replacement={localTest ? <LocalTestLink label="Jugadores" url={joinUrl} /> : undefined}
        />
      </div>

      <section className="screen__teams" aria-live="polite" aria-label="Jugadores por equipo">
        {teams.map((team) => (
          <div key={team.id} className="team-column" style={{ borderColor: teamColor(team.id) }}>
            <h2>{team.name} <span className="muted">({team.players.length})</span></h2>
            {team.players.length === 0 ? (
              <p className="muted">Esperando jugadores…</p>
            ) : (
              <ul aria-label={`Jugadores de ${team.name}`}>
                {team.players.map((p) => <li key={p.id} className={p.connected ? undefined : 'is-away'}>{p.name}</li>)}
              </ul>
            )}
          </div>
        ))}
      </section>

      {(error || (loadError && loadError.status !== 404)) && (
        <footer className="screen__footer muted">
          {error && <span role="alert">{error} </span>}
          {loadError && loadError.status !== 404 && <span role="alert">{loadError.message} </span>}
        </footer>
      )}
    </main>
  )
}
