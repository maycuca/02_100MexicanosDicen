import { afterEach, describe, expect, it, vi } from 'vitest'
import { mockFetch } from '../../test/fetchMock'
import { ApiError, NETWORK_ERROR_MESSAGE, api, errorMessage } from './client'

describe('cliente REST (contrato v0.2.0)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('envía X-Presenter-Token y el cuerpo JSON de assignControl', async () => {
    const { calls } = mockFetch([{ method: 'POST', path: '/api/rooms/KDMZ/rounds/current/control', body: { version: 2 } }])
    await api.assignControl('KDMZ', 'tok', 'B')
    expect(calls[0]).toMatchObject({ method: 'POST', headers: { 'X-Presenter-Token': 'tok' }, body: { team: 'B' } })
  })

  it('arma las rutas de revelar y getMe', async () => {
    const { calls } = mockFetch([
      { method: 'POST', path: /reveal$/, body: {} },
      { path: '/api/rooms/KDMZ/players/me', body: {} },
    ])
    await api.revealAnswer('KDMZ', 'tok', 42)
    await api.getMe('KDMZ', 'pt')
    expect(calls[0].path).toBe('/api/rooms/KDMZ/rounds/current/answers/42/reveal')
    expect(calls[1].headers['X-Player-Token']).toBe('pt')
  })

  it('AC-66 renameTeam hace PUT con X-Player-Token y el nombre (spec 003)', async () => {
    const { calls } = mockFetch([{ method: 'PUT', path: '/api/rooms/KDMZ/teams/B/name', body: { version: 4 } }])
    await api.renameTeam('KDMZ', 'pt', 'B', 'Las Gorditas')
    expect(calls[0]).toMatchObject({ method: 'PUT', headers: { 'X-Player-Token': 'pt' }, body: { name: 'Las Gorditas' } })
  })

  it.each([
    ['NOT_CAPTAIN', 403, 'Solo el capitán'],
    ['TEAM_NAME_TAKEN', 409, 'El otro equipo ya usa ese nombre'],
  ])('AC-64 traduce %s de renameTeam', async (code, status, text) => {
    mockFetch([{ method: 'PUT', path: /.*/, status, body: { status, code } }])
    const err = await api.renameTeam('KDMZ', 'pt', 'A', 'Otro').catch((e: unknown) => e)
    expect((err as ApiError).message).toContain(text)
  })

  it.each([
    ['NAME_TAKEN', 409, 'Ya hay alguien con ese nombre'],
    ['ROOM_NOT_IN_LOBBY', 409, 'ya empezó'],
    ['CLAIM_USED', 409, 'ya se usó'],
    ['ROOM_NOT_FOUND', 404, 'No existe una sala'],
  ])('traduce %s a un mensaje en español', async (code, status, text) => {
    mockFetch([{ method: 'POST', path: /.*/, status, body: { status, code } }])
    const err = await api.joinRoom('KDMZ', { name: 'Ana', team: 'A' }).catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).code).toBe(code)
    expect((err as ApiError).message).toContain(text)
  })

  it('usa un mensaje por status si no hay código conocido y otro para errores de red', async () => {
    mockFetch([{ path: /.*/, status: 403, body: {} }])
    const err = await api.getPresenterView('KDMZ', 'x').catch((e: unknown) => e)
    expect(errorMessage(err)).toBe('No tienes permiso para hacer esto en esta sala.')
    expect(errorMessage(new TypeError('Failed to fetch'))).toBe(NETWORK_ERROR_MESSAGE)
  })
})
