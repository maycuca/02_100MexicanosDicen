import { useId, useState, type FormEvent } from 'react'
import { ApiError, api, errorMessage, messageForCode, type Player, type RoomState } from '../../shared/api/client'
import { findTeam, otherTeam, playerName, teamColor } from '../../shared/game/display'

type Props = {
  code: string
  playerToken: string
  state: RoomState
  self: Player
  /** Aplica el snapshot que devuelve el servidor (los demás lo reciben por STOMP, AC-65). */
  onSaved: (state: RoomState) => void
}

const MIN = 2
const MAX = 20

/**
 * Sala de espera del celular (T815, AC-66): el capitán ve "Eres el capitán de <equipo>" y puede
 * nombrar a su equipo; los demás ven el nombre actual, que se actualiza en vivo con el snapshot.
 * Las reglas finales (AC-63, AC-64) las aplica el servidor.
 */
export function TeamNamePanel({ code, playerToken, state, self, onSaved }: Props) {
  const inputId = useId()
  const helpId = useId()
  const team = findTeam(state, self.team)
  const other = findTeam(state, otherTeam(self.team))
  // null = el campo sigue el nombre vigente; texto = lo que el capitán está escribiendo.
  const [draft, setDraft] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  if (!team) return null
  const color = teamColor(team.id)

  if (team.captainId !== self.id) {
    const captain = playerName(state, team.captainId)
    return (
      <section className="card captain" style={{ borderColor: color }} aria-label="Tu equipo">
        <p className="captain__label">Tu equipo</p>
        <p className="captain__team" aria-live="polite">{team.name}</p>
        {captain && <p className="muted captain__note">{captain} es el capitán y elige el nombre del equipo.</p>}
      </section>
    )
  }

  const value = draft ?? team.name
  const trimmed = value.trim()
  const clash = other !== undefined && trimmed.toLocaleLowerCase('es') === other.name.trim().toLocaleLowerCase('es')
  const lengthOk = trimmed.length >= MIN && trimmed.length <= MAX
  const localError = clash ? messageForCode('TEAM_NAME_TAKEN') : null
  const shownError = fieldError ?? localError
  // Spec 007 (AC-111): también se puede guardar el nombre sugerido sin cambiarlo.
  const canSave = lengthOk && !clash && !busy

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!canSave || !team) return
    setBusy(true)
    setError(null)
    setFieldError(null)
    setSaved(false)
    try {
      const next = await api.renameTeam(code, playerToken, team.id, trimmed)
      onSaved(next)
      setDraft(null)
      setSaved(true)
    } catch (err) {
      if (err instanceof ApiError && (err.code === 'TEAM_NAME_TAKEN' || err.code === 'VALIDATION_ERROR')) {
        setFieldError(err.code === 'VALIDATION_ERROR' ? `El nombre debe tener entre ${MIN} y ${MAX} caracteres.` : err.message)
      } else {
        setError(errorMessage(err))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card captain captain--mine" style={{ borderColor: color }} aria-label="Nombre del equipo">
      <p className="captain__badge" style={{ background: color }}>
        <span aria-hidden="true">★ </span>Eres el capitán de {team.name}
      </p>
      <form className="captain__form" onSubmit={onSubmit} noValidate>
        <label htmlFor={inputId}>Ponle nombre a tu equipo</label>
        <input
          id={inputId}
          className="input"
          value={value}
          maxLength={MAX}
          autoComplete="off"
          enterKeyHint="done"
          aria-invalid={shownError ? true : undefined}
          aria-describedby={helpId}
          onChange={(e) => {
            setDraft(e.target.value)
            setFieldError(null)
            setSaved(false)
          }}
        />
        <small id={helpId} className={shownError ? 'field-error' : 'muted'}>
          {shownError ?? `Entre ${MIN} y ${MAX} caracteres`}
        </small>
        <button className="btn btn--big" type="submit" disabled={!canSave}>
          {busy ? 'Guardando…' : 'Guardar nombre'}
        </button>
      </form>
      {saved && <p className="pill pill--ok captain__saved" role="status">¡Nombre guardado!</p>}
      {error && <p role="alert" className="alert">{error}</p>}
    </section>
  )
}
