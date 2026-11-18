-- Spec 001 · data-model.md
CREATE TABLE question (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    text   TEXT    NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1))
);

CREATE TABLE answer (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    question_id INTEGER NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    text        TEXT    NOT NULL,
    points      INTEGER NOT NULL CHECK (points > 0),
    position    INTEGER NOT NULL CHECK (position BETWEEN 1 AND 8),
    UNIQUE (question_id, position)
);

CREATE INDEX idx_answer_question ON answer (question_id);

CREATE TABLE game_result (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    room_code    TEXT    NOT NULL,
    team_a_name  TEXT    NOT NULL,
    team_b_name  TEXT    NOT NULL,
    team_a_score INTEGER NOT NULL,
    team_b_score INTEGER NOT NULL,
    winner       TEXT    NOT NULL CHECK (winner IN ('A', 'B', 'TIE')),
    finished_at  TEXT    NOT NULL
);
