import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError, api, errorMessage, messageForCode, type RoomState, type TeamId } from '../../shared/api/client'
import { session } from '../../shared/session/session'
import { orderedTeams, playerName, teamColor } from '../../shared/game/display'
import { GameLogo } from '../../shared/ui/GameLogo'

/**
 * Unirse a la sala (T501, AC-05…AC-07). Si ya hay sesión válida, va directo a jugar (AC-09).
 * Spec 003 (T815, AC-66): equipos con su nombre actual y su color, y quién es su capitán.
 */
export function JoinPage() {
  const { code = '' } = useParams()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [team, setTeam] = useState<TeamId | null>(null)
  const [room, setRoom] = useState<RoomState | null>(null)
  const [roomError, setRoomError] = useState<string | null>(null)
  const [nameError, setNameError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    const token = session.getPlayerToken(code)
    if (token) {
      api
        .getMe(code, token)
        .then(() => {
          if (!cancelled) navigate(`/play/${code}`, { replace: true })
        })
        .catch((err: unknown) => {
          if (err instanceof ApiError && (err.status === 401 || err.status === 404)) session.clearPlayerToken(code)
        })
    }
    api
      .getRoom(code)
      .then((r) => {
        if (!cancelled) setRoom(r)
      })
      .catch((err: unknown) => {
        if (!cancelled && err instanceof ApiError && err.status === 404) setRoomError(messageForCode('ROOM_NOT_FOUND'))
      })
    return () => {
      cancelled = true
    }
  }, [code, navigate])

  const trimmed = name.trim()
  const closed = room !== null && room.status !== 'LOBBY'
  const valid = trimmed.length >= 2 && trimmed.length <= 20 && team !== null && !roomError && !closed

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!valid || busy || !team) return
    setBusy(true)
    setError(null)
    setNameError(null)
    try {
      const res = await api.joinRoom(code, { name: trimmed, team })
      session.setPlayerToken(code, res.playerToken)
      navigate(`/play/${code}`)
    } catch (err) {
      if (err instanceof ApiError && err.code === 'NAME_TAKEN') setNameError(err.message)
      else setError(errorMessage(err))
      setBusy(false)
    }
  }

  const teams = orderedTeams(room)

  return (
    <main className="page page--mobile">
      <GameLogo as="p" size="mini" />
      <h1>Sala {code}</h1>
      {roomError && <p role="alert" className="alert">{roomError}</p>}
      {closed && <p role="alert" className="alert">{messageForCode('ROOM_NOT_IN_LOBBY')}</p>}
      <form className="card form" onSubmit={onSubmit} noValidate>
        <label>
          Tu nombre
          <input
            className="input"
            value={name}
            onChange={(e) => {
              setName(e.target.value)
              setNameError(null)
            }}
            maxLength={20}
            autoComplete="nickname"
            enterKeyHint="go"
            aria-invalid={nameError ? true : undefined}
            aria-describedby="name-help"
          />
          <small id="name-help" className={nameError ? 'field-error' : 'muted'}>
            {nameError ?? 'Entre 2 y 20 caracteres'}
          </small>
        </label>
        <fieldset className="team-picker">
          <legend>Elige equipo</legend>
          {teams.map((t) => {
            const captain = room ? playerName(room, t.captainId) : ''
            return (
              <div key={t.id} className="team-picker__option">
                <button
                  type="button"
                  className="btn team-picker__btn"
                  aria-pressed={team === t.id}
                  aria-describedby={`team-${t.id}-info`}
                  onClick={() => setTeam(t.id)}
                  style={{ background: teamColor(t.id) }}
                >
                  {room ? t.name : `Equipo ${t.id}`}
                </button>
                <small id={`team-${t.id}-info`} className="muted team-picker__info">
                  {!room ? '' : captain ? `Capitán: ${captain}` : 'El primero en unirse será el capitán y le pondrá nombre'}
                </small>
              </div>
            )
          })}
        </fieldset>
        {error && <p role="alert" className="alert">{error}</p>}
        <button className="btn btn--big" type="submit" disabled={!valid || busy}>
          {busy ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </main>
  )
}
