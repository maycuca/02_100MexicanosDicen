import type { components } from '../api/schema'

// Payloads de asyncapi.yaml; desde el contrato v0.3.0 se generan desde openapi.yaml.
export type GameEvent = components['schemas']['GameEvent']
export type GameEventType = GameEvent['type']
/** /user/queue/errors */
export type RealtimeError = components['schemas']['StompError']

export const destinations = {
  state: (code: string) => `/topic/rooms/${code}/state`,
  presenter: (code: string) => `/topic/rooms/${code}/presenter`,
  events: (code: string) => `/topic/rooms/${code}/events`,
  buzz: (code: string) => `/app/rooms/${code}/buzz`,
  errors: '/user/queue/errors',
} as const
