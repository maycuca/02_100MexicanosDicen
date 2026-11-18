import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFakeStompClient, fakeClients } from '../../test/fakeStomp'
import { mockFetch, networkInfo } from '../../test/fetchMock'
import { lobbyState } from '../../test/fixtures'
import { renderRoute } from '../../test/renderRoute'
import { getSessionScope, session } from '../../shared/session/session'
import { installLocalTest, isLocalTestMode, setLocalTestMode } from '.'

vi.mock('../../shared/realtime/stompClient', () => ({ createStompClient: (c: never) => createFakeStompClient(c) }))

beforeEach(() => {
  fakeClients.length = 0
  localStorage.clear()
  sessionStorage.clear()
})
afterEach(() => {
  setLocalTestMode(false)
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  localStorage.clear()
  sessionStorage.clear()
})

function setupScreen({ networkFails = false } = {}) {
  return mockFetch([
    networkFails
      ? { path: '/api/network-info', status: 503, body: { status: 503, code: 'INTERNAL_ERROR' } }
      : { path: '/api/network-info', body: networkInfo },
    { path: '/api/rooms/KDMZ', body: lobbyState() },
  ])
}

const origin = () => window.location.origin

describe('modo prueba local (spec 004, T902)', () => {
  it('AC-73 está apagado por defecto y la bandera se recuerda en este navegador', () => {
    expect(isLocalTestMode()).toBe(false)
    setLocalTestMode(true)
    expect(localStorage.getItem('cm:local-test')).toBe('on')
    setLocalTestMode(false)
    expect(localStorage.getItem('cm:local-test')).toBeNull()
  })

  it('AC-76 con el modo encendido cada pestaña guarda su sesión en sessionStorage', () => {
    setLocalTestMode(true)
    expect(getSessionScope()).toBe('tab')
    session.setPlayerToken('KDMZ', 'tok-tab')
    expect(sessionStorage.getItem('cm:player:KDMZ')).toBe('tok-tab')
    expect(localStorage.getItem('cm:player:KDMZ')).toBeNull()
    expect(session.getPlayerToken('KDMZ')).toBe('tok-tab')
  })

  it('AC-78 con el modo apagado la sesión sigue en localStorage', () => {
    installLocalTest()
    expect(getSessionScope()).toBe('local')
    session.setPlayerToken('KDMZ', 'tok-local')
    expect(localStorage.getItem('cm:player:KDMZ')).toBe('tok-local')
    expect(sessionStorage.getItem('cm:player:KDMZ')).toBeNull()
  })

  it('AC-77 una pestaña nueva hereda el modo al cargar (misma bandera de origen)', () => {
    localStorage.setItem('cm:local-test', 'on')
    installLocalTest()
    expect(getSessionScope()).toBe('tab')
  })
})

describe('AJUSTES y pantalla de título (T903)', () => {
  it('AC-73 · AC-74 la fila "Prueba local" enciende el modo y aparece el distintivo; Restablecer no lo cambia', async () => {
    const user = userEvent.setup()
    mockFetch([{ path: /.*/, body: {} }])
    renderRoute('/')
    expect(screen.queryByText('PRUEBA LOCAL')).toBeNull()

    await user.click(screen.getByRole('button', { name: 'Ajustes' }))
    const dialog = screen.getByRole('dialog', { name: 'Ajustes' })
    const toggle = within(dialog).getByRole('switch', { name: 'Prueba local' })
    expect(toggle).toHaveAttribute('aria-checked', 'false')

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-checked', 'true')
    expect(isLocalTestMode()).toBe(true)
    expect(screen.getByText('PRUEBA LOCAL')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Restablecer' }))
    expect(toggle).toHaveAttribute('aria-checked', 'true')

    await user.click(toggle)
    expect(isLocalTestMode()).toBe(false)
    expect(screen.queryByText('PRUEBA LOCAL')).toBeNull()
  })
})

describe('pantalla de QR en prueba local (T904)', () => {
  beforeEach(() => {
    setLocalTestMode(true)
    session.setPresenterToken('KDMZ', 'tv-token')
    session.setPresenterClaim('KDMZ', 'claim-1')
  })

  it('AC-75 muestra las URL con el origen del navegador, sin QR y aunque falle la detección de IP', async () => {
    setupScreen({ networkFails: true })
    renderRoute('/screen/KDMZ')

    const presenter = screen.getByRole('region', { name: 'QR Presentador' })
    const players = screen.getByRole('region', { name: 'QR Jugadores' })
    expect(await within(players).findByLabelText('URL de Jugadores')).toHaveTextContent(`${origin()}/join/KDMZ`)
    expect(within(presenter).getByLabelText('URL de Presentador')).toHaveTextContent(`${origin()}/host/KDMZ?claim=claim-1`)
    expect(screen.queryByTitle(/^QR /)).toBeNull()
    expect(screen.getByRole('note')).toHaveTextContent('pega cada URL en una pestaña distinta')
    expect(screen.queryByText(/No se pudo detectar la IP/)).toBeNull()
  })

  it('AC-75 "Copiar" copia la URL y confirma; "Abrir en pestaña nueva" usa noopener', async () => {
    const user = userEvent.setup()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    setupScreen()
    renderRoute('/screen/KDMZ')

    const players = screen.getByRole('region', { name: 'QR Jugadores' })
    await within(players).findByLabelText('URL de Jugadores')
    await user.click(within(players).getByRole('button', { name: 'Copiar' }))
    expect(writeText).toHaveBeenCalledWith(`${origin()}/join/KDMZ`)
    expect(within(players).getByRole('status')).toHaveTextContent('¡Copiada!')

    await user.click(within(players).getByRole('button', { name: 'Abrir en pestaña nueva' }))
    expect(open).toHaveBeenCalledWith(`${origin()}/join/KDMZ`, '_blank', 'noopener,noreferrer')
  })

  it('AC-75 con el presentador conectado la tarjeta muestra "Presentador conectado ✓"', async () => {
    mockFetch([
      { path: '/api/network-info', body: networkInfo },
      { path: '/api/rooms/KDMZ', body: lobbyState({ presenterConnected: true }) },
    ])
    renderRoute('/screen/KDMZ')
    const presenter = screen.getByRole('region', { name: 'QR Presentador' })
    expect(await within(presenter).findByText('Presentador conectado ✓')).toBeInTheDocument()
    expect(within(presenter).queryByRole('button', { name: 'Copiar' })).toBeNull()
  })
})

describe('modo apagado (AC-78)', () => {
  it('la pantalla de QR muestra los QR con la IP de la LAN y ningún control de prueba local', async () => {
    session.setPresenterClaim('KDMZ', 'claim-1')
    setupScreen()
    renderRoute('/screen/KDMZ')
    const players = screen.getByRole('region', { name: 'QR Jugadores' })
    expect(await within(players).findByText('http://192.168.1.42:5173/join/KDMZ')).toBeInTheDocument()
    expect(screen.getAllByTitle(/^QR /).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Copiar' })).toBeNull()
    expect(screen.queryByRole('note')).toBeNull()
  })
})
