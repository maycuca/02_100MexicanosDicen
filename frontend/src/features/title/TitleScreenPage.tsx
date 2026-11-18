import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, errorMessage } from '../../shared/api/client'
import { session } from '../../shared/session/session'
import { useMenuSounds } from '../../shared/audio/useMenuSounds'
import { GameLogo } from '../../shared/ui/GameLogo'
import { SettingsModal } from './SettingsModal'
import { TitleBackdrop } from './TitleBackdrop'
import { loadSettings, saveSettings, turnLabel, type GameSettings } from './settings'
import { LocalTestBadge, useLocalTestMode } from '../localtest'

type MenuItem = 'start' | 'settings'
const ITEMS: { id: MenuItem; label: string }[] = [
  { id: 'start', label: 'Empezar' },
  { id: 'settings', label: 'Ajustes' },
]
const UP_KEYS = ['ArrowUp', 'w', 'W']
const DOWN_KEYS = ['ArrowDown', 's', 'S']

/**
 * Pantalla de título en `/` (spec 003): T811 (AC-55, AC-56, AC-60), T812 (AC-57, AC-58), T813 (AC-59).
 * Menú de videojuego EMPEZAR / AJUSTES con ↑/↓ (W/S), Enter/Espacio, mouse y toque.
 * EMPEZAR crea la sala sin nombres de equipo (los nombra el capitán, AC-66) y abre `/screen/:code` (AC-29).
 */
export function TitleScreenPage() {
  const navigate = useNavigate()
  const [selected, setSelected] = useState(0)
  const [settings, setSettings] = useState<GameSettings>(loadSettings)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const sound = useMenuSounds()
  const localTest = useLocalTestMode()
  const { play } = sound
  const buttons = useRef<(HTMLButtonElement | null)[]>([])

  // Ref para decidir sin efectos dentro de los actualizadores de estado (StrictMode los repite).
  const selectedRef = useRef(0)
  const select = useCallback(
    (i: number) => {
      if (selectedRef.current === i) return
      selectedRef.current = i
      setSelected(i)
      play('menuMove')
    },
    [play],
  )

  const start = useCallback(async () => {
    if (busy) return
    setBusy(true)
    setError(null)
    try {
      // AC-57: sin teamAName/teamBName; el servidor usa "Equipo Rojo" y "Equipo Verde".
      const res = await api.createRoom({ ...settings })
      session.setPresenterToken(res.code, res.presenterToken)
      session.setPresenterClaim(res.code, res.presenterClaimToken)
      navigate(`/screen/${res.code}`)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }, [busy, settings, navigate])

  const activate = useCallback(
    (item: MenuItem) => {
      if (busy) return
      play('menuSelect')
      if (item === 'start') void start()
      else setSettingsOpen(true)
    },
    [busy, play, start],
  )

  // Teclado de videojuego en toda la pantalla (no hace falta tener el foco en el menú).
  useEffect(() => {
    if (settingsOpen) return
    function onKey(e: KeyboardEvent) {
      if (e.altKey || e.ctrlKey || e.metaKey) return
      const up = UP_KEYS.includes(e.key)
      const down = DOWN_KEYS.includes(e.key)
      if (up || down) {
        e.preventDefault()
        const next = (selectedRef.current + (up ? -1 : 1) + ITEMS.length) % ITEMS.length
        select(next)
        buttons.current[next]?.focus()
      } else if (e.key === 'Enter' || e.key === ' ') {
        const target = e.target as HTMLElement | null
        const idx = buttons.current.findIndex((b) => b === target)
        // Otros controles (p. ej. "Reintentar") conservan su comportamiento nativo.
        if (idx < 0 && target?.closest('button, a, input, select, textarea')) return
        // Evita el clic nativo del botón enfocado para no activar dos veces.
        e.preventDefault()
        if (e.repeat) return
        activate(ITEMS[idx >= 0 ? idx : selectedRef.current].id)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [settingsOpen, activate, select])

  function changeSettings(next: GameSettings) {
    setSettings(next)
    saveSettings(next)
  }

  function closeSettings() {
    setSettingsOpen(false)
    requestAnimationFrame(() => buttons.current[1]?.focus())
  }

  const summary = `${settings.rounds} rondas · meta ${settings.targetScore} · ${turnLabel(settings.turnSeconds).toLowerCase()}`

  return (
    <main className="title-screen">
      <TitleBackdrop />
      <div className="title-screen__content">
        <GameLogo />
        <nav className="title-menu" aria-label="Menú principal">
          <ul>
            {ITEMS.map((item, i) => {
              const isSelected = selected === i
              const loading = item.id === 'start' && busy
              return (
                <li key={item.id}>
                  <button
                    ref={(el) => {
                      buttons.current[i] = el
                    }}
                    type="button"
                    className={`title-menu__item${isSelected ? ' is-selected' : ''}${loading ? ' is-loading' : ''}`}
                    aria-current={isSelected ? 'true' : undefined}
                    aria-busy={loading || undefined}
                    disabled={busy && !loading}
                    autoFocus={i === 0}
                    onPointerEnter={() => select(i)}
                    onFocus={() => select(i)}
                    onClick={() => {
                      select(i)
                      activate(item.id)
                    }}
                  >
                    <span className="title-menu__cursor" aria-hidden="true">►</span>
                    <span className="title-menu__label">{loading ? 'Preparando…' : item.label}</span>
                  </button>
                </li>
              )
            })}
          </ul>
        </nav>

        {error ? (
          <div className="game-toast" role="alert">
            <div className="game-toast__text">
              <strong className="game-toast__title">¡Ups! No se pudo empezar</strong>
              <p>{error}</p>
            </div>
            <button type="button" className="game-btn" onClick={() => activate('start')}>
              Reintentar
            </button>
          </div>
        ) : (
          <p className="title-screen__press" aria-hidden="true">
            {busy ? 'Armando el escenario…' : 'Presiona EMPEZAR'}
          </p>
        )}
      </div>

      <footer className="title-screen__footer">
        <span className="title-screen__summary">{summary}</span>
        <LocalTestBadge />
        <span className="key-hints" aria-hidden="true">
          <kbd>↑</kbd><kbd>↓</kbd> Elegir · <kbd>Enter</kbd> Aceptar
        </span>
      </footer>

      {settingsOpen && (
        <SettingsModal
          settings={settings}
          onChange={changeSettings}
          onClose={closeSettings}
          sound={sound}
          play={play}
          localTest={localTest}
        />
      )}
    </main>
  )
}
