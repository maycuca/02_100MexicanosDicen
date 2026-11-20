import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const enable = vi.fn<() => Promise<void>>()
const disable = vi.fn<() => void>()

vi.mock('nosleep.js', () => ({
  default: class {
    enable = enable
    disable = disable
  },
}))

const { useKeepScreenOn } = await import('./keepScreenOn')

function setVisibility(state: DocumentVisibilityState) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
}

describe('useKeepScreenOn (spec 010)', () => {
  beforeEach(() => {
    enable.mockReset().mockResolvedValue(undefined)
    disable.mockReset()
    setVisibility('visible')
  })
  afterEach(() => setVisibility('visible'))

  it('AC-129 · AC-130 lo pide al montar y otra vez con cada toque, clic o tecla', () => {
    renderHook(() => useKeepScreenOn())
    expect(enable).toHaveBeenCalledTimes(1)
    act(() => {
      window.dispatchEvent(new Event('pointerdown'))
      window.dispatchEvent(new Event('touchend'))
      window.dispatchEvent(new Event('keydown'))
    })
    expect(enable).toHaveBeenCalledTimes(4)
  })

  it('AC-131 al volver a la pestaña lo pide de nuevo; oculta, no', () => {
    renderHook(() => useKeepScreenOn())
    enable.mockClear()
    setVisibility('hidden')
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'))
      window.dispatchEvent(new Event('pointerdown'))
    })
    expect(enable).not.toHaveBeenCalled()
    setVisibility('visible')
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    expect(enable).toHaveBeenCalledTimes(1)
  })

  it('AC-132 al salir de la página lo libera y deja de escuchar gestos', () => {
    const { unmount } = renderHook(() => useKeepScreenOn())
    unmount()
    expect(disable).toHaveBeenCalledTimes(1)
    enable.mockClear()
    act(() => window.dispatchEvent(new Event('pointerdown')))
    expect(enable).not.toHaveBeenCalled()
  })

  it('AC-132 si el navegador lo rechaza o no lo soporta, no pasa nada', async () => {
    enable.mockRejectedValue(new Error('NotAllowedError'))
    expect(() => renderHook(() => useKeepScreenOn())).not.toThrow()
    enable.mockImplementation(() => {
      throw new Error('sin soporte')
    })
    expect(() => act(() => window.dispatchEvent(new Event('pointerdown')))).not.toThrow()
    await Promise.resolve()
  })

  it('con active = false no hace nada', () => {
    renderHook(() => useKeepScreenOn(false))
    act(() => window.dispatchEvent(new Event('pointerdown')))
    expect(enable).not.toHaveBeenCalled()
  })
})
