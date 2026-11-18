import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFakeStompClient, fakeClients, lastClient } from '../../test/fakeStomp'
import { mockFetch } from '../../test/fetchMock'
import { PLAYER_A, PLAYER_B, faceOffState, lobbyState } from '../../test/fixtures'
import { renderRoute } from '../../test/renderRoute'
import { session } from '../../shared/session/session'
import type { Player } from '../../shared/api/client'

vi.mock('../../shared/realtime/stompClient', () => ({ createStompClient: (c: never) => createFakeStompClient(c) }))

beforeEach(() => {
  fakeClients.length = 0
})
afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('JoinPage (T501)', () => {
  it('AC-05 se une con nombre y equipo, guarda el playerToken y va a jugar', async () => {
    const { calls } = mockFetch([
      { path: '/api/rooms/KDMZ', body: lobbyState() },
      { method: 'POST', path: '/api/rooms/KDMZ/players', status: 201, body: { player: PLAYER_A, playerToken: 'pt-ana' } },
      { path: /.*/, body: {} },
    ])
    const user = userEvent.setup()
    const { router } = renderRoute('/join/KDMZ')
    await user.type(screen.getByLabelText(/Tu nombre/), '  Ana ')
    await user.click(await screen.findByRole('button', { name: 'Los Tlacoyos' }))
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/play/KDMZ'))
    expect(calls.find((c) => c.method === 'POST')?.body).toEqual({ name: 'Ana', team: 'A' })
    expect(session.getPlayerToken('KDMZ')).toBe('pt-ana')
  })

  it('AC-06 muestra NAME_TAKEN junto al nombre', async () => {
    mockFetch([
      { path: '/api/rooms/KDMZ', body: lobbyState() },
      { method: 'POST', path: '/api/rooms/KDMZ/players', status: 409, body: { code: 'NAME_TAKEN' } },
    ])
    const user = userEvent.setup()
    renderRoute('/join/KDMZ')
    await user.type(screen.getByLabelText(/Tu nombre/), 'Ana')
    await user.click(await screen.findByRole('button', { name: 'Las Gorditas' }))
    await user.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText(/Ya hay alguien con ese nombre/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Tu nombre/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('AC-07 avisa si la partida ya empezó', async () => {
    mockFetch([{ path: '/api/rooms/KDMZ', body: faceOffState() }])
    renderRoute('/join/KDMZ')
    expect(await screen.findByRole('alert')).toHaveTextContent('ya no es posible unirse')
  })

  it('avisa si la sala no existe (ROOM_NOT_FOUND)', async () => {
    mockFetch([{ path: '/api/rooms/KDMZ', status: 404, body: { code: 'ROOM_NOT_FOUND' } }])
    renderRoute('/join/KDMZ')
    expect(await screen.findByRole('alert')).toHaveTextContent('No existe una sala')
  })
})

describe('PlayPage (T502, T503)', () => {
  function setup(state = faceOffState(), me: Player = PLAYER_A) {
    session.setPlayerToken('KDMZ', 'pt')
    return mockFetch([
      { path: '/api/rooms/KDMZ/players/me', body: me },
      { path: '/api/rooms/KDMZ', body: state },
    ])
  }

  it('spec 007 AC-113 con ROOM_CLOSED el jugador borra su sesión y ve "¡Gracias por jugar!"', async () => {
    setup(lobbyState())
    const { router } = renderRoute('/play/KDMZ')
    await screen.findByText('Espera a que el presentador inicie la partida…')
    act(() => lastClient().connect())
    act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'ROOM_CLOSED', version: 9 }))
    expect(router.state.location.pathname).toBe('/gracias')
    expect(await screen.findByRole('heading', { name: '¡Gracias por jugar!' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Menú principal' })).toBeNull()
    // spec 007 AC-118: "Cerrar ventana"; si el navegador no deja cerrarla, se explica cómo
    const close = vi.spyOn(window, 'close').mockImplementation(() => {})
    await userEvent.setup().click(screen.getByRole('button', { name: 'Cerrar ventana' }))
    expect(close).toHaveBeenCalled()
    expect(await screen.findByText(/cierra esta pestaña o ventana/)).toBeInTheDocument()
    close.mockRestore()
    expect(session.getPlayerToken('KDMZ')).toBeNull()
  })

  it('spec 007 AC-116 al ganar el celular dice "¡Ganaron!" con los puntos; al perder, "¡Buen juego!"', async () => {
    const over = lobbyState({ status: 'GAME_OVER', winner: 'A', version: 30 })
    over.teams = over.teams.map((t) => ({ ...t, score: t.id === 'A' ? 320 : 150 }))
    setup(over)
    const first = renderRoute('/play/KDMZ')
    const result = await screen.findByRole('status', { name: 'Resultado final' })
    expect(result).toHaveTextContent('¡Ganaron!')
    expect(result).toHaveTextContent('320')
    expect(result).toHaveTextContent('150')
    first.unmount()

    setup(over, PLAYER_B)
    renderRoute('/play/KDMZ')
    expect(await screen.findByRole('status', { name: 'Resultado final' })).toHaveTextContent('¡Buen juego!')
  })

  it('AC-09 recupera la sesión con getMe y el playerToken guardado', async () => {
    const { calls } = setup(lobbyState())
    renderRoute('/play/KDMZ')
    expect(await screen.findByText('Espera a que el presentador inicie la partida…')).toBeInTheDocument()
    expect(screen.getByText('Ana')).toBeInTheDocument()
    expect(calls.find((c) => c.path.endsWith('/players/me'))?.headers['X-Player-Token']).toBe('pt')
    expect(lastClient().credentials).toEqual({ playerToken: 'pt' })
  })

  it('sin token vuelve a la página de unirse', async () => {
    mockFetch([{ path: /.*/, body: lobbyState() }])
    const { router } = renderRoute('/play/KDMZ')
    await waitFor(() => expect(router.state.location.pathname).toBe('/join/KDMZ'))
  })

  it('AC-13 el jugador designado ve el buzzer y lo envía por STOMP', async () => {
    setup()
    const user = userEvent.setup()
    renderRoute('/play/KDMZ')
    const buzzer = await screen.findByRole('button', { name: /Buzzer/ })
    act(() => lastClient().connect())
    await user.click(buzzer)
    expect(lastClient().published).toEqual([{ destination: '/app/rooms/KDMZ/buzz', body: '{}' }])
    expect(screen.queryByRole('button', { name: /Buzzer/ })).toBeNull()

    const won = faceOffState({ version: 6 })
    won.round!.faceOff!.buzzWinner = PLAYER_A.id
    act(() => lastClient().emit('/topic/rooms/KDMZ/state', won))
    expect(screen.getByText('¡Ganaste el buzzer! Di tu respuesta en voz alta.')).toBeInTheDocument()
  })

  it('AC-13 los demás ven "Espera tu turno"', async () => {
    const noB = faceOffState()
    noB.round!.faceOff!.playerB = '99999999-9999-4999-8999-999999999999'
    setup(noB, PLAYER_B)
    renderRoute('/play/KDMZ')
    expect(await screen.findByText('Espera tu turno')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Buzzer/ })).toBeNull()
  })

  it('AC-24 muestra pregunta, marcador y "tu equipo responde"', async () => {
    const playing = faceOffState({ status: 'PLAYING' })
    playing.round!.controllingTeam = 'A'
    playing.teams[0].score = 120
    setup(playing)
    renderRoute('/play/KDMZ')
    expect(await screen.findByText('¡Tu equipo responde!')).toBeInTheDocument()
    expect(screen.getByText('Algo que se lleva a la playa')).toBeInTheDocument()
    expect(screen.getByText('120')).toBeInTheDocument()
  })

  it('muestra errores de /user/queue/errors en español', async () => {
    setup()
    renderRoute('/play/KDMZ')
    await screen.findByRole('button', { name: /Buzzer/ })
    act(() => lastClient().connect())
    act(() => lastClient().emit('/user/queue/errors', { code: 'NOT_YOUR_TURN', message: 'Not your turn' }))
    expect(screen.getByRole('alert')).toHaveTextContent('No es tu turno.')
  })
})

describe('PlayPage: turno y vibración (T716)', () => {
  function setup(state = faceOffState(), me: Player = PLAYER_A) {
    session.setPlayerToken('KDMZ', 'pt')
    return mockFetch([
      { path: '/api/rooms/KDMZ/players/me', body: me },
      { path: '/api/rooms/KDMZ', body: state },
    ])
  }
  function withTurn(team: 'A' | 'B' | null, overrides: Parameters<typeof faceOffState>[0] = {}) {
    const s = faceOffState({ status: 'PLAYING', ...overrides })
    s.round = { ...s.round!, controllingTeam: 'A', activeTeam: team }
    return s
  }

  it('AC-38 "¡Le toca a tu equipo!" cuando su equipo está en turno', async () => {
    setup(withTurn('A'))
    renderRoute('/play/KDMZ')
    expect(await screen.findByText('¡Le toca a tu equipo!')).toBeInTheDocument()
  })

  it('AC-38 "Turno de <equipo>" cuando le toca al otro', async () => {
    setup(withTurn('B'))
    renderRoute('/play/KDMZ')
    expect(await screen.findByText('Turno de Las Gorditas')).toBeInTheDocument()
    expect(screen.queryByText('¡Le toca a tu equipo!')).toBeNull()
  })

  it('spec 005 AC-109 el celular muestra los puntos de la ronda sin la etiqueta "Bote"', async () => {
    const s = withTurn('A')
    s.round = { ...s.round!, pot: 70 }
    setup(s)
    renderRoute('/play/KDMZ')
    expect(await screen.findByLabelText('Puntos de la ronda')).toHaveTextContent('70')
    expect(screen.queryByText(/Bote/)).toBeNull()
  })

  it('spec 006 AC-103 en el segundo intento del cara a cara el celular dice "¡<equipo> debe superar la #N!"', async () => {
    const s = faceOffState()
    s.round = { ...s.round!, activeTeam: 'B', faceOff: { ...s.round!.faceOff!, buzzWinner: s.round!.faceOff!.playerA, firstTeam: 'A', firstAnswerPosition: 2 } }
    setup(s)
    renderRoute('/play/KDMZ')
    expect(await screen.findByText('¡Las Gorditas debe superar la #2!')).toBeInTheDocument()
  })

  it('AC-38 sin equipo en turno no muestra banner', async () => {
    setup(faceOffState())
    renderRoute('/play/KDMZ')
    await screen.findByRole('button', { name: /Buzzer/ })
    expect(screen.queryByText(/Turno de|Le toca/)).toBeNull()
  })

  it('AC-50 el jugador ve la cuenta regresiva', async () => {
    const s = withTurn('A')
    s.round!.turnDeadline = new Date(Date.now() + 30_000).toISOString()
    s.round!.timerRunning = true
    setup(s)
    renderRoute('/play/KDMZ')
    expect(await screen.findByRole('timer', { name: /Tiempo del turno: (29|30) segundos/ })).toBeInTheDocument()
  })

  it('AC-54 vibra al ganar el buzzer, cuando el turno pasa a su equipo y con X a su equipo', async () => {
    const vibrateSpy = vi.fn(() => true)
    vi.stubGlobal('navigator', Object.assign(Object.create(navigator), { vibrate: vibrateSpy }))
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      setup(withTurn('B'))
      renderRoute('/play/KDMZ')
      await screen.findByText('Turno de Las Gorditas')
      act(() => lastClient().connect())
      const emit = (e: object) => act(() => lastClient().emit('/topic/rooms/KDMZ/events', e))

      // Eventos de otros: no vibra.
      emit({ type: 'BUZZ_WON', version: 6, playerId: PLAYER_B.id, team: 'B' })
      emit({ type: 'STRIKE', version: 7, team: 'B', strikes: 1 })
      expect(vibrateSpy).not.toHaveBeenCalled()

      emit({ type: 'BUZZ_WON', version: 8, playerId: PLAYER_A.id, team: 'A' })
      expect(vibrateSpy).toHaveBeenCalledTimes(1)
      vi.advanceTimersByTime(1_000)
      emit({ type: 'TURN_CHANGED', version: 9, team: 'A' })
      expect(vibrateSpy).toHaveBeenCalledTimes(2)
      vi.advanceTimersByTime(1_000)
      emit({ type: 'STRIKE', version: 10, team: 'A', strikes: 1 })
      expect(vibrateSpy).toHaveBeenCalledTimes(3)
      expect(vibrateSpy.mock.calls.map((c: unknown[]) => c[0])).toEqual([[80, 40, 80], [200], [400]])
    } finally {
      vi.useRealTimers()
    }
  })

  it('AC-54 sin navigator.vibrate no pasa nada', async () => {
    vi.stubGlobal('navigator', Object.assign(Object.create(navigator), { vibrate: undefined }))
    setup(withTurn('B'))
    renderRoute('/play/KDMZ')
    await screen.findByText('Turno de Las Gorditas')
    act(() => lastClient().connect())
    expect(() =>
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'TURN_CHANGED', version: 9, team: 'A' })),
    ).not.toThrow()
  })
})

describe('capitán y nombre del equipo (T815)', () => {
  const CARLOS = { id: '33333333-3333-4333-8333-333333333333', name: 'Carlos', team: 'A' as const, connected: true }
  const lobbyWithCarlos = (overrides: Parameters<typeof lobbyState>[0] = {}) => {
    const base = lobbyState(overrides)
    return { ...base, teams: base.teams.map((t) => (t.id === 'A' ? { ...t, players: [...t.players, CARLOS] } : t)) }
  }
  const renamed = (name: string, version = 4) => {
    const s = lobbyWithCarlos({ version })
    return { ...s, teams: s.teams.map((t) => (t.id === 'A' ? { ...t, name } : t)) }
  }

  function setup(me: Player, extra: Parameters<typeof mockFetch>[0] = []) {
    session.setPlayerToken('KDMZ', 'pt')
    return mockFetch([
      ...extra,
      { path: '/api/rooms/KDMZ/players/me', body: me },
      { path: '/api/rooms/KDMZ', body: lobbyWithCarlos() },
    ])
  }

  it('AC-66 el capitán ve "Eres el capitán de <equipo>" y guarda un nombre nuevo', async () => {
    const { calls } = setup(PLAYER_A, [{ method: 'PUT', path: '/api/rooms/KDMZ/teams/A/name', body: renamed('Los Chilaquiles') }])
    const user = userEvent.setup()
    renderRoute('/play/KDMZ')
    expect(await screen.findByText('Eres el capitán de Los Tlacoyos')).toBeInTheDocument()
    const input = screen.getByLabelText('Ponle nombre a tu equipo')
    expect(input).toHaveValue('Los Tlacoyos')
    const save = screen.getByRole('button', { name: 'Guardar nombre' })
    expect(save).toBeEnabled() // spec 007 AC-111: también con el nombre sugerido
    await user.clear(input)
    await user.type(input, '  Los Chilaquiles ')
    await user.click(save)
    expect(await screen.findByText('¡Nombre guardado!')).toBeInTheDocument()
    const put = calls.find((c) => c.method === 'PUT')
    expect(put).toMatchObject({ path: '/api/rooms/KDMZ/teams/A/name', headers: { 'X-Player-Token': 'pt' }, body: { name: 'Los Chilaquiles' } })
    // La respuesta del servidor se aplica como snapshot.
    expect(screen.getByText('Eres el capitán de Los Chilaquiles')).toBeInTheDocument()
    expect(screen.getByLabelText('Ponle nombre a tu equipo')).toHaveValue('Los Chilaquiles')
  })

  it('spec 007 AC-111 guardar el nombre sugerido sin cambiarlo confirma "¡Nombre guardado!"', async () => {
    const { calls } = setup(PLAYER_A, [{ method: 'PUT', path: '/api/rooms/KDMZ/teams/A/name', body: renamed('Los Tlacoyos') }])
    const user = userEvent.setup()
    renderRoute('/play/KDMZ')
    await user.click(await screen.findByRole('button', { name: 'Guardar nombre' }))
    expect(await screen.findByText('¡Nombre guardado!')).toBeInTheDocument()
    expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({ name: 'Los Tlacoyos' })
  })

  it('AC-63 / AC-66 valida longitud y que no repita el nombre del otro equipo', async () => {
    setup(PLAYER_A)
    const user = userEvent.setup()
    renderRoute('/play/KDMZ')
    const input = await screen.findByLabelText('Ponle nombre a tu equipo')
    const save = screen.getByRole('button', { name: 'Guardar nombre' })
    await user.clear(input)
    await user.type(input, 'X')
    expect(save).toBeDisabled()
    await user.clear(input)
    await user.type(input, 'las gorditas')
    expect(save).toBeDisabled()
    expect(screen.getByText('El otro equipo ya usa ese nombre. Elige otro.')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-invalid', 'true')
  })

  it('AC-64 muestra TEAM_NAME_TAKEN del servidor junto al campo', async () => {
    setup(PLAYER_A, [{ method: 'PUT', path: /\/name$/, status: 409, body: { status: 409, code: 'TEAM_NAME_TAKEN' } }])
    const user = userEvent.setup()
    renderRoute('/play/KDMZ')
    const input = await screen.findByLabelText('Ponle nombre a tu equipo')
    await user.clear(input)
    await user.type(input, 'Los Elotes')
    await user.click(screen.getByRole('button', { name: 'Guardar nombre' }))
    expect(await screen.findByText('El otro equipo ya usa ese nombre. Elige otro.')).toBeInTheDocument()
  })

  it('AC-64 muestra NOT_CAPTAIN como aviso', async () => {
    setup(PLAYER_A, [{ method: 'PUT', path: /\/name$/, status: 403, body: { status: 403, code: 'NOT_CAPTAIN' } }])
    const user = userEvent.setup()
    renderRoute('/play/KDMZ')
    const input = await screen.findByLabelText('Ponle nombre a tu equipo')
    await user.clear(input)
    await user.type(input, 'Los Elotes')
    await user.click(screen.getByRole('button', { name: 'Guardar nombre' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Solo el capitán del equipo puede cambiarle el nombre.')
  })

  it('AC-66 / AC-65 los demás ven el nombre actual, que cambia en vivo con el snapshot', async () => {
    setup(CARLOS)
    renderRoute('/play/KDMZ')
    const panel = await screen.findByRole('region', { name: 'Tu equipo' })
    expect(panel).toHaveTextContent('Los Tlacoyos')
    expect(panel).toHaveTextContent('Ana es el capitán y elige el nombre del equipo.')
    expect(screen.queryByLabelText('Ponle nombre a tu equipo')).toBeNull()
    act(() => lastClient().connect())
    act(() => lastClient().emit('/topic/rooms/KDMZ/state', renamed('Los Chilaquiles', 9)))
    expect(panel).toHaveTextContent('Los Chilaquiles')
  })

  it('fuera de LOBBY ya no se muestra el panel del capitán', async () => {
    session.setPlayerToken('KDMZ', 'pt')
    mockFetch([
      { path: '/api/rooms/KDMZ/players/me', body: PLAYER_A },
      { path: '/api/rooms/KDMZ', body: faceOffState() },
    ])
    renderRoute('/play/KDMZ')
    await screen.findByText('Algo que se lleva a la playa')
    expect(screen.queryByText(/Eres el capitán/)).toBeNull()
  })

  it('AC-66 en /join los equipos muestran su nombre actual, su color y su capitán', async () => {
    const state = lobbyState()
    mockFetch([{ path: '/api/rooms/KDMZ', body: { ...state, teams: [state.teams[0], { ...state.teams[1], captainId: null, players: [] }] } }])
    renderRoute('/join/KDMZ')
    const tlacoyos = await screen.findByRole('button', { name: 'Los Tlacoyos' })
    expect(tlacoyos.getAttribute('style')).toContain('var(--cm-team-a)')
    expect(tlacoyos).toHaveAccessibleDescription('Capitán: Ana')
    expect(screen.getByRole('button', { name: 'Las Gorditas' })).toHaveAccessibleDescription(
      'El primero en unirse será el capitán y le pondrá nombre',
    )
  })
})
