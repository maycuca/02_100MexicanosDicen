import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Countdown } from './Countdown'
import { observeServerDate, resetServerClock } from '../api/serverClock'

const NOW = Date.parse('2026-11-17T20:00:00.000Z')
const iso = (ms: number) => new Date(ms).toISOString()

beforeEach(() => {
  vi.useFakeTimers({ now: NOW })
})
afterEach(() => {
  vi.useRealTimers()
  resetServerClock()
})

describe('Countdown (T712)', () => {
  it('AC-50 muestra los segundos que faltan para turnDeadline y avanza', () => {
    render(<Countdown deadline={iso(NOW + 20_000)} />)
    expect(screen.getByRole('timer')).toHaveTextContent('20')
    act(() => vi.advanceTimersByTime(3_000))
    expect(screen.getByRole('timer')).toHaveTextContent('17')
    expect(screen.getByRole('timer')).not.toHaveClass('countdown--urgent')
  })

  it('AC-50 los últimos 5 s se muestran en rojo y al vencer se queda en 0', () => {
    render(<Countdown deadline={iso(NOW + 6_000)} />)
    expect(screen.getByRole('timer')).not.toHaveClass('countdown--urgent')
    act(() => vi.advanceTimersByTime(1_100))
    expect(screen.getByRole('timer')).toHaveClass('countdown--urgent')
    expect(screen.getByText('Quedan 5 segundos o menos')).toBeInTheDocument()
    act(() => vi.advanceTimersByTime(10_000))
    expect(screen.getByRole('timer')).toHaveTextContent('0')
    expect(screen.getByText('¡Se acabó el tiempo!')).toBeInTheDocument()
  })

  it('AC-50 sin temporizador (turnDeadline null) no muestra nada', () => {
    const { container } = render(<Countdown deadline={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('AC-50 / D17 corrige el desfase del reloj local con el header Date', () => {
    // El reloj local va 10 s adelantado respecto al servidor.
    observeServerDate(new Date(NOW - 10_000).toUTCString(), NOW - 50, NOW)
    render(<Countdown deadline={iso(NOW - 10_000 + 20_000)} />)
    act(() => vi.advanceTimersByTime(0))
    const secs = Number(screen.getByRole('timer').textContent?.match(/\d+/)?.[0])
    expect(secs).toBeGreaterThanOrEqual(19)
    expect(secs).toBeLessThanOrEqual(20)
  })
})
