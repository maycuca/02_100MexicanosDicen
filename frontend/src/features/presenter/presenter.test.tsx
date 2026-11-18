import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFakeStompClient, fakeClients, lastClient } from '../../test/fakeStomp'
import { mockFetch } from '../../test/fetchMock'
import { PLAYER_B, lobbyState, presenterFaceOff } from '../../test/fixtures'
import { renderRoute } from '../../test/renderRoute'
import { session } from '../../shared/session/session'

vi.mock('../../shared/realtime/stompClient', () => ({ createStompClient: (c: never) => createFakeStompClient(c) }))

beforeEach(() => {
  fakeClients.length = 0
})
afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

describe('HostPage (T404)', () => {
  it('AC-30 canjea el claim una sola vez, guarda el token y limpia la URL', async () => {
    const { calls } = mockFetch([
      { method: 'POST', path: '/api/rooms/KDMZ/presenter/claim', body: { presenterToken: 'tok-cel', state: presenterFaceOff() } },
      { path: '/api/rooms/KDMZ/presenter-view', body: presenterFaceOff() },
    ])
    const { router } = renderRoute('/host/KDMZ?claim=abc')
    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(session.getPresenterToken('KDMZ')).toBe('tok-cel')
    const claims = calls.filter((c) => c.path.endsWith('/presenter/claim'))
    expect(claims).toHaveLength(1)
    expect(claims[0].body).toEqual({ claimToken: 'abc' })
    // Vista de presentador: respuestas ocultas visibles para él (sin buzzer aún no se revelan, spec 006 AC-100).
    expect(await screen.findByRole('button', { name: 'Revelar Toalla, 30 puntos' })).toBeDisabled()
    expect(lastClient().credentials).toEqual({ presenterToken: 'tok-cel' })
  })

  it.each([
    ['CLAIM_USED', 'ya se usó'],
    ['CLAIM_INVALID', 'ya no es válido'],
  ])('muestra el error %s', async (code, text) => {
    mockFetch([{ method: 'POST', path: /claim$/, status: code === 'CLAIM_USED' ? 409 : 403, body: { code } }])
    renderRoute(`/host/KDMZ?claim=${code}`)
    expect(await screen.findByRole('alert')).toHaveTextContent(text)
  })

  it('habilita los controles según el estado y aplica la respuesta de la acción', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const buzzedA = () => {
      const s = presenterFaceOff()
      s.round = { ...s.round!, activeTeam: 'A', faceOff: { ...s.round!.faceOff!, buzzWinner: s.round!.faceOff!.playerA, firstTeam: 'A' } }
      return s
    }
    const revealed = {
      ...buzzedA(),
      version: 6,
      presenterBoard: presenterFaceOff().presenterBoard!.map((a) => (a.answerId === 102 ? { ...a, revealed: true } : a)),
    }
    const { calls } = mockFetch([
      { path: '/api/rooms/KDMZ/presenter-view', body: buzzedA() },
      { method: 'POST', path: '/api/rooms/KDMZ/rounds/current/answers/102/reveal', body: revealed },
    ])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    await user.click(await screen.findByRole('button', { name: 'Revelar Toalla, 30 puntos' }))
    expect(await screen.findByRole('button', { name: 'Toalla, 30 puntos, revelada' })).toBeDisabled()
    expect(calls.at(-1)?.headers['X-Presenter-Token']).toBe('tok')

    // Spec 006 (AC-102): el cara a cara se resuelve solo; ya no hay "Asignar control".
    expect(screen.queryByRole('group', { name: 'Asignar control' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Falló Los Tlacoyos · pasa el turno' })).toBeEnabled()
    expect(screen.queryByRole('button', { name: 'Iniciar partida' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Siguiente ronda' })).toBeNull()
  })

  it('AC-10 en LOBBY solo deja iniciar con jugadores en ambos equipos', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const empty = lobbyState({ teams: [lobbyState().teams[0], { ...lobbyState().teams[1], players: [] }] })
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: empty }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('button', { name: 'Iniciar partida' })).toBeDisabled()
    act(() => lastClient().connect())
    act(() => lastClient().emit('/topic/rooms/KDMZ/presenter', lobbyState({ version: 2 })))
    expect(screen.getByRole('button', { name: 'Iniciar partida' })).toBeEnabled()
  })

  it('AC-23 en GAME_OVER ofrece la revancha', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: lobbyState({ status: 'GAME_OVER', winner: 'TIE' }) }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('button', { name: 'Revancha' })).toBeEnabled()
    expect(screen.getByText('¡Empate!')).toBeInTheDocument()
  })
})

describe('HostPage: equipo en turno y sonido (T714)', () => {
  function buzzed(overrides: Parameters<typeof presenterFaceOff>[0] = {}) {
    const s = presenterFaceOff({ version: 7, ...overrides })
    s.round = { ...s.round!, activeTeam: 'B', faceOff: { ...s.round!.faceOff!, buzzWinner: PLAYER_B.id } }
    return s
  }

  it('AC-39 · spec 005 AC-86 en el cara a cara muestra el equipo en turno y "Falló <equipo> · pasa el turno"', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const { calls } = mockFetch([
      { path: '/api/rooms/KDMZ/presenter-view', body: buzzed() },
      { method: 'POST', path: '/api/rooms/KDMZ/rounds/current/strike', body: buzzed({ version: 8 }) },
    ])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    expect(await screen.findByText('Las Gorditas', { selector: '.turn-banner strong' })).toBeInTheDocument()
    expect(screen.getByText(/En turno:/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /X para/ })).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Falló Las Gorditas · pasa el turno' }))
    expect(calls.at(-1)?.path).toBe('/api/rooms/KDMZ/rounds/current/strike')
  })

  it('AC-45 en PLAYING el botón dice "X para <equipo>"', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const s = buzzed({ status: 'PLAYING' })
    s.round = { ...s.round!, controllingTeam: 'B' }
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: s }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('button', { name: '✕ X para Las Gorditas' })).toBeEnabled()
  })

  it('AC-45 sin equipo en turno el botón de X está deshabilitado', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: presenterFaceOff() }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('button', { name: /sin equipo en turno/ })).toBeDisabled()
    expect(screen.getByText('Ningún equipo en turno')).toBeInTheDocument()
  })

  it('AC-40 muestra NO_ACTIVE_TEAM en español', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    mockFetch([
      { path: '/api/rooms/KDMZ/presenter-view', body: buzzed() },
      { method: 'POST', path: '/api/rooms/KDMZ/rounds/current/strike', status: 409, body: { status: 409, code: 'NO_ACTIVE_TEAM' } },
    ])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    await user.click(await screen.findByRole('button', { name: 'Falló Las Gorditas · pasa el turno' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Ningún equipo está en turno')
  })

  it('AC-50 el presentador ve la cuenta regresiva', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const s = buzzed()
    s.round!.turnDeadline = new Date(Date.now() + 9_000).toISOString()
    s.round!.timerRunning = true
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: s }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('timer', { name: /Tiempo del turno: [89] segundos/ })).toBeInTheDocument()
  })

  it('AC-53 · AC-107 el switch "Sonido de la TV" silencia la TV con setSound', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const { calls } = mockFetch([
      { path: '/api/rooms/KDMZ/presenter-view', body: buzzed() },
      { method: 'POST', path: '/api/rooms/KDMZ/sound', body: buzzed({ version: 8, soundEnabled: false }) },
    ])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    const sw = await screen.findByRole('switch', { name: 'Sonido de la TV' })
    expect(sw).toBeChecked()
    expect(sw).toHaveAttribute('switch')
    await user.click(sw)
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Sonido de la TV' })).not.toBeChecked())
    const call = calls.find((c) => c.path === '/api/rooms/KDMZ/sound')
    expect(call?.body).toEqual({ enabled: false })
    expect(call?.headers['X-Presenter-Token']).toBe('tok')
  })
})

describe('HostPage: temporizador manual y menú principal (spec 005, T1011)', () => {
  function buzzed(round: Partial<NonNullable<ReturnType<typeof presenterFaceOff>['round']>> = {}, version = 7) {
    const s = presenterFaceOff({ version })
    s.round = { ...s.round!, activeTeam: 'B', faceOff: { ...s.round!.faceOff!, buzzWinner: PLAYER_B.id }, ...round }
    return s
  }

  it('AC-80 · AC-82 detenido y completo: "Iniciar" habilitado, Parar y Restablecer no; muestra 20 detenido', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const running = buzzed({ timerRunning: true, turnDeadline: new Date(Date.now() + 20_000).toISOString() }, 8)
    const { calls } = mockFetch([
      { path: '/api/rooms/KDMZ/presenter-view', body: buzzed() },
      { method: 'POST', path: '/api/rooms/KDMZ/rounds/current/timer', body: running },
    ])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    const group = await screen.findByRole('group', { name: 'Temporizador' })
    expect(screen.getByRole('timer', { name: 'Tiempo del turno: 20 segundos, detenido' })).toBeInTheDocument()
    expect(within(group).getByRole('button', { name: /Parar/ })).toBeDisabled()
    expect(within(group).getByRole('button', { name: /Restablecer/ })).toBeDisabled()

    await user.click(within(group).getByRole('button', { name: /Iniciar/ }))
    expect(calls.at(-1)?.body).toEqual({ action: 'START' })
    expect(calls.at(-1)?.headers['X-Presenter-Token']).toBe('tok')
    expect(await screen.findByRole('timer', { name: /Tiempo del turno: (19|20) segundos$/ })).toBeInTheDocument()
    // AC-106: solo iconos, con nombre accesible y title
    for (const b of within(group).getAllByRole('button')) {
      expect(b.textContent).toBe('')
      expect(b).toHaveAttribute('title', b.getAttribute('aria-label'))
    }
    expect(within(group).getByRole('button', { name: /Iniciar/ })).toBeDisabled()
    expect(within(group).getByRole('button', { name: /Parar/ })).toBeEnabled()
  })

  it('AC-80 con tiempo a medias dice "Reanudar"; Parar y Restablecer mandan PAUSE y RESET', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const { calls } = mockFetch([
      { path: '/api/rooms/KDMZ/presenter-view', body: buzzed({ timerRunning: true, turnDeadline: new Date(Date.now() + 9_000).toISOString(), timerRemainingMs: 20_000 }) },
      { method: 'POST', path: '/api/rooms/KDMZ/rounds/current/timer', body: buzzed({ timerRemainingMs: 9_000 }, 8) },
    ])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    const group = await screen.findByRole('group', { name: 'Temporizador' })
    await user.click(within(group).getByRole('button', { name: /Parar/ }))
    expect(calls.at(-1)?.body).toEqual({ action: 'PAUSE' })
    expect(await within(group).findByRole('button', { name: /Reanudar/ })).toBeEnabled()
    expect(screen.getByRole('timer', { name: 'Tiempo del turno: 9 segundos, detenido' })).toBeInTheDocument()
    await user.click(within(group).getByRole('button', { name: /Restablecer/ }))
    expect(calls.at(-1)?.body).toEqual({ action: 'RESET' })
  })

  it('spec 007 AC-112 la tarjeta de la ronda dice "Revelados" en lugar de "Bote"', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: buzzed({ pot: 40 }) }])
    renderRoute('/host/KDMZ')
    expect((await screen.findByText('Revelados')).nextElementSibling).toHaveTextContent('40')
    expect(screen.queryByText(/Bote/)).toBeNull()
  })

  it('spec 008 AC-119 · AC-120 pregunta destacada y tres fichas (sin temporizador; control con el color del equipo)', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const s = buzzed({ pot: 40, controllingTeam: 'B', multiplier: 2 })
    s.status = 'PLAYING'
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: s }])
    renderRoute('/host/KDMZ')
    const card = await screen.findByRole('region', { name: 'Ronda actual' })
    expect(within(card).getByText('Algo que se lleva a la playa')).toHaveClass('host__question')
    expect(within(card).getByText(/^Pregunta · Ronda 1 de/)).toBeInTheDocument()
    const stat = (label: string) => within(card).getByText(label).nextElementSibling as HTMLElement
    expect(stat('Revelados')).toHaveTextContent('40')
    expect(stat('Multiplicador')).toHaveTextContent('×2')
    expect(within(card).queryByText('Temporizador')).toBeNull() // ajuste: sin ficha de temporizador
    expect(stat('Control')).toHaveTextContent('Las Gorditas')
    expect(stat('Control').style.color).toBe('var(--cm-team-b)')
  })

  it('spec 008 AC-121 · AC-122 "Cambiar pregunta" en el cara a cara llama a changeQuestion y muestra la nueva', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const hidden = (st: ReturnType<typeof buzzed>) => ({ ...st, presenterBoard: st.presenterBoard!.map((a) => ({ ...a, revealed: false })) })
    const next = hidden(buzzed({ question: 'Algo que se lleva al cine', activeTeam: null }, 8))
    const { calls } = mockFetch([
      { path: '/api/rooms/KDMZ/presenter-view', body: hidden(buzzed()) },
      { method: 'POST', path: '/api/rooms/KDMZ/rounds/current/question', body: next },
    ])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    const change = await screen.findByRole('button', { name: '↻ Cambiar pregunta' })
    expect(change).toBeEnabled()
    await user.click(change)
    expect(calls.at(-1)).toMatchObject({ method: 'POST', path: '/api/rooms/KDMZ/rounds/current/question', headers: { 'X-Presenter-Token': 'tok' } })
    expect(await screen.findByText('Algo que se lleva al cine')).toBeInTheDocument()
  })

  it('spec 008 AC-121 con una respuesta revelada o fuera del cara a cara no se puede cambiar la pregunta', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const s = buzzed()
    s.status = 'PLAYING'
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: s }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('button', { name: '↻ Cambiar pregunta' })).toBeDisabled()
    expect(screen.getByText('Solo se puede cambiar en el cara a cara, antes de revelar respuestas.')).toBeInTheDocument()
  })

  it('AC-80 sin temporizador (turnSeconds 0) no hay botones de temporizador', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const s = buzzed()
    s.turnSeconds = 0
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: s }])
    renderRoute('/host/KDMZ')
    await screen.findByText(/En turno:/)
    expect(screen.queryByRole('group', { name: 'Temporizador' })).toBeNull()
    expect(screen.queryByRole('timer')).toBeNull()
  })

  it('AC-90 "Menú principal" pide confirmación; Cancelar no cierra nada', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const { calls } = mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: buzzed() }])
    const user = userEvent.setup()
    renderRoute('/host/KDMZ')
    await screen.findByText(/En turno:/) // el botón de la vista "Cargando…" se reemplaza al llegar el estado
    await user.click(screen.getByRole('button', { name: 'Menú principal' }))
    const dialog = screen.getByRole('dialog', { name: '¿Regresar al menú principal?' })
    expect(within(dialog).getByRole('button', { name: 'Cancelar' })).toHaveFocus()
    expect(dialog).toHaveTextContent('Se cerrará la sala y el juego se reiniciará')
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false)
  })

  it('AC-91 · spec 007 AC-113 "Sí, regresar" cierra la sala (DELETE), borra las sesiones y muestra "¡Gracias por jugar!"', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    const { calls, fn } = mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: buzzed() }])
    fn.mockImplementation(async (input: RequestInfo | URL, init: RequestInit = {}) => {
      const path = String(input)
      calls.push({ method: init.method ?? 'GET', path, headers: (init.headers ?? {}) as Record<string, string>, body: undefined })
      if (init.method === 'DELETE') return new Response(null, { status: 204 })
      return new Response(JSON.stringify(buzzed()), { status: 200 })
    })
    const user = userEvent.setup()
    const { router } = renderRoute('/host/KDMZ')
    await screen.findByText(/En turno:/) // el botón de la vista "Cargando…" se reemplaza al llegar el estado
    await user.click(screen.getByRole('button', { name: 'Menú principal' }))
    await user.click(screen.getByRole('button', { name: 'Sí, regresar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/gracias'))
    expect(await screen.findByRole('heading', { name: '¡Gracias por jugar!' })).toBeInTheDocument()
    const del = calls.find((c) => c.method === 'DELETE')
    expect(del?.path).toBe('/api/rooms/KDMZ')
    expect(del?.headers['X-Presenter-Token']).toBe('tok')
    expect(session.getPresenterToken('KDMZ')).toBeNull()
  })
})

describe('HostPage: cara a cara automático (spec 006, T1111)', () => {
  function faceOffWith(round: Partial<NonNullable<ReturnType<typeof presenterFaceOff>['round']>>) {
    const s = presenterFaceOff({ version: 9 })
    s.round = { ...s.round!, ...round, faceOff: { ...s.round!.faceOff!, ...(round.faceOff ?? {}) } }
    return s
  }

  it('AC-100 · AC-102 sin buzzer no se puede revelar', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: presenterFaceOff() }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('button', { name: 'Revelar Toalla, 30 puntos' })).toBeDisabled()
  })

  it('AC-102 primer intento: "Falló <equipo> · pasa el turno"', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    mockFetch([{
      path: '/api/rooms/KDMZ/presenter-view',
      body: faceOffWith({ activeTeam: 'B', faceOff: { playerA: PLAYER_B.id, playerB: PLAYER_B.id, buzzWinner: PLAYER_B.id, firstTeam: 'B', firstAnswerPosition: null } }),
    }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByRole('button', { name: 'Falló Las Gorditas · pasa el turno' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Revelar Toalla, 30 puntos' })).toBeEnabled()
  })

  it('AC-102 segundo intento: pista "abrió la #N" y "Falló <equipo> · termina el cara a cara"', async () => {
    session.setPresenterToken('KDMZ', 'tok')
    mockFetch([{
      path: '/api/rooms/KDMZ/presenter-view',
      body: faceOffWith({ activeTeam: 'A', faceOff: { playerA: PLAYER_B.id, playerB: PLAYER_B.id, buzzWinner: PLAYER_B.id, firstTeam: 'B', firstAnswerPosition: 3 } }),
    }])
    renderRoute('/host/KDMZ')
    expect(await screen.findByText('Las Gorditas abrió la #3: Los Tlacoyos necesita una más alta')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Falló Los Tlacoyos · termina el cara a cara' })).toBeEnabled()
    expect(screen.getByText('Si falla, el control es de Las Gorditas.')).toBeInTheDocument()
  })
})
