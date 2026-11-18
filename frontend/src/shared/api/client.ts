import type { components } from './schema'
import { observeServerDate } from './serverClock'

// Tipos del contrato (openapi.yaml v0.5.0). Nunca se escriben a mano: vienen de schema.d.ts.
export type Schemas = components['schemas']
export type NetworkInfo = Schemas['NetworkInfo']
export type RoomCode = Schemas['RoomCode']
export type TeamId = Schemas['TeamId']
export type RoomStatus = Schemas['RoomStatus']
export type RoomState = Schemas['RoomState']
export type PresenterRoomState = Schemas['PresenterRoomState']
export type RoundState = Schemas['RoundState']
export type BoardSlot = Schemas['BoardSlot']
export type PresenterBoardSlot = Schemas['PresenterBoardSlot']
export type Team = Schemas['Team']
export type Player = Schemas['Player']
export type Question = Schemas['Question']
export type Problem = Schemas['Problem']
export type CreateRoomRequest = Schemas['CreateRoomRequest']
export type CreateRoomResponse = Schemas['CreateRoomResponse']
export type ClaimPresenterResponse = Schemas['ClaimPresenterResponse']
export type PresenterClaim = Schemas['PresenterClaim']
export type JoinRoomRequest = Schemas['JoinRoomRequest']
export type JoinRoomResponse = Schemas['JoinRoomResponse']
export type SetSoundRequest = Schemas['SetSoundRequest']
export type RenameTeamRequest = Schemas['RenameTeamRequest']
export type TimerRequest = Schemas['TimerRequest']
export type TimerAction = TimerRequest['action']

/** Mensajes en español por `problem.code` (plan.md D7). */
const MESSAGES: Record<string, string> = {
  ROOM_NOT_FOUND: 'No existe una sala con ese código. Revisa el código o escanea el QR de nuevo.',
  CLAIM_USED: 'Este QR de presentador ya se usó. Pide en la pantalla un QR de presentador nuevo.',
  CLAIM_INVALID: 'Este QR de presentador ya no es válido. Pide en la pantalla un QR de presentador nuevo.',
  NAME_TAKEN: 'Ya hay alguien con ese nombre en la sala. Elige otro.',
  ROOM_NOT_IN_LOBBY: 'La partida ya empezó; ya no es posible unirse.',
  INVALID_STATE: 'Esa acción no está permitida en este momento del juego.',
  TEAM_EMPTY: 'Cada equipo necesita al menos un jugador para iniciar.',
  NOT_YOUR_TURN: 'No es tu turno.',
  NOT_ENOUGH_QUESTIONS: 'No hay suficientes preguntas para ese número de rondas.',
  ANSWER_NOT_FOUND: 'Esa respuesta no pertenece a la pregunta actual.',
  NO_ACTIVE_TEAM: 'Ningún equipo está en turno: espera a que alguien gane el buzzer para marcar la X.',
  NOT_IMPLEMENTED: 'El servidor todavía no implementa esta función.',
  NOT_CAPTAIN: 'Solo el capitán del equipo puede cambiarle el nombre.',
  TEAM_NAME_TAKEN: 'El otro equipo ya usa ese nombre. Elige otro.',
  TIMER_DISABLED: 'Esta sala no tiene temporizador.',
  NO_MORE_QUESTIONS: 'Ya no quedan preguntas nuevas para cambiar.',
}

const STATUS_MESSAGES: Record<number, string> = {
  400: 'Los datos enviados no son válidos.',
  401: 'Tu sesión no es válida. Vuelve a entrar.',
  403: 'No tienes permiso para hacer esto en esta sala.',
  404: 'No se encontró lo que buscas.',
  409: 'Esa acción no está permitida en este momento del juego.',
  500: 'Ocurrió un error en el servidor del juego. Intenta de nuevo.',
  501: MESSAGES.NOT_IMPLEMENTED,
  502: 'El servidor del juego no responde. Revisa que esté encendido.',
  503: 'El servidor del juego no responde. Revisa que esté encendido.',
  504: 'El servidor del juego no responde. Revisa que esté encendido.',
}

export const NETWORK_ERROR_MESSAGE = 'No se pudo conectar con el servidor. Revisa que estés en la misma red Wi-Fi.'

/** Mensaje en español para un código de error (REST o STOMP). */
export function messageForCode(code: string | undefined, fallback?: string): string {
  return (code && MESSAGES[code]) || fallback || 'Ocurrió un error inesperado.'
}

/** Error con el ProblemDetail del contrato (código estable en `problem.code`). */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem

  constructor(status: number, problem: Problem) {
    super(messageForCode(problem.code, STATUS_MESSAGES[status] ?? problem.detail ?? problem.title ?? `HTTP ${status}`))
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  get code(): string | undefined {
    return this.problem.code
  }
}

/** Mensaje para mostrar al usuario a partir de cualquier error. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  return NETWORK_ERROR_MESSAGE
}

type Headers = Record<string, string>

async function request<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, opts: { body?: unknown; headers?: Headers } = {}): Promise<T> {
  const headers: Headers = { Accept: 'application/json, application/problem+json', ...opts.headers }
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  const sentAt = Date.now()
  const res = await fetch(path, {
    method,
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  })
  // D17: el header `Date` corrige el desfase del reloj para la cuenta regresiva.
  observeServerDate(res.headers?.get('Date'), sentAt, Date.now())
  if (!res.ok) {
    const problem = (await res.json().catch(() => ({}))) as Problem
    throw new ApiError(res.status, problem)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

const room = (code: string) => `/api/rooms/${encodeURIComponent(code)}`
const presenter = (token: string): Headers => ({ 'X-Presenter-Token': token })

/** Una función por `operationId` de openapi.yaml. */
export const api = {
  getNetworkInfo: () => request<NetworkInfo>('GET', '/api/network-info'),

  /** Todos los campos son opcionales en el contrato; desde la spec 003 no se mandan nombres de equipo (AC-57). */
  createRoom: (body: Partial<CreateRoomRequest>) => request<CreateRoomResponse>('POST', '/api/rooms', { body }),
  getRoom: (code: string) => request<RoomState>('GET', room(code)),
  /** Spec 005 (AC-91): el presentador cierra la sala y regresa al menú principal. */
  closeRoom: (code: string, token: string) => request<void>('DELETE', room(code), { headers: presenter(token) }),
  getPresenterView: (code: string, token: string) =>
    request<PresenterRoomState>('GET', `${room(code)}/presenter-view`, { headers: presenter(token) }),
  claimPresenter: (code: string, claimToken: string) =>
    request<ClaimPresenterResponse>('POST', `${room(code)}/presenter/claim`, { body: { claimToken } }),
  regeneratePresenterClaim: (code: string, token: string) =>
    request<PresenterClaim>('POST', `${room(code)}/presenter/claim-token`, { headers: presenter(token) }),

  joinRoom: (code: string, body: JoinRoomRequest) => request<JoinRoomResponse>('POST', `${room(code)}/players`, { body }),
  getMe: (code: string, playerToken: string) =>
    request<Player>('GET', `${room(code)}/players/me`, { headers: { 'X-Player-Token': playerToken } }),
  /** El capitán nombra a su equipo durante el LOBBY (spec 003, AC-63, AC-66). */
  renameTeam: (code: string, playerToken: string, team: TeamId, name: string) =>
    request<RoomState>('PUT', `${room(code)}/teams/${team}/name`, {
      headers: { 'X-Player-Token': playerToken },
      body: { name } satisfies RenameTeamRequest,
    }),

  startGame: (code: string, token: string) =>
    request<PresenterRoomState>('POST', `${room(code)}/start`, { headers: presenter(token) }),
  revealAnswer: (code: string, token: string, answerId: number) =>
    request<PresenterRoomState>('POST', `${room(code)}/rounds/current/answers/${answerId}/reveal`, { headers: presenter(token) }),
  markStrike: (code: string, token: string) =>
    request<PresenterRoomState>('POST', `${room(code)}/rounds/current/strike`, { headers: presenter(token) }),
  /** Spec 008 (AC-122): cambiar la pregunta de la ronda. */
  changeQuestion: (code: string, token: string) =>
    request<PresenterRoomState>('POST', `${room(code)}/rounds/current/question`, { headers: presenter(token) }),
  /** Spec 005 (AC-80, AC-81): iniciar, parar o restablecer el temporizador del turno. */
  controlTimer: (code: string, token: string, action: TimerAction) =>
    request<PresenterRoomState>('POST', `${room(code)}/rounds/current/timer`, {
      headers: presenter(token),
      body: { action } satisfies TimerRequest,
    }),
  assignControl: (code: string, token: string, team: TeamId) =>
    request<PresenterRoomState>('POST', `${room(code)}/rounds/current/control`, { headers: presenter(token), body: { team } }),
  nextRound: (code: string, token: string) =>
    request<PresenterRoomState>('POST', `${room(code)}/next-round`, { headers: presenter(token) }),
  rematch: (code: string, token: string) =>
    request<PresenterRoomState>('POST', `${room(code)}/rematch`, { headers: presenter(token) }),

  setSound: (code: string, token: string, enabled: boolean) =>
    request<PresenterRoomState>('POST', `${room(code)}/sound`, { headers: presenter(token), body: { enabled } satisfies SetSoundRequest }),

  listQuestions: (token: string) => request<Question[]>('GET', '/api/questions', { headers: presenter(token) }),
}
