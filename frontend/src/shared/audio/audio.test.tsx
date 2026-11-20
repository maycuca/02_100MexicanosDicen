import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { audioContext, installFakeAudio, playedSounds } from '../../test/fakeAudio'
import { SOUNDS, playSound, unlockAudio, type SoundName } from './synth'
import { SOUND_FOR_EVENT, useGameSounds, useSoundActivation } from './useGameSounds'
import { getSoundPreference, setSoundPreference } from './preference'
import type { GameEvent } from '../realtime/messages'

const NOW = Date.parse('2026-11-17T20:00:00.000Z')

beforeEach(() => installFakeAudio())
afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
  localStorage.clear()
})

describe('síntesis Web Audio (T713, AC-51)', () => {
  it('AC-51 cada sonido se sintetiza con osciladores, sin archivos ni red', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    expect(await unlockAudio()).toBe(true)
    const names = Object.keys(SOUNDS) as SoundName[]
    expect(names).toEqual(expect.arrayContaining(['buzz', 'reveal', 'strike', 'miss', 'steal', 'roundEnd', 'winner', 'tick']))
    for (const n of names) expect(playSound(n)).toBe(true)
    expect(playedSounds()).toEqual(names)
    expect(audioContext()!.oscillators.every((o) => o.start.mock.calls.length === 1)).toBe(true)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('AC-52 sin interacción (AudioContext bloqueado) no suena', () => {
    expect(playSound('reveal')).toBe(false)
  })

  it('no truena en navegadores sin Web Audio', async () => {
    vi.stubGlobal('AudioContext', undefined)
    expect(await unlockAudio()).toBe(false)
    expect(playSound('buzz')).toBe(false)
  })
})

describe('useGameSounds (D16)', () => {
  const ev = (type: GameEvent['type'], version: number): GameEvent => ({ type, version })

  it('AC-51 cada evento dispara su sonido una sola vez', async () => {
    await unlockAudio()
    const { result } = renderHook(() => useGameSounds({ enabled: true, deadline: null }))
    act(() => {
      result.current.handleEvent(ev('BUZZ_WON', 2))
      result.current.handleEvent(ev('ANSWER_REVEALED', 3))
      result.current.handleEvent(ev('STRIKE', 4))
      result.current.handleEvent(ev('STEAL_STARTED', 4))
      result.current.handleEvent(ev('ROUND_ENDED', 5))
      result.current.handleEvent(ev('GAME_OVER', 6))
      // Repetido (p. ej. al reconectar) y eventos sin sonido.
      result.current.handleEvent(ev('STRIKE', 4))
      result.current.handleEvent(ev('PLAYER_JOINED', 7))
      result.current.handleEvent(ev('TURN_CHANGED', 8))
    })
    expect(playedSounds()).toEqual(['buzz', 'reveal', 'strike', 'steal', 'roundEnd', 'winner'])
    expect(SOUND_FOR_EVENT.STRIKE).toBe('strike')
  })

  it('AC-53 con el sonido desactivado no suena nada', async () => {
    await unlockAudio()
    const { result, rerender } = renderHook((p: { enabled: boolean }) => useGameSounds({ enabled: p.enabled, deadline: null }), {
      initialProps: { enabled: false },
    })
    act(() => result.current.handleEvent(ev('ANSWER_REVEALED', 2)))
    expect(playedSounds()).toEqual([])
    rerender({ enabled: true })
    act(() => result.current.handleEvent(ev('ANSWER_REVEALED', 3)))
    expect(playedSounds()).toEqual(['reveal'])
  })

  it('AC-51 un tic en cada uno de los últimos 5 s del temporizador', async () => {
    await unlockAudio()
    vi.useFakeTimers({ now: NOW })
    const deadline = new Date(NOW + 8_000).toISOString()
    renderHook(() => useGameSounds({ enabled: true, deadline }))
    act(() => vi.advanceTimersByTime(2_500))
    expect(playedSounds()).toEqual([])
    // Cada intervalo de 250 ms es un render (como en el navegador).
    for (let i = 0; i < 40; i++) act(() => vi.advanceTimersByTime(250))
    expect(playedSounds()).toEqual(['tick', 'tick', 'tick', 'tick', 'tick'])
  })
})

describe('preferencia de sonido (AC-52)', () => {
  it('AC-52 activar recuerda la preferencia y desbloquea el audio', async () => {
    const { result } = renderHook(() => useSoundActivation())
    expect(result.current.supported).toBe(true)
    expect(result.current.active).toBe(false)
    await act(() => result.current.activate())
    expect(result.current.active).toBe(true)
    expect(getSoundPreference()).toBe(true)
    act(() => result.current.deactivate())
    expect(getSoundPreference()).toBe(false)
  })

  it('AC-52 tras recargar, la preferencia guardada se reanuda con la primera interacción', async () => {
    setSoundPreference(true)
    const { result } = renderHook(() => useSoundActivation())
    expect(result.current.wanted).toBe(true)
    expect(result.current.active).toBe(false)
    await act(async () => {
      window.dispatchEvent(new Event('pointerdown'))
      await Promise.resolve()
    })
    expect(result.current.active).toBe(true)
  })

  it('spec 010 AC-133 sin preferencia guardada el sonido está activado; "off" lo apaga', () => {
    localStorage.removeItem('cm:sound')
    expect(getSoundPreference()).toBe(true)
    setSoundPreference(false)
    expect(localStorage.getItem('cm:sound')).toBe('off')
    expect(getSoundPreference()).toBe(false)
    setSoundPreference(true)
    expect(getSoundPreference()).toBe(true)
  })

  it('funciona aunque localStorage falle (modo privado)', () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado')
    })
    expect(getSoundPreference()).toBe(true) // spec 010 AC-133: activado por defecto
    expect(() => setSoundPreference(true)).not.toThrow()
    spy.mockRestore()
    vi.restoreAllMocks()
  })
})
