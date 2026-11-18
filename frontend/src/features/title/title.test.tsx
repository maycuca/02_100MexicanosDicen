import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mockFetch } from '../../test/fetchMock'
import { lobbyState } from '../../test/fixtures'
import { renderRoute } from '../../test/renderRoute'
import { audioContext, installFakeAudio, playedSounds } from '../../test/fakeAudio'
import { session } from '../../shared/session/session'
import { DEFAULT_SETTINGS, loadSettings } from './settings'

const created = { code: 'QWER', presenterToken: 'ptok', presenterClaimToken: 'claim1', state: lobbyState({ code: 'QWER' }) }

function mockCreate(status = 201, body: unknown = created) {
  return mockFetch([
    { method: 'POST', path: '/api/rooms', status, body },
    { path: /.*/, body: {} },
  ])
}

const menuItem = (name: 'Empezar' | 'Ajustes') => screen.getByRole('button', { name })

beforeEach(() => {
  localStorage.clear()
})
afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('pantalla de título (T811)', () => {
  it('AC-55 / AC-61 muestra el logotipo y el menú EMPEZAR / AJUSTES, sin campos de formulario', () => {
    renderRoute('/')
    expect(screen.getByRole('heading', { level: 1, name: 'Lo que 100 Mexicanos dicen' })).toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Menú principal' })
    expect(within(menu).getAllByRole('button').map((b) => b.textContent)).toEqual(['►Empezar', '►Ajustes'])
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.queryByRole('spinbutton')).toBeNull()
    expect(screen.queryByRole('combobox')).toBeNull()
    expect(screen.queryByText(/Nombre del equipo/)).toBeNull()
  })

  it('AC-56 al cargar la selección está en EMPEZAR', () => {
    renderRoute('/')
    expect(menuItem('Empezar')).toHaveAttribute('aria-current', 'true')
    expect(menuItem('Empezar')).toHaveFocus()
    expect(menuItem('Ajustes')).not.toHaveAttribute('aria-current')
  })

  it('AC-56 ↑/↓ y W/S mueven la selección (y dan la vuelta)', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    await user.keyboard('{ArrowDown}')
    expect(menuItem('Ajustes')).toHaveAttribute('aria-current', 'true')
    expect(menuItem('Ajustes')).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(menuItem('Empezar')).toHaveAttribute('aria-current', 'true')
    await user.keyboard('s')
    expect(menuItem('Ajustes')).toHaveAttribute('aria-current', 'true')
    await user.keyboard('w')
    expect(menuItem('Empezar')).toHaveAttribute('aria-current', 'true')
    await user.keyboard('{ArrowUp}')
    expect(menuItem('Ajustes')).toHaveAttribute('aria-current', 'true')
  })

  it('AC-56 las teclas funcionan aunque el foco no esté en el menú', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    menuItem('Empezar').blur()
    await user.keyboard('{ArrowDown}{Enter}')
    expect(await screen.findByRole('dialog', { name: 'Ajustes' })).toBeInTheDocument()
  })

  it('AC-56 el mouse selecciona al pasar por encima y el clic activa', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    fireEvent.pointerEnter(menuItem('Ajustes'))
    expect(menuItem('Ajustes')).toHaveAttribute('aria-current', 'true')
    await user.click(menuItem('Ajustes'))
    expect(await screen.findByRole('dialog', { name: 'Ajustes' })).toBeInTheDocument()
  })
})

describe('EMPEZAR (T812)', () => {
  it('AC-57 Enter en EMPEZAR crea la sala SIN nombres de equipo, guarda los tokens y abre /screen', async () => {
    const { calls } = mockCreate()
    const user = userEvent.setup()
    const { router } = renderRoute('/')
    await user.keyboard('{Enter}')
    await waitFor(() => expect(router.state.location.pathname).toBe('/screen/QWER'))
    const post = calls.filter((c) => c.method === 'POST' && c.path === '/api/rooms')
    expect(post).toHaveLength(1)
    expect(post[0].body).toEqual(DEFAULT_SETTINGS)
    expect(post[0].body).not.toHaveProperty('teamAName')
    expect(post[0].body).not.toHaveProperty('teamBName')
    expect(session.getPresenterToken('QWER')).toBe('ptok')
    expect(session.getPresenterClaim('QWER')).toBe('claim1')
  })

  it('AC-57 muestra el estado de carga mientras se crea la sala', async () => {
    let resolve: (r: Response) => void = () => {}
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>((r) => (resolve = r))))
    const user = userEvent.setup()
    const { router } = renderRoute('/')
    await user.click(menuItem('Empezar'))
    const loading = await screen.findByRole('button', { name: /Preparando/ })
    expect(loading).toHaveAttribute('aria-busy', 'true')
    expect(menuItem('Ajustes')).toBeDisabled()
    // Un segundo Enter no crea otra sala.
    await user.keyboard('{Enter}')
    expect(fetch).toHaveBeenCalledTimes(1)
    resolve(new Response(JSON.stringify(created), { status: 201 }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/screen/QWER'))
  })

  it('AC-57 si falla muestra un aviso estilo juego y permite reintentar', async () => {
    mockFetch([{ method: 'POST', path: '/api/rooms', status: 503, body: { status: 503 } }])
    const user = userEvent.setup()
    const { router } = renderRoute('/')
    await user.keyboard('{Enter}')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('¡Ups! No se pudo empezar')
    expect(alert).toHaveTextContent('El servidor del juego no responde')
    expect(menuItem('Empezar')).toBeEnabled()

    const { calls } = mockCreate()
    await user.click(within(alert).getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/screen/QWER'))
    expect(calls.filter((c) => c.method === 'POST' && c.path === '/api/rooms')).toHaveLength(1)
  })
})

describe('modal de AJUSTES (T812)', () => {
  async function openSettings(user: ReturnType<typeof userEvent.setup>) {
    await user.keyboard('{ArrowDown}{Enter}')
    return screen.findByRole('dialog', { name: 'Ajustes' })
  }

  it('AC-58 muestra los valores por defecto del contrato con selectores ◄ ►', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    const dialog = await openSettings(user)
    expect(within(dialog).getByRole('spinbutton', { name: 'Rondas' })).toHaveAttribute('aria-valuenow', '5')
    expect(within(dialog).getByRole('spinbutton', { name: 'Meta de puntos' })).toHaveAttribute('aria-valuetext', '300 pts')
    expect(within(dialog).getByRole('spinbutton', { name: 'Temporizador' })).toHaveAttribute('aria-valuetext', '20 s')
    expect(within(dialog).getByRole('switch', { name: 'X automática' })).toHaveAttribute('aria-checked', 'true')
    // El foco entra al modal.
    expect(within(dialog).getByRole('spinbutton', { name: 'Rondas' })).toHaveFocus()
  })

  it('AC-58 ←/→ cambian el valor dentro de los rangos, ↑/↓ mueven entre opciones y se guarda en localStorage', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    const dialog = await openSettings(user)
    const rounds = within(dialog).getByRole('spinbutton', { name: 'Rondas' })
    await user.keyboard('{ArrowRight}{ArrowRight}{ArrowRight}{ArrowRight}')
    expect(rounds).toHaveAttribute('aria-valuenow', '8') // tope 3–8
    await user.keyboard('{ArrowDown}')
    const target = within(dialog).getByRole('spinbutton', { name: 'Meta de puntos' })
    expect(target).toHaveFocus()
    await user.keyboard('{ArrowLeft}')
    expect(target).toHaveAttribute('aria-valuenow', '250')
    await user.click(within(dialog).getByRole('button', { name: 'Meta de puntos: anterior' }))
    expect(target).toHaveAttribute('aria-valuenow', '200')
    expect(loadSettings()).toEqual({ ...DEFAULT_SETTINGS, rounds: 8, targetScore: 200 })
  })

  it('AC-58 "Sin temporizador" deshabilita la X automática', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    const dialog = await openSettings(user)
    const timer = within(dialog).getByRole('spinbutton', { name: 'Temporizador' })
    timer.focus()
    await user.keyboard('{ArrowLeft}{ArrowLeft}')
    expect(timer).toHaveAttribute('aria-valuetext', '10 s')
    await user.keyboard('{ArrowLeft}')
    expect(timer).toHaveAttribute('aria-valuetext', 'Sin temporizador')
    expect(within(dialog).getByRole('switch', { name: 'X automática' })).toHaveAttribute('aria-disabled', 'true')
    await user.keyboard('{ArrowRight}'.repeat(15))
    expect(timer).toHaveAttribute('aria-valuetext', '60 s')
  })

  it('AC-58 Esc cierra el modal y regresa el foco a AJUSTES; "Volver" también cierra', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    await openSettings(user)
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(menuItem('Ajustes')).toHaveFocus())

    // AJUSTES sigue seleccionado: Enter lo vuelve a abrir.
    await user.keyboard('{Enter}')
    const dialog = await screen.findByRole('dialog', { name: 'Ajustes' })
    await user.click(within(dialog).getByRole('button', { name: 'Volver' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('AC-58 el foco queda atrapado en el modal (Tab da la vuelta)', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    const dialog = await openSettings(user)
    const volver = within(dialog).getByRole('button', { name: 'Volver' })
    volver.focus()
    await user.tab()
    expect(within(dialog).getByRole('spinbutton', { name: 'Rondas' })).toHaveFocus()
    await user.tab({ shift: true })
    expect(volver).toHaveFocus()
  })

  it('AC-58 "Restablecer" vuelve a los valores del contrato', async () => {
    localStorage.setItem('cm:settings', JSON.stringify({ rounds: 7, targetScore: 500, turnSeconds: 45, autoStrikeOnTimeout: false }))
    const user = userEvent.setup()
    renderRoute('/')
    const dialog = await openSettings(user)
    expect(within(dialog).getByRole('spinbutton', { name: 'Rondas' })).toHaveAttribute('aria-valuenow', '7')
    await user.click(within(dialog).getByRole('button', { name: 'Restablecer' }))
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS)
  })

  it('AC-57 / AC-58 el siguiente EMPEZAR usa los ajustes guardados (también tras recargar)', async () => {
    localStorage.setItem('cm:settings', JSON.stringify({ rounds: 3, targetScore: 600, turnSeconds: 0, autoStrikeOnTimeout: false }))
    const { calls } = mockCreate()
    const user = userEvent.setup()
    renderRoute('/')
    expect(screen.getByText('3 rondas · meta 600 · sin temporizador')).toBeInTheDocument()
    await user.keyboard('{Enter}')
    await waitFor(() => expect(calls.some((c) => c.path === '/api/rooms')).toBe(true))
    expect(calls.find((c) => c.path === '/api/rooms')?.body).toEqual({
      rounds: 3,
      targetScore: 600,
      turnSeconds: 0,
      autoStrikeOnTimeout: false,
    })
  })

  it('AC-58 ignora ajustes guardados fuera de rango o corruptos', () => {
    localStorage.setItem('cm:settings', JSON.stringify({ rounds: 12, targetScore: 'x', turnSeconds: 7, autoStrikeOnTimeout: 'sí' }))
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS)
    localStorage.setItem('cm:settings', '{no es json')
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS)
  })

  it('AC-58 sin localStorage disponible el menú sigue funcionando', async () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceeded')
    })
    try {
      const user = userEvent.setup()
      renderRoute('/')
      const dialog = await openSettings(user)
      await user.keyboard('{ArrowRight}')
      expect(within(dialog).getByRole('spinbutton', { name: 'Rondas' })).toHaveAttribute('aria-valuenow', '6')
    } finally {
      spy.mockRestore()
      set.mockRestore()
    }
  })
})

describe('sonidos del menú (T813)', () => {
  beforeEach(() => installFakeAudio())

  it('AC-59 con sonido activado, mover y activar suenan (Web Audio, tras la interacción)', async () => {
    localStorage.setItem('cm:sound', 'on')
    const user = userEvent.setup()
    renderRoute('/')
    expect(audioContext()).toBeUndefined() // sin interacción previa no suena nada
    await user.keyboard('{ArrowDown}')
    await waitFor(() => expect(playedSounds()).toEqual(['menuMove']))
    await user.keyboard('{Enter}')
    await waitFor(() => expect(playedSounds()).toEqual(['menuMove', 'menuSelect']))
  })

  it('AC-59 sin la preferencia de sonido no suena nada', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    await user.keyboard('{ArrowDown}{ArrowUp}')
    expect(audioContext()).toBeUndefined()
  })

  it('AC-59 / AC-52 el modal permite activar el sonido y lo recuerda', async () => {
    const user = userEvent.setup()
    renderRoute('/')
    await user.keyboard('{ArrowDown}{Enter}')
    const dialog = await screen.findByRole('dialog', { name: 'Ajustes' })
    const soundSwitch = within(dialog).getByRole('switch', { name: 'Sonido' })
    expect(soundSwitch).toHaveAttribute('aria-checked', 'false')
    await user.click(soundSwitch)
    expect(soundSwitch).toHaveAttribute('aria-checked', 'true')
    expect(localStorage.getItem('cm:sound')).toBe('on')
    await waitFor(() => expect(playedSounds()).toContain('menuSelect'))
  })
})
