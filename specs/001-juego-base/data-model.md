# Modelo de datos — 001 Juego base

## Persistente (SQLite, Flyway)

### `question`
| Columna | Tipo | Notas |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | |
| text | TEXT NOT NULL | "Mencione algo que se lleva a la playa" |
| active | INTEGER NOT NULL DEFAULT 1 | 0/1 |

### `answer`
| Columna | Tipo | Notas |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | |
| question_id | INTEGER NOT NULL FK → question.id | |
| text | TEXT NOT NULL | |
| points | INTEGER NOT NULL | > 0 |
| position | INTEGER NOT NULL | 1 = la más popular; único por pregunta |

Invariante: una pregunta tiene de 3 a 8 respuestas y `SUM(points) ≤ 100` (RNF-5).

### `game_result` (historial)
| Columna | Tipo | Notas |
|---|---|---|
| id | INTEGER PK AUTOINCREMENT | |
| room_code | TEXT NOT NULL | |
| team_a_name / team_b_name | TEXT NOT NULL | |
| team_a_score / team_b_score | INTEGER NOT NULL | |
| winner | TEXT NOT NULL | `A`, `B` o `TIE` |
| finished_at | TEXT NOT NULL | ISO-8601 |

## En memoria (dominio `game.domain`)

```
Room
├── code: RoomCode (4 letras)
├── presenterToken: UUID
├── status: RoomStatus
├── settings: totalRounds, targetScore
├── teams: Map<TeamId, Team>
├── questions: List<QuestionSnapshot>   (elegidas al iniciar)
├── currentRound: Round?
├── version: long
└── lastActivity: Instant               (AC-28)

Team:   id (A|B), name, score, players (orden de llegada), nextFaceOffIndex
Player: id UUID, token UUID, name, team, connected
Round:  number, multiplier, question, slots[] (answerId, position, text, points, revealed),
        strikes 0..3, pot, controllingTeam?, faceOff (playerA, playerB, buzzWinner?, firstAnswerPosition?),
        stealResolved
```

### Reglas puras (`GameRules`)
- `multiplier(round, total)`: `total` → 3, `total-1` → 2, resto → 1 (AC-21).
- `pot += points` al revelar en `PLAYING`/`FACE_OFF` (AC-17); en `STEAL` no se suma (C-1).
- Una X en `FACE_OFF` no incrementa `strikes` (C-3).
- `strikes == 3` → `STEAL` (AC-18).
- `STEAL` + reveal → bote al rival; `STEAL` + strike → bote al equipo con control (AC-19).
- Al cerrar ronda: `score[winner] += pot * multiplier` (AC-20).
- Fin: alguien ≥ `targetScore` o `round == total` (AC-22).

### Transiciones válidas
| Desde | Comando | Hacia |
|---|---|---|
| LOBBY | start | FACE_OFF |
| FACE_OFF | buzz, reveal, strike | FACE_OFF |
| FACE_OFF | assignControl | PLAYING |
| PLAYING | reveal (quedan ocultas) / strike (< 3) | PLAYING |
| PLAYING | reveal (todas reveladas) | ROUND_END |
| PLAYING | strike (= 3) | STEAL |
| STEAL | reveal / strike | ROUND_END |
| ROUND_END | nextRound | FACE_OFF o GAME_OVER |
| GAME_OVER | rematch | LOBBY |

Cualquier otro comando → `GameException(INVALID_STATE)` → HTTP 409.
