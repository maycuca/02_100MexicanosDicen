import { render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from './routes'
import { session } from '../shared/session/session'

function renderAt(path: string) {
  return render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />)
}

describe('rutas del esqueleto (T009)', () => {
  it('AC-55 / AC-61 muestra la pantalla de título "Lo que 100 Mexicanos dicen" en /', () => {
    renderAt('/')
    expect(screen.getByRole('heading', { name: 'Lo que 100 Mexicanos dicen' })).toBeInTheDocument()
  })

  it('deshabilita "Entrar" hasta tener nombre y equipo (AC-05)', () => {
    renderAt('/join/KDMZ')
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled()
  })

  it('la página de presentador indica que se está conectando con el claim', () => {
    renderAt('/host/KDMZ?claim=abc')
    expect(screen.getByText('Conectando como presentador…')).toBeInTheDocument()
  })
})

describe('pantalla de inicio (US-10)', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ lanAddress: '192.168.1.42', backendPort: 8080, frontendPort: 5173 }), { status: 200 }),
      ),
    )
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('AC-29 muestra dos QR (presentador y jugadores) con su URL', async () => {
    session.setPresenterClaim('KDMZ', 'claim-123')
    renderAt('/screen/KDMZ')

    const presenter = screen.getByRole('region', { name: 'QR Presentador' })
    const players = screen.getByRole('region', { name: 'QR Jugadores' })

    expect(await within(presenter).findByText('http://192.168.1.42:5173/host/KDMZ?claim=claim-123')).toBeInTheDocument()
    expect(within(players).getByText('http://192.168.1.42:5173/join/KDMZ')).toBeInTheDocument()
  })

  it('AC-68 · AC-69 · AC-70 la pantalla de QR no muestra código de sala, IP ni botón de sonido', async () => {
    renderAt('/screen/KDMZ')
    await within(screen.getByRole('region', { name: 'QR Jugadores' })).findByText('http://192.168.1.42:5173/join/KDMZ')
    expect(screen.queryByLabelText('Código de sala')).toBeNull()
    expect(screen.queryByLabelText('IP de la red')).toBeNull()
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.queryByRole('button', { name: /sonido/i })).toBeNull()
  })

  it('AC-72 el botón circular "Regresar" vuelve a la pantalla de título', async () => {
    renderAt('/screen/KDMZ')
    await within(screen.getByRole('region', { name: 'QR Jugadores' })).findByText('http://192.168.1.42:5173/join/KDMZ')
    screen.getByRole('button', { name: 'Regresar' }).click()
    expect(await screen.findByRole('button', { name: /empezar/i })).toBeInTheDocument()
  })

  it('AC-61 la cabecera de /screen muestra el logotipo "Lo que 100 Mexicanos dicen"', async () => {
    renderAt('/screen/KDMZ')
    expect(screen.getByRole('heading', { level: 1, name: 'Lo que 100 Mexicanos dicen' })).toBeInTheDocument()
    await within(screen.getByRole('region', { name: 'QR Jugadores' })).findByText('http://192.168.1.42:5173/join/KDMZ')
  })

  it('AC-30 sin claim no muestra el QR de presentador', async () => {
    renderAt('/screen/KDMZ')
    const players = screen.getByRole('region', { name: 'QR Jugadores' })
    await within(players).findByText('http://192.168.1.42:5173/join/KDMZ')
    expect(within(screen.getByRole('region', { name: 'QR Presentador' })).queryByText(/\/host\//)).toBeNull()
  })
})
