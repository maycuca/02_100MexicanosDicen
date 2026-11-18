import type { PresenterRoomState, RoomState } from '../shared/api/client'

export const PLAYER_A = { id: '11111111-1111-4111-8111-111111111111', name: 'Ana', team: 'A' as const, connected: true }
export const PLAYER_B = { id: '22222222-2222-4222-8222-222222222222', name: 'Beto', team: 'B' as const, connected: true }

export function lobbyState(overrides: Partial<RoomState> = {}): RoomState {
  return {
    code: 'KDMZ',
    status: 'LOBBY',
    presenterConnected: false,
    turnSeconds: 20,
    autoStrikeOnTimeout: true,
    soundEnabled: true,
    totalRounds: 5,
    targetScore: 300,
    teams: [
      { id: 'A', name: 'Los Tlacoyos', captainId: PLAYER_A.id, score: 0, players: [PLAYER_A] },
      { id: 'B', name: 'Las Gorditas', captainId: PLAYER_B.id, score: 0, players: [PLAYER_B] },
    ],
    round: null,
    winner: null,
    version: 1,
    ...overrides,
  }
}

export function faceOffState(overrides: Partial<RoomState> = {}): RoomState {
  return lobbyState({
    status: 'FACE_OFF',
    presenterConnected: true,
    version: 5,
    round: {
      number: 1,
      multiplier: 1,
      question: 'Algo que se lleva a la playa',
      strikes: 0,
      pot: 0,
      activeTeam: null,
      strikesByTeam: { A: 0, B: 0 },
      turnDeadline: null,
      timerRunning: false,
      timerRemainingMs: 20_000,
      controllingTeam: null,
      faceOff: { playerA: PLAYER_A.id, playerB: PLAYER_B.id, buzzWinner: null },
      board: [
        { position: 1, revealed: true, text: 'Bloqueador', points: 40 },
        { position: 2, revealed: false },
        { position: 3, revealed: false },
      ],
    },
    ...overrides,
  })
}

export function presenterFaceOff(overrides: Partial<PresenterRoomState> = {}): PresenterRoomState {
  return {
    ...faceOffState(),
    presenterBoard: [
      { answerId: 101, position: 1, revealed: true, text: 'Bloqueador', points: 40 },
      { answerId: 102, position: 2, revealed: false, text: 'Toalla', points: 30 },
      { answerId: 103, position: 3, revealed: false, text: 'Sombrilla', points: 20 },
    ],
    ...overrides,
  }
}
