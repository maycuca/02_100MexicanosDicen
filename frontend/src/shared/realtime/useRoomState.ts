import { useCallback, useEffect, useRef, useState } from 'react'
import type { Client, IMessage } from '@stomp/stompjs'
import { api, ApiError, type PresenterRoomState, type RoomState } from '../api/client'
import { createStompClient, type Credentials } from './stompClient'
import { destinations, type GameEvent, type RealtimeError } from './messages'

/** Sin credenciales no hay STOMP; se consulta el snapshot por REST cada tanto. */
export const POLL_MS = 3000

export type RoomStateHook<S extends RoomState> = {
  state: S | null
  /** true mientras la sesión STOMP está activa. */
  connected: boolean
  /** Error al cargar el snapshot por REST (p. ej. ROOM_NOT_FOUND, 401/403). */
  loadError: ApiError | null
  /** Último evento de /topic/rooms/{code}/events (animaciones). */
  lastEvent: GameEvent | null
  /** Último error de /user/queue/errors. */
  lastError: RealtimeError | null
  clearError: () => void
  /** Aplica un snapshot (p. ej. la respuesta REST de una acción) respetando `version`. */
  applySnapshot: (s: S) => void
  /** Envía un mensaje STOMP; devuelve false si no hay conexión. */
  publish: (destination: string, body?: unknown) => boolean
}

type EventHandler = (event: GameEvent) => void

type Options =
  | { view: 'presenter'; presenterToken: string | null; onEvent?: EventHandler }
  | { view?: 'public'; credentials: Credentials | null; onEvent?: EventHandler }

/** Validación mínima del payload antes de aceptarlo como snapshot. */
export function isRoomState(value: unknown): value is RoomState {
  if (typeof value !== 'object' || value === null) return false
  const v = value as Partial<RoomState>
  return typeof v.version === 'number' && typeof v.status === 'string' && Array.isArray(v.teams)
}

/** Devuelve `next` salvo que sea más viejo que `prev` (asyncapi: se descartan versiones menores). */
export function newest<S extends RoomState>(prev: S | null, next: S): S {
  return prev && next.version < prev.version ? prev : next
}

function parse(message: IMessage): unknown {
  try {
    return JSON.parse(message.body)
  } catch {
    return null
  }
}

/**
 * Estado de la sala en vivo (T304): snapshot inicial por REST + STOMP.
 * - Vista pública: `/topic/rooms/{code}/state` (sin respuestas ocultas, AC-25).
 * - Vista de presentador: `/topic/rooms/{code}/presenter` con `X-Presenter-Token`.
 * En cada (re)conexión se vuelve a pedir el snapshot por REST.
 */
export function useRoomState(
  code: string,
  opts: { view: 'presenter'; presenterToken: string | null; onEvent?: EventHandler },
): RoomStateHook<PresenterRoomState>
export function useRoomState(
  code: string,
  opts?: { view?: 'public'; credentials: Credentials | null; onEvent?: EventHandler },
): RoomStateHook<RoomState>
export function useRoomState(code: string, opts: Options = { credentials: null }): RoomStateHook<RoomState> {
  const presenterView = opts.view === 'presenter'
  const credentials: Credentials | null =
    opts.view === 'presenter' ? (opts.presenterToken ? { presenterToken: opts.presenterToken } : null) : opts.credentials
  const credKey = credentials ? ('playerToken' in credentials ? `p:${credentials.playerToken}` : `h:${credentials.presenterToken}`) : ''

  const [state, setState] = useState<RoomState | null>(null)
  const [connected, setConnected] = useState(false)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [lastEvent, setLastEvent] = useState<GameEvent | null>(null)
  const [lastError, setLastError] = useState<RealtimeError | null>(null)
  const clientRef = useRef<Client | null>(null)
  const onEventRef = useRef<EventHandler | undefined>(opts.onEvent)
  useEffect(() => {
    onEventRef.current = opts.onEvent
  })

  const applySnapshot = useCallback((s: RoomState) => {
    if (isRoomState(s)) setState((prev) => newest(prev, s))
  }, [])

  useEffect(() => {
    if (!code) return
    // En la vista de presentador sin token no hay nada que pedir.
    if (presenterView && !credentials) return
    let disposed = false

    const load = () => {
      const req =
        presenterView && credentials && 'presenterToken' in credentials
          ? api.getPresenterView(code, credentials.presenterToken)
          : api.getRoom(code)
      req
        .then((s) => {
          if (disposed) return
          setLoadError(null)
          applySnapshot(s)
        })
        .catch((err: unknown) => {
          if (disposed) return
          // Los errores de red se reintentan solos; solo se guardan los del contrato.
          if (err instanceof ApiError) setLoadError(err)
        })
    }

    load()

    if (!credentials) {
      const timer = setInterval(load, POLL_MS)
      return () => {
        disposed = true
        clearInterval(timer)
      }
    }

    const client = createStompClient(credentials)
    clientRef.current = client
    client.onConnect = () => {
      if (disposed) return
      const stateTopic = presenterView ? destinations.presenter(code) : destinations.state(code)
      client.subscribe(stateTopic, (m) => {
        const s = parse(m)
        if (isRoomState(s)) applySnapshot(s)
      })
      client.subscribe(destinations.events(code), (m) => {
        const e = parse(m) as GameEvent | null
        if (e && typeof e.type === 'string') {
          setLastEvent(e)
          onEventRef.current?.(e)
        }
      })
      client.subscribe(destinations.errors, (m) => {
        const e = parse(m) as RealtimeError | null
        if (e && typeof e.code === 'string') setLastError(e)
      })
      setConnected(true)
      // Tras (re)conectar: snapshot por REST y luego solo versiones mayores.
      load()
    }
    client.onWebSocketClose = () => {
      if (!disposed) setConnected(false)
    }
    client.onStompError = () => {
      if (!disposed) setConnected(false)
    }
    client.activate()

    return () => {
      disposed = true
      clientRef.current = null
      setConnected(false)
      void client.deactivate()
    }
    // credKey resume `credentials`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code, presenterView, credKey, applySnapshot])

  const publish = useCallback((destination: string, body?: unknown) => {
    const client = clientRef.current
    if (!client || !client.connected) return false
    client.publish({ destination, body: body === undefined ? '{}' : JSON.stringify(body) })
    return true
  }, [])

  const clearError = useCallback(() => setLastError(null), [])

  return { state, connected, loadError, lastEvent, lastError, clearError, applySnapshot, publish }
}
