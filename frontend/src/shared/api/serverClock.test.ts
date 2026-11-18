import { afterEach, describe, expect, it, vi } from 'vitest'
import { observeServerDate, resetServerClock, serverNow, serverOffsetMs } from './serverClock'
import { api } from './client'

afterEach(() => {
  resetServerClock()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('reloj del servidor (D17, AC-50)', () => {
  it('sin desfase real no corrige nada', () => {
    const t = Date.parse('2026-11-17T20:00:00.400Z')
    observeServerDate(new Date(Date.parse('2026-11-17T20:00:00Z')).toUTCString(), t - 50, t)
    expect(serverOffsetMs()).toBe(0)
  })

  it('estima el adelanto del servidor con el header Date (error ≤ 1 s)', () => {
    const local = Date.parse('2026-11-17T20:00:00.000Z')
    // El servidor va 30 s adelantado.
    observeServerDate(new Date(local + 30_000).toUTCString(), local - 100, local)
    expect(serverOffsetMs()).toBeGreaterThanOrEqual(29_000)
    expect(serverOffsetMs()).toBeLessThanOrEqual(31_000)
    // Una segunda muestra afina el intervalo.
    const local2 = local + 5_700
    observeServerDate(new Date(local2 + 30_000).toUTCString(), local2 - 20, local2)
    expect(Math.abs(serverOffsetMs() - 30_000)).toBeLessThan(1_000)
  })

  it('ignora headers ausentes o inválidos', () => {
    observeServerDate(null, 0, 10)
    observeServerDate('no es fecha', 0, 10)
    expect(serverOffsetMs()).toBe(0)
  })

  it('las respuestas REST alimentan el reloj', async () => {
    vi.useFakeTimers({ now: Date.parse('2026-11-17T20:00:00.000Z') })
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ lanAddress: 'x', backendPort: 1, frontendPort: 2 }), {
          headers: { Date: new Date(Date.parse('2026-11-17T19:59:50Z')).toUTCString() },
        }),
      ),
    )
    await api.getNetworkInfo()
    // El servidor va 10 s atrasado.
    expect(serverOffsetMs()).toBeLessThanOrEqual(-9_000)
    expect(serverNow()).toBeLessThanOrEqual(Date.now() - 9_000)
  })
})
