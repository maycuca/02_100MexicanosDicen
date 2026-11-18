import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import type { MenuSound } from '../../shared/audio/useMenuSounds'
import { DEFAULT_SETTINGS, ROUND_OPTIONS, TARGET_OPTIONS, TURN_OPTIONS, turnLabel, type GameSettings } from './settings'

type Props = {
  settings: GameSettings
  onChange: (next: GameSettings) => void
  onClose: () => void
  sound: { supported: boolean; enabled: boolean; setSound: (on: boolean) => void }
  play: (name: MenuSound) => void
  /** Spec 004 (AC-73): fila opcional "Prueba local"; no forma parte de los ajustes de partida. */
  localTest?: { enabled: boolean; setEnabled: (on: boolean) => void }
}

const PREV_KEYS = ['ArrowLeft', 'a', 'A']
const NEXT_KEYS = ['ArrowRight', 'd', 'D']
const UP_KEYS = ['ArrowUp', 'w', 'W']
const DOWN_KEYS = ['ArrowDown', 's', 'S']

function step<T>(options: readonly T[], value: T, delta: number): T {
  const i = options.indexOf(value)
  const next = Math.min(options.length - 1, Math.max(0, (i < 0 ? 0 : i) + delta))
  return options[next]
}

type RowProps = {
  label: string
  valueText: string
  onStep: (delta: number) => void
  atMin: boolean
  atMax: boolean
  disabled?: boolean
  hint?: ReactNode
  /** spinbutton para listas de valores; switch para Sí/No. */
  kind: { role: 'spinbutton'; now: number; min: number; max: number } | { role: 'switch'; checked: boolean }
}

/** Selector "◄ valor ►" estilo menú de videojuego. ←/→ cambian el valor; también se toca. */
function SelectorRow({ label, valueText, onStep, atMin, atMax, disabled = false, hint, kind }: RowProps) {
  const labelId = useId()
  const hintId = useId()
  const aria =
    kind.role === 'spinbutton'
      ? { 'aria-valuenow': kind.now, 'aria-valuemin': kind.min, 'aria-valuemax': kind.max, 'aria-valuetext': valueText }
      : { 'aria-checked': kind.checked }

  function onKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (disabled) return
    if (PREV_KEYS.includes(e.key)) {
      e.preventDefault()
      if (!atMin) onStep(-1)
    } else if (NEXT_KEYS.includes(e.key)) {
      e.preventDefault()
      if (!atMax) onStep(1)
    } else if (kind.role === 'switch' && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault()
      onStep(kind.checked ? -1 : 1)
    }
  }

  return (
    <div className={`selector${disabled ? ' selector--disabled' : ''}`}>
      <span className="selector__label" id={labelId}>{label}</span>
      <div className="selector__control">
        <button
          type="button"
          className="selector__arrow"
          tabIndex={-1}
          aria-label={`${label}: anterior`}
          disabled={disabled || atMin}
          onClick={() => onStep(-1)}
        >
          ◄
        </button>
        <div
          role={kind.role}
          tabIndex={0}
          data-nav=""
          className="selector__value"
          aria-labelledby={labelId}
          aria-describedby={hint ? hintId : undefined}
          aria-disabled={disabled || undefined}
          onKeyDown={onKeyDown}
          onClick={kind.role === 'switch' && !disabled ? () => onStep(kind.checked ? -1 : 1) : undefined}
          {...aria}
        >
          {valueText}
        </div>
        <button
          type="button"
          className="selector__arrow"
          tabIndex={-1}
          aria-label={`${label}: siguiente`}
          disabled={disabled || atMax}
          onClick={() => onStep(1)}
        >
          ►
        </button>
      </div>
      {hint && <small className="selector__hint" id={hintId}>{hint}</small>}
    </div>
  )
}

/**
 * Modal de AJUSTES (T812, AC-58): `<dialog>` nativo con foco atrapado; Esc o "Volver" lo cierra.
 * ↑/↓ (W/S) mueven entre opciones y ←/→ (A/D) cambian el valor, como en un menú de juego.
 * Cada cambio se guarda al momento (lo persiste quien lo abre).
 */
export function SettingsModal({ settings, onChange, onClose, sound, play, localTest }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (!d.open) {
      if (typeof d.showModal === 'function') d.showModal()
      else d.setAttribute('open', '')
    }
    d.querySelector<HTMLElement>('[data-nav]')?.focus()
    return () => {
      if (d.open && typeof d.close === 'function') d.close()
    }
  }, [])

  const update = (patch: Partial<GameSettings>) => {
    play('menuMove')
    onChange({ ...settings, ...patch })
  }

  function close() {
    play('menuSelect')
    onClose()
  }

  function onKeyDown(e: KeyboardEvent<HTMLDialogElement>) {
    if (e.key === 'Escape') {
      e.preventDefault()
      close()
      return
    }
    const items = Array.from(ref.current?.querySelectorAll<HTMLElement>('[data-nav]') ?? [])
    if (items.length === 0) return
    const i = items.indexOf(document.activeElement as HTMLElement)
    if (e.key === 'Tab') {
      // Foco atrapado también donde el navegador no lo garantiza.
      e.preventDefault()
      const next = e.shiftKey ? (i <= 0 ? items.length - 1 : i - 1) : (i + 1) % items.length
      items[next].focus()
      return
    }
    const up = UP_KEYS.includes(e.key)
    const down = DOWN_KEYS.includes(e.key)
    if (!up && !down) return
    e.preventDefault()
    const next = up ? (i <= 0 ? items.length - 1 : i - 1) : (i + 1) % items.length
    items[next].focus()
    play('menuMove')
  }

  const noTimer = settings.turnSeconds === 0

  return (
    // El teclado del menú se maneja en el diálogo: ↑/↓, Tab y Esc.
    <dialog
      ref={ref}
      className="settings"
      aria-labelledby={titleId}
      aria-modal="true"
      onKeyDown={onKeyDown}
      onCancel={(e) => {
        e.preventDefault()
        close()
      }}
    >
      <div className="settings__panel">
        <h2 className="settings__title" id={titleId}>Ajustes</h2>
        <div className="settings__rows">
          <SelectorRow
            label="Rondas"
            valueText={String(settings.rounds)}
            kind={{ role: 'spinbutton', now: settings.rounds, min: ROUND_OPTIONS[0], max: ROUND_OPTIONS.at(-1)! }}
            atMin={settings.rounds <= ROUND_OPTIONS[0]}
            atMax={settings.rounds >= ROUND_OPTIONS.at(-1)!}
            onStep={(d) => update({ rounds: step(ROUND_OPTIONS, settings.rounds, d) })}
          />
          <SelectorRow
            label="Meta de puntos"
            valueText={`${settings.targetScore} pts`}
            kind={{ role: 'spinbutton', now: settings.targetScore, min: TARGET_OPTIONS[0], max: TARGET_OPTIONS.at(-1)! }}
            atMin={settings.targetScore <= TARGET_OPTIONS[0]}
            atMax={settings.targetScore >= TARGET_OPTIONS.at(-1)!}
            onStep={(d) => update({ targetScore: step(TARGET_OPTIONS, settings.targetScore, d) })}
          />
          <SelectorRow
            label="Temporizador"
            valueText={turnLabel(settings.turnSeconds)}
            kind={{ role: 'spinbutton', now: settings.turnSeconds, min: 0, max: TURN_OPTIONS.at(-1)! }}
            atMin={settings.turnSeconds <= 0}
            atMax={settings.turnSeconds >= TURN_OPTIONS.at(-1)!}
            onStep={(d) => update({ turnSeconds: step(TURN_OPTIONS, settings.turnSeconds, d) })}
            hint="El presentador la inicia, la para y la restablece"
          />
          <SelectorRow
            label="X automática"
            valueText={settings.autoStrikeOnTimeout ? 'Sí' : 'No'}
            kind={{ role: 'switch', checked: settings.autoStrikeOnTimeout }}
            atMin={!settings.autoStrikeOnTimeout}
            atMax={settings.autoStrikeOnTimeout}
            disabled={noTimer}
            onStep={(d) => update({ autoStrikeOnTimeout: d > 0 })}
            hint={noTimer ? 'Necesita temporizador' : 'Marca una X al acabarse el tiempo'}
          />
          {sound.supported && (
            <SelectorRow
              label="Sonido"
              valueText={sound.enabled ? 'Sí' : 'No'}
              kind={{ role: 'switch', checked: sound.enabled }}
              atMin={!sound.enabled}
              atMax={sound.enabled}
              onStep={(d) => sound.setSound(d > 0)}
              hint="Se recuerda en este navegador"
            />
          )}
          {localTest && (
            <SelectorRow
              label="Prueba local"
              valueText={localTest.enabled ? 'Sí' : 'No'}
              kind={{ role: 'switch', checked: localTest.enabled }}
              atMin={!localTest.enabled}
              atMax={localTest.enabled}
              onStep={(d) => {
                play('menuMove')
                localTest.setEnabled(d > 0)
              }}
              hint="Sin celulares: URL para pegar en pestañas de este navegador"
            />
          )}
        </div>
        <div className="settings__actions">
          <button
            type="button"
            className="game-btn game-btn--ghost"
            data-nav=""
            onClick={() => update({ ...DEFAULT_SETTINGS })}
          >
            Restablecer
          </button>
          <button type="button" className="game-btn" data-nav="" onClick={close}>
            Volver
          </button>
        </div>
        <p className="key-hints" aria-hidden="true">
          <kbd>↑</kbd><kbd>↓</kbd> Elegir · <kbd>◄</kbd><kbd>►</kbd> Cambiar · <kbd>Esc</kbd> Volver
        </p>
      </div>
    </dialog>
  )
}
