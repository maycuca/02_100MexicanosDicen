import type { Client, IMessage } from '@stomp/stompjs'
import type { Credentials } from '../shared/realtime/stompClient'

type Handler = (m: IMessage) => void

/** Cliente STOMP falso para pruebas: registra suscripciones y permite emitir mensajes. */
export class FakeStompClient {
  credentials: Credentials
  connected = false
  activated = false
  subscriptions = new Map<string, Handler>()
  published: { destination: string; body?: string }[] = []
  onConnect: () => void = () => {}
  onWebSocketClose: () => void = () => {}
  onStompError: () => void = () => {}

  constructor(credentials: Credentials) {
    this.credentials = credentials
  }

  activate() {
    this.activated = true
  }

  async deactivate() {
    this.activated = false
    this.connected = false
  }

  subscribe(destination: string, cb: Handler) {
    this.subscriptions.set(destination, cb)
    return { id: destination, unsubscribe: () => this.subscriptions.delete(destination) }
  }

  publish(params: { destination: string; body?: string }) {
    this.published.push(params)
  }

  /** Simula el CONNECTED del broker. */
  connect() {
    this.connected = true
    this.subscriptions.clear()
    this.onConnect()
  }

  drop() {
    this.connected = false
    this.onWebSocketClose()
  }

  emit(destination: string, payload: unknown) {
    const cb = this.subscriptions.get(destination)
    if (!cb) throw new Error(`sin suscripción a ${destination}`)
    cb({ body: JSON.stringify(payload) } as IMessage)
  }
}

export const fakeClients: FakeStompClient[] = []

export function createFakeStompClient(credentials: Credentials): Client {
  const c = new FakeStompClient(credentials)
  fakeClients.push(c)
  return c as unknown as Client
}

export const lastClient = () => fakeClients[fakeClients.length - 1]
