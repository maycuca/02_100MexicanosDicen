import { Client } from '@stomp/stompjs'

export type Credentials = { playerToken: string } | { presenterToken: string }

/** Cliente STOMP según asyncapi.yaml. La URL usa el mismo origen (proxy de Vite). */
export function createStompClient(credentials: Credentials): Client {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const connectHeaders: Record<string, string> =
    'playerToken' in credentials
      ? { 'X-Player-Token': credentials.playerToken }
      : { 'X-Presenter-Token': credentials.presenterToken }

  return new Client({
    brokerURL: `${protocol}://${window.location.host}/ws`,
    connectHeaders,
    reconnectDelay: 2000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  })
}
