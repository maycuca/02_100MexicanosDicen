import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFakeStompClient, fakeClients, lastClient } from '../../test/fakeStomp'
import { mockFetch, networkInfo } from '../../test/fetchMock'
import { faceOffState, lobbyState } from '../../test/fixtures'
import { renderRoute } from '../../test/renderRoute'
import { installFakeAudio, playedSounds } from '../../test/fakeAudio'
import { session } from '../../shared/session/session'

vi.mock('../../shared/realtime/stompClient', () => ({ createStompClient: (c: never) => createFakeStompClient(c) }))

beforeEach(() => {
  fakeClients.length = 0
  session.setPresenterToken('KDMZ', 'tv-token')
  session.setPresenterClaim('KDMZ', 'claim-1')
})
afterEach(() => {
  vi.unstubAllGlobals()
  localStorage.clear()
})

function setup(state = lobbyState()) {
  return mockFetch([
    { path: '/api/network-info', body: networkInfo },
    { path: '/api/rooms/KDMZ', body: state },
    { method: 'POST', path: '/api/rooms/KDMZ/presenter/claim-token', body: { claimToken: 'claim-2' } },
  ])
}

describe('pantalla de la TV (T402, T405, T403)', () => {
  it('AC-08 / AC-32 muestra jugadores en vivo por equipo y el presentador conectado', async () => {
    setup()
    renderRoute('/screen/KDMZ')
    expect(await screen.findByText('Ana')).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Jugadores de Las Gorditas' })).getByText('Beto')).toBeInTheDocument()
    // La TV usa la vista pública aunque tenga presenterToken.
    const client = lastClient()
    expect(client.credentials).toEqual({ presenterToken: 'tv-token' })
    act(() => client.connect())
    expect(client.subscriptions.has('/topic/rooms/KDMZ/state')).toBe(true)
    expect(client.subscriptions.has('/topic/rooms/KDMZ/presenter')).toBe(false)

    const state = lobbyState({ version: 2, presenterConnected: true })
    state.teams[0].players.push({ id: '33333333-3333-4333-8333-333333333333', name: 'Carla', team: 'A', connected: true })
    act(() => client.emit('/topic/rooms/KDMZ/state', state))
    expect(screen.getByText('Carla')).toBeInTheDocument()
    expect(screen.getByText('Presentador conectado ✓')).toBeInTheDocument()
  })

  it('AC-33 genera un QR de presentador nuevo', async () => {
    const { calls } = setup(lobbyState({ presenterConnected: true }))
    const user = userEvent.setup()
    renderRoute('/screen/KDMZ')
    await screen.findByText('Presentador conectado ✓')
    await user.click(screen.getByRole('button', { name: 'Nuevo QR de presentador' }))
    const presenter = screen.getByRole('region', { name: 'QR Presentador' })
    expect(await within(presenter).findByText('http://192.168.1.42:5173/host/KDMZ?claim=claim-2')).toBeInTheDocument()
    expect(session.getPresenterClaim('KDMZ')).toBe('claim-2')
    expect(calls.find((c) => c.path.endsWith('/claim-token'))?.headers['X-Presenter-Token']).toBe('tv-token')

    act(() => lastClient().connect())
    act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'PRESENTER_CLAIMED', version: 3 }))
    expect(screen.getByText('Presentador conectado ✓')).toBeInTheDocument()
  })

  it('AC-34 al salir de LOBBY se convierte en tablero sin respuestas ocultas (AC-25)', async () => {
    setup()
    renderRoute('/screen/KDMZ')
    await screen.findByText('Ana')
    act(() => lastClient().connect())
    act(() => lastClient().emit('/topic/rooms/KDMZ/state', faceOffState({ version: 9 })))

    expect(screen.getByRole('heading', { name: 'Algo que se lleva a la playa' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'QR Jugadores' })).toBeNull()
    const slots = screen.getByRole('list', { name: 'Respuestas' })
    expect(within(slots).getByText('Bloqueador')).toBeInTheDocument()
    expect(within(slots).getByLabelText('Respuesta 2 oculta')).toBeInTheDocument()
    expect(document.querySelector('.board__status')).toHaveTextContent('Cara a cara: Ana contra Beto')
    // Sin controles de juego en la TV.
    expect(screen.queryByRole('button', { name: /Revelar|Marcar X|Iniciar/ })).toBeNull()
  })

  it('anima la X al recibir STRIKE y muestra el ganador en GAME_OVER (AC-22)', async () => {
    setup(faceOffState())
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-A')
    expect(document.querySelector('.board__status')).toHaveTextContent('Cara a cara: Ana contra Beto')
    act(() => lastClient().connect())
    act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'STRIKE', version: 6, team: 'A', strikes: 1 }))
    expect(screen.getByRole('img', { name: 'X para Los Tlacoyos' })).toBeInTheDocument()

    const over = faceOffState({ version: 20, status: 'GAME_OVER', winner: 'B', round: null })
    over.teams[1].score = 320
    act(() => lastClient().emit('/topic/rooms/KDMZ/state', over))
    expect(screen.getByRole('heading', { name: '¡Gana Las Gorditas!' })).toBeInTheDocument()
  })
})

describe('TV: experiencia de juego (spec 002: T711, T712, T713)', () => {
  function playingState(overrides: Parameters<typeof faceOffState>[0] = {}) {
    const s = faceOffState({ status: 'PLAYING', version: 10, ...overrides })
    s.round = { ...s.round!, controllingTeam: 'B', activeTeam: 'B', strikes: 2, strikesByTeam: { A: 0, B: 2 } }
    return s
  }

  it('AC-37 resalta el equipo en turno con "EN TURNO" y atenúa al otro', async () => {
    setup(playingState())
    renderRoute('/screen/KDMZ')
    const b = await screen.findByTestId('team-panel-B')
    const a = screen.getByTestId('team-panel-A')
    expect(within(b).getByText('EN TURNO')).toBeInTheDocument()
    expect(b).toHaveClass('board__side--turn')
    expect(a).toHaveClass('board__side--dim')
    expect(within(a).queryByText('EN TURNO')).toBeNull()
    expect(screen.getByText('En turno: Las Gorditas')).toBeInTheDocument()
  })

  it('AC-37 con activeTeam null ningún equipo se resalta', async () => {
    setup(faceOffState())
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-A')
    expect(document.querySelector('.board__status')).toHaveTextContent('Cara a cara: Ana contra Beto')
    expect(screen.queryByText('EN TURNO')).toBeNull()
    for (const id of ['A', 'B']) {
      expect(screen.getByTestId(`team-panel-${id}`)).not.toHaveClass('board__side--turn')
      expect(screen.getByTestId(`team-panel-${id}`)).not.toHaveClass('board__side--dim')
    }
  })

  it('AC-44 muestra las X debajo de cada equipo según strikesByTeam', async () => {
    setup(playingState())
    renderRoute('/screen/KDMZ')
    expect(await screen.findByLabelText('X de Las Gorditas: 2')).toBeInTheDocument()
    expect(screen.getByLabelText('X de Los Tlacoyos: 0')).toBeInTheDocument()
    const bStrikes = screen.getByLabelText('X de Las Gorditas: 2')
    expect(bStrikes.querySelectorAll('.strike--on')).toHaveLength(2)
  })

  it('AC-44 la X gigante usa el color del equipo que la recibió', async () => {
    setup(playingState())
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-B')
    act(() => lastClient().connect())
    act(() => {
      // STRIKE y STEAL_STARTED juntos: la X no se pierde.
      lastClient().emit('/topic/rooms/KDMZ/events', { type: 'STRIKE', version: 11, team: 'B', strikes: 3 })
      lastClient().emit('/topic/rooms/KDMZ/events', { type: 'STEAL_STARTED', version: 11, team: 'A' })
    })
    const flash = screen.getByRole('img', { name: 'X para Las Gorditas' })
    expect(flash).toHaveAttribute('data-team', 'B')
    expect(flash.style.getPropertyValue('--flash-color')).toBe('var(--cm-team-b)')
    expect(flash.querySelectorAll('span')).toHaveLength(3)
  })

  it('AC-50 la TV muestra la cuenta regresiva de turnDeadline', async () => {
    const s = playingState()
    s.round!.turnDeadline = new Date(Date.now() + 15_000).toISOString()
    s.round!.timerRunning = true
    setup(s)
    renderRoute('/screen/KDMZ')
    expect(await screen.findByRole('timer', { name: /Tiempo del turno: 1[45] segundos$/ })).toBeInTheDocument()
  })

  it('spec 005 AC-82 con el temporizador detenido la TV muestra el número fijo', async () => {
    const s = playingState()
    s.round = { ...s.round!, timerRunning: false, turnDeadline: null, timerRemainingMs: 12_400 }
    setup(s)
    renderRoute('/screen/KDMZ')
    expect(await screen.findByRole('timer', { name: 'Tiempo del turno: 13 segundos, detenido' })).toBeInTheDocument()
  })

  it('spec 005 AC-87 los puntos de la ronda se muestran sin la etiqueta "Bote"', async () => {
    const s = playingState()
    s.round = { ...s.round!, pot: 70 }
    setup(s)
    renderRoute('/screen/KDMZ')
    expect(await screen.findByLabelText('Puntos de la ronda')).toHaveTextContent('70')
    expect(screen.queryByText(/^Bote$/i)).toBeNull()
  })

  it('spec 005 AC-88 el tablero no tiene botón de sonido ni "QR de presentador"', async () => {
    setup(playingState())
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-B')
    expect(screen.queryByRole('button', { name: /sonido/i })).toBeNull()
    expect(screen.queryByRole('button', { name: 'QR de presentador' })).toBeNull()
  })

  it('spec 005 AC-86 un fallo en el cara a cara muestra "¡Pasa a …!" y ninguna X gigante', async () => {
    const s = faceOffState({ version: 10 })
    s.round = { ...s.round!, activeTeam: 'B', faceOff: { ...s.round!.faceOff!, buzzWinner: s.round!.faceOff!.playerA } }
    setup(s)
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-B')
    act(() => lastClient().connect())
    act(() => {
      lastClient().emit('/topic/rooms/KDMZ/events', { type: 'FACE_OFF_MISS', version: 11, team: 'A' })
      lastClient().emit('/topic/rooms/KDMZ/events', { type: 'TURN_CHANGED', version: 11, team: 'B' })
    })
    expect(document.querySelector('.board__status')).toHaveTextContent('¡Pasa a Las Gorditas!')
    expect(screen.queryByRole('img', { name: /^X/ })).toBeNull()
  })

  it('spec 006 AC-103 en el segundo intento la TV dice "¡<equipo> debe superar la #N!"', async () => {
    const s = faceOffState({ version: 10 })
    s.round = { ...s.round!, activeTeam: 'B', faceOff: { ...s.round!.faceOff!, buzzWinner: s.round!.faceOff!.playerA, firstTeam: 'A', firstAnswerPosition: 3 } }
    setup(s)
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-B')
    expect(document.querySelector('.board__status')).toHaveTextContent('¡Las Gorditas debe superar la #3!')
  })

  it('spec 005 AC-91 · spec 007 AC-113 · AC-123 con ROOM_CLOSED la TV borra sus sesiones y vuelve al menú principal', async () => {
    setup(playingState())
    const { router } = renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-B')
    act(() => lastClient().connect())
    act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'ROOM_CLOSED', version: 11 }))
    expect(router.state.location.pathname).toBe('/')
    expect(await screen.findByRole('button', { name: /empezar/i })).toBeInTheDocument()
    expect(screen.queryByText('¡Gracias por jugar!')).toBeNull()
    expect(session.getPresenterToken('KDMZ')).toBeNull()
    expect(session.getPresenterClaim('KDMZ')).toBeNull()
  })

  it('spec 007 AC-117 en el cara a cara los nombres de los jugadores van con el color de su equipo', async () => {
    setup(faceOffState())
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-A')
    const players = [...document.querySelectorAll<HTMLElement>('.board__status .status-player')]
    expect(players.map((p) => [p.textContent, p.style.color])).toEqual([
      ['Ana', 'var(--cm-team-a)'],
      ['Beto', 'var(--cm-team-b)'],
    ])
    act(() => lastClient().connect())
    const buzzed = faceOffState({ version: 9 })
    buzzed.round = { ...buzzed.round!, activeTeam: 'B', faceOff: { ...buzzed.round!.faceOff!, buzzWinner: buzzed.round!.faceOff!.playerB } }
    act(() => lastClient().emit('/topic/rooms/KDMZ/state', buzzed))
    expect(document.querySelector('.board__status')).toHaveTextContent('¡Beto ganó el buzzer!')
    expect((document.querySelector('.board__status .status-player') as HTMLElement).style.color).toBe('var(--cm-team-b)')
  })

  it('spec 007 AC-110 la línea de estado pinta al equipo con su color y los puntos en dorado', async () => {
    const s = playingState({ status: 'ROUND_END', version: 12 })
    s.round = { ...s.round!, potWinner: 'B', pot: 70, multiplier: 2 }
    setup(s)
    renderRoute('/screen/KDMZ')
    await screen.findByTestId('team-panel-B')
    const status = document.querySelector('.board__status')!
    expect(status).toHaveTextContent('Fin de la ronda: Las Gorditas se lleva 140 puntos')
    const team = status.querySelector('.status-team') as HTMLElement
    expect(team).toHaveTextContent('Las Gorditas')
    expect(team.style.color).toBe('var(--cm-team-b)')
    expect(status.querySelector('.status-pts')).toHaveTextContent('140')
  })

  it('spec 007 AC-114 los resultados celebran: confeti, trofeo, ganador a color y marcador final', async () => {
    const over = lobbyState({ status: 'GAME_OVER', winner: 'B', version: 40, presenterConnected: true })
    over.teams = over.teams.map((t) => ({ ...t, score: t.id === 'B' ? 410 : 220 }))
    setup(over)
    renderRoute('/screen/KDMZ')
    const title = await screen.findByRole('heading', { name: '¡Gana Las Gorditas!' })
    expect((title.querySelector('span') as HTMLElement).style.color).toBe('var(--cm-team-b)')
    expect(document.querySelector('.confetti')).toBeInTheDocument()
    expect(document.querySelector('.game-over__trophy svg')).toBeInTheDocument()
    expect(screen.getByLabelText('Las Gorditas: 410 puntos')).toBeInTheDocument()
    expect(screen.getByText('★ CAMPEÓN ★')).toBeInTheDocument()
  })

  describe('sonido (AC-51, AC-52, AC-53)', () => {
    beforeEach(() => installFakeAudio())

    it('AC-51 · spec 010 AC-133 con el sonido desactivado (AJUSTES → Sonido: No) la TV no suena aunque se toque', async () => {
      localStorage.setItem('cm:sound', 'off')
      setup(playingState())
      const user = userEvent.setup()
      renderRoute('/screen/KDMZ')
      await screen.findByTestId('team-panel-B')
      await user.click(screen.getByTestId('team-panel-B'))
      act(() => lastClient().connect())
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'ANSWER_REVEALED', version: 11, position: 2 }))
      expect(playedSounds()).toEqual([])
    })

    it('spec 010 AC-133 sin preferencia guardada el sonido está activado: suena desde el primer toque', async () => {
      setup(playingState())
      const user = userEvent.setup()
      renderRoute('/screen/KDMZ')
      await screen.findByTestId('team-panel-B')
      await user.click(screen.getByTestId('team-panel-B')) // gesto: desbloquea el audio
      act(() => lastClient().connect())
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'ANSWER_REVEALED', version: 11, position: 2 }))
      expect(playedSounds()).toEqual(['reveal'])
    })

    it('AC-51 · spec 005 AC-88 · AC-89 con la preferencia activa, el primer toque reanuda el audio: júbilo al revelar, aviso al fallar el cara a cara', async () => {
      localStorage.setItem('cm:sound', 'on')
      setup(playingState())
      const user = userEvent.setup()
      renderRoute('/screen/KDMZ')
      await screen.findByTestId('team-panel-B')
      await user.click(screen.getByTestId('team-panel-B')) // gesto: desbloquea el audio
      act(() => lastClient().connect())
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'ANSWER_REVEALED', version: 11, position: 2 }))
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'FACE_OFF_MISS', version: 12, team: 'A' }))
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'STRIKE', version: 13, team: 'B', strikes: 3 }))
      expect(playedSounds()).toEqual(['reveal', 'miss', 'strike'])
    })

    it('AC-53 si el presentador silenció la TV (soundEnabled false) no suena', async () => {
      localStorage.setItem('cm:sound', 'on')
      setup(playingState({ soundEnabled: false }))
      const user = userEvent.setup()
      renderRoute('/screen/KDMZ')
      await screen.findByTestId('team-panel-B')
      await user.click(screen.getByTestId('team-panel-B'))
      act(() => lastClient().connect())
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'ANSWER_REVEALED', version: 11, position: 2 }))
      expect(playedSounds()).toEqual([])

      act(() => lastClient().emit('/topic/rooms/KDMZ/state', playingState({ version: 12, soundEnabled: true })))
      act(() => lastClient().emit('/topic/rooms/KDMZ/events', { type: 'ANSWER_REVEALED', version: 13, position: 3 }))
      expect(playedSounds()).toEqual(['reveal'])
    })
  })
})
