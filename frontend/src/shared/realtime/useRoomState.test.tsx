import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFakeStompClient, fakeClients, lastClient } from '../../test/fakeStomp'
import { mockFetch } from '../../test/fetchMock'
import { faceOffState, lobbyState, presenterFaceOff } from '../../test/fixtures'
import { POLL_MS, newest, useRoomState } from './useRoomState'

vi.mock('./stompClient', () => ({ createStompClient: (c: never) => createFakeStompClient(c) }))

describe('useRoomState (T304)', () => {
  beforeEach(() => {
    fakeClients.length = 0
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('carga el snapshot por REST y se suscribe a /state, /events y errores (AC-08)', async () => {
    const { calls } = mockFetch([{ path: '/api/rooms/KDMZ', body: lobbyState() }])
    const { result } = renderHook(() => useRoomState('KDMZ', { credentials: { playerToken: 'pt' } }))
    await waitFor(() => expect(result.current.state?.version).toBe(1))

    const client = lastClient()
    expect(client.credentials).toEqual({ playerToken: 'pt' })
    expect(client.activated).toBe(true)
    act(() => client.connect())
    expect([...client.subscriptions.keys()]).toEqual([
      '/topic/rooms/KDMZ/state',
      '/topic/rooms/KDMZ/events',
      '/user/queue/errors',
    ])
    expect(result.current.connected).toBe(true)
    // En cada (re)conexión vuelve a pedir el snapshot.
    await waitFor(() => expect(calls.filter((c) => c.path === '/api/rooms/KDMZ')).toHaveLength(2))
  })

  it('descarta snapshots con version menor', async () => {
    mockFetch([{ path: '/api/rooms/KDMZ', body: lobbyState({ version: 3 }) }])
    const { result } = renderHook(() => useRoomState('KDMZ', { credentials: { playerToken: 'pt' } }))
    await waitFor(() => expect(result.current.state?.version).toBe(3))
    const client = lastClient()
    act(() => client.connect())
    await waitFor(() => expect(result.current.state?.version).toBe(3))

    act(() => client.emit('/topic/rooms/KDMZ/state', faceOffState({ version: 2 })))
    expect(result.current.state?.status).toBe('LOBBY')
    act(() => client.emit('/topic/rooms/KDMZ/state', faceOffState({ version: 4 })))
    expect(result.current.state?.status).toBe('FACE_OFF')
    act(() => result.current.applySnapshot(lobbyState({ version: 1 })))
    expect(result.current.state?.version).toBe(4)
  })

  it('expone eventos y errores del usuario', async () => {
    mockFetch([{ path: '/api/rooms/KDMZ', body: lobbyState() }])
    const onEvent = vi.fn()
    const { result } = renderHook(() => useRoomState('KDMZ', { credentials: { playerToken: 'pt' }, onEvent }))
    const client = lastClient()
    act(() => client.connect())
    act(() => client.emit('/topic/rooms/KDMZ/events', { type: 'STRIKE', version: 7, strikes: 2 }))
    expect(result.current.lastEvent).toEqual({ type: 'STRIKE', version: 7, strikes: 2 })
    expect(onEvent).toHaveBeenCalledWith({ type: 'STRIKE', version: 7, strikes: 2 })
    act(() => client.emit('/user/queue/errors', { code: 'NOT_YOUR_TURN', message: 'no' }))
    expect(result.current.lastError?.code).toBe('NOT_YOUR_TURN')
    act(() => result.current.clearError())
    expect(result.current.lastError).toBeNull()
  })

  it('publica solo con conexión y marca desconexión al caer el socket', async () => {
    mockFetch([{ path: '/api/rooms/KDMZ', body: lobbyState() }])
    const { result } = renderHook(() => useRoomState('KDMZ', { credentials: { playerToken: 'pt' } }))
    const client = lastClient()
    expect(result.current.publish('/app/rooms/KDMZ/buzz')).toBe(false)
    act(() => client.connect())
    expect(result.current.publish('/app/rooms/KDMZ/buzz')).toBe(true)
    expect(client.published).toEqual([{ destination: '/app/rooms/KDMZ/buzz', body: '{}' }])
    act(() => client.drop())
    expect(result.current.connected).toBe(false)
  })

  it('la vista de presentador usa getPresenterView y /presenter con el token', async () => {
    const { calls } = mockFetch([{ path: '/api/rooms/KDMZ/presenter-view', body: presenterFaceOff() }])
    const { result } = renderHook(() => useRoomState('KDMZ', { view: 'presenter', presenterToken: 'tok' }))
    await waitFor(() => expect(result.current.state?.presenterBoard).toHaveLength(3))
    expect(calls[0].headers['X-Presenter-Token']).toBe('tok')
    const client = lastClient()
    expect(client.credentials).toEqual({ presenterToken: 'tok' })
    act(() => client.connect())
    expect(client.subscriptions.has('/topic/rooms/KDMZ/presenter')).toBe(true)
    expect(client.subscriptions.has('/topic/rooms/KDMZ/state')).toBe(false)
  })

  it('sin credenciales consulta el snapshot por REST periódicamente', async () => {
    vi.useFakeTimers()
    const { calls } = mockFetch([{ path: '/api/rooms/KDMZ', body: lobbyState() }])
    renderHook(() => useRoomState('KDMZ', { credentials: null }))
    expect(fakeClients).toHaveLength(0)
    expect(calls).toHaveLength(1)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS)
    })
    expect(calls).toHaveLength(2)
  })

  it('guarda el error del contrato al cargar (ROOM_NOT_FOUND)', async () => {
    mockFetch([{ path: '/api/rooms/ZZZZ', status: 404, body: { status: 404, code: 'ROOM_NOT_FOUND' } }])
    const { result } = renderHook(() => useRoomState('ZZZZ', { credentials: null }))
    await waitFor(() => expect(result.current.loadError?.code).toBe('ROOM_NOT_FOUND'))
  })

  it('newest conserva el snapshot más reciente', () => {
    const a = lobbyState({ version: 5 })
    expect(newest(a, lobbyState({ version: 4 }))).toBe(a)
    const b = lobbyState({ version: 6 })
    expect(newest(a, b)).toBe(b)
  })
})
