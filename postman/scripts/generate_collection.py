"""Genera la colección Postman desde el contrato (T011, T206).

Uso: python3 postman/scripts/generate_collection.py   (desde la raíz del repo)
Mantener FLOW sincronizado con specs/001-juego-base/contracts/openapi.yaml (v0.5.0, incluye specs 002 y 003).

La colección es un flujo encadenado real que se ejecuta en orden contra el backend:
createRoom (sin nombres de equipo, spec 003) → claimPresenter → regeneratePresenterClaim → joinRoom ×3 →
renameTeam por los capitanes (spec 003) → startGame →
revealAnswer / assignControl / markStrike (robo) → nextRound … → GAME_OVER → rematch.
Cada paso guarda en variables de colección lo que necesita el siguiente (código, tokens,
ids de respuestas leídos de presenter-view, porque la pregunta es aleatoria — AC-11) y
verifica estado HTTP y forma del payload según el contrato.

Nota: el buzz es STOMP y Postman no lo envía; el flujo asume que el presentador puede
revelar / asignar control en FACE_OFF sin buzz previo (C-2, tabla de transiciones de data-model.md).
Desde la spec 002 (AC-40) una X en FACE_OFF sin buzz → 409 NO_ACTIVE_TEAM; el flujo lo verifica.
La sala del flujo se crea con turnSeconds = 0 para que ninguna X automática (AC-48) altere el orden.
El temporizador (TIMER_EXPIRED) y TURN_CHANGED/SOUND_TOGGLED/TEAM_RENAMED son STOMP: se prueban en la aceptación del backend.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# --------------------------------------------------------------------------- JS reutilizable

# Validaciones de forma (subset de los schemas de openapi.yaml), disponibles en cada script de prueba.
SHAPES = r"""
const TEAM = ['A', 'B'];
const STATUSES = ['LOBBY', 'FACE_OFF', 'PLAYING', 'STEAL', 'ROUND_END', 'GAME_OVER'];
function expectRoomState(s) {
  pm.expect(s.code, 'code').to.match(/^[A-HJ-NP-Z]{4}$/);
  pm.expect(STATUSES, 'status').to.include(s.status);
  pm.expect(s.presenterConnected, 'presenterConnected').to.be.a('boolean');
  pm.expect(s.totalRounds, 'totalRounds').to.be.a('number');
  pm.expect(s.targetScore, 'targetScore').to.be.a('number');
  pm.expect(s.version, 'version').to.be.a('number');
  // spec 002 (AC-46, AC-53)
  pm.expect(s.turnSeconds, 'turnSeconds').to.be.a('number');
  pm.expect(s.turnSeconds === 0 || (s.turnSeconds >= 10 && s.turnSeconds <= 60), 'turnSeconds 0 | 10–60').to.be.true;
  pm.expect(s.autoStrikeOnTimeout, 'autoStrikeOnTimeout').to.be.a('boolean');
  pm.expect(s.soundEnabled, 'soundEnabled').to.be.a('boolean');
  pm.expect(s.teams, 'teams').to.be.an('array').with.lengthOf(2);
  s.teams.forEach(t => {
    pm.expect(TEAM).to.include(t.id);
    pm.expect(t.name).to.be.a('string');
    pm.expect(t.score).to.be.a('number');
    pm.expect(t.players).to.be.an('array');
    // spec 003 (AC-62): captainId requerido; null sin jugadores, si no un jugador del equipo
    pm.expect(t, 'Team v0.5.0 requiere captainId').to.have.property('captainId');
    if (t.players.length === 0) pm.expect(t.captainId == null, 'AC-62 sin jugadores captainId null').to.be.true;
    else pm.expect(t.players.map(p => p.id), 'AC-62 el capitán es del equipo').to.include(t.captainId);
    t.players.forEach(p => {
      pm.expect(p.id).to.match(/^[0-9a-fA-F-]{36}$/);
      pm.expect(p.name).to.be.a('string');
      pm.expect(TEAM).to.include(p.team);
      pm.expect(p.connected).to.be.a('boolean');
    });
  });
  if (s.round) {
    const r = s.round;
    pm.expect(r.number).to.be.at.least(1);
    pm.expect([1, 2, 3]).to.include(r.multiplier);
    pm.expect(r.question).to.be.a('string').and.not.empty;
    pm.expect(r.strikes).to.be.within(0, 3);
    pm.expect(r.pot).to.be.a('number');
    // spec 002: activeTeam (AC-35), strikesByTeam e invariante (AC-41), turnDeadline ISO-8601 UTC (AC-47)
    ['activeTeam', 'strikesByTeam', 'turnDeadline'].forEach(k => pm.expect(r, 'RoundState v0.4.0 requiere ' + k).to.have.property(k));
    pm.expect(r.activeTeam == null || TEAM.includes(r.activeTeam), 'activeTeam A | B | null').to.be.true;
    pm.expect(r.strikesByTeam, 'strikesByTeam').to.be.an('object');
    pm.expect(r.strikesByTeam.A, 'strikesByTeam.A').to.be.within(0, 3);
    pm.expect(r.strikesByTeam.B, 'strikesByTeam.B').to.be.within(0, 3);
    if (r.controllingTeam) pm.expect(r.strikesByTeam[r.controllingTeam], 'AC-41 strikesByTeam[controllingTeam] == strikes').to.equal(r.strikes);
    if (r.turnDeadline != null) pm.expect(r.turnDeadline, 'turnDeadline UTC').to.match(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?(Z|\+00:00)$/);
    if (s.turnSeconds === 0) pm.expect(r.turnDeadline == null, 'AC-47 sin temporizador no hay turnDeadline').to.be.true;
    pm.expect(r.board).to.be.an('array');
    r.board.forEach(b => {
      pm.expect(b.position).to.be.within(1, 8);
      pm.expect(b.revealed).to.be.a('boolean');
    });
  }
}
function expectPublicNoLeak(s) {
  // AC-25: casillas ocultas sin text/points y sin el tablero del presentador
  pm.expect(s, 'vista pública').to.not.have.property('presenterBoard');
  (s.round ? s.round.board : []).forEach(b => {
    if (!b.revealed) {
      pm.expect(b.text == null, 'text oculto en posición ' + b.position).to.be.true;
      pm.expect(b.points == null, 'points ocultos en posición ' + b.position).to.be.true;
    }
  });
  const hidden = JSON.parse(pm.collectionVariables.get('hiddenTexts') || '[]');
  const raw = pm.response.text();
  hidden.forEach(t => pm.expect(raw, 'fuga de "' + t + '"').to.not.include(JSON.stringify(t)));
}
function expectPresenterState(s) {
  expectRoomState(s);
  if (s.round) {
    pm.expect(s.presenterBoard, 'presenterBoard').to.be.an('array').that.is.not.empty;
    s.presenterBoard.forEach(b => {
      pm.expect(b.answerId).to.be.a('number');
      pm.expect(b.position).to.be.a('number');
      pm.expect(b.revealed).to.be.a('boolean');
      pm.expect(b.text).to.be.a('string');
      pm.expect(b.points).to.be.a('number');
    });
  }
}
function saveBoard(s) {
  // Guarda answerId por posición (answerId1…answerIdN) y los textos aún ocultos.
  const board = (s.presenterBoard || []).slice().sort((a, b) => a.position - b.position);
  board.forEach(b => pm.collectionVariables.set('answerId' + b.position, String(b.answerId)));
  pm.collectionVariables.set('answerCount', String(board.length));
  pm.collectionVariables.set('hiddenTexts', JSON.stringify(board.filter(b => !b.revealed).map(b => b.text)));
}
function expectProblem(status, code) {
  pm.test('HTTP ' + status + (code ? ' ' + code : ''), () => {
    pm.response.to.have.status(status);
    if (code) pm.expect(pm.response.json().code).to.equal(code);
  });
}
function score(s, team) { return s.teams.find(t => t.id === team).score; }
function team(s, id) { return s.teams.find(t => t.id === id); }
"""


def ok(status: int, name: str, extra: str = "") -> str:
    """Prueba de éxito: estado HTTP + snapshot del presentador + líneas extra."""
    return f"""
pm.test('{name}: HTTP {status}', () => pm.response.to.have.status({status}));
const s = pm.response.json();
pm.test('{name}: forma PresenterRoomState', () => expectPresenterState(s));
saveBoard(s);
{extra}
"""


# --------------------------------------------------------------------------- Flujo

# Cada paso: (carpeta, nombre, método, path, cuerpo | None, headers extra {k: v}, script de prueba JS)
P = {"X-Presenter-Token": "{{presenterToken}}"}
PHONE = {"X-Presenter-Token": "{{phoneToken}}"}

FLOW = [
    # ---------------------------------------------------------------- 01 red
    ("01 red", "getNetworkInfo (AC-04)", "GET", "/api/network-info", None, {}, """
pm.test('HTTP 200 con lanAddress y puertos (AC-04)', () => {
  pm.response.to.have.status(200);
  const b = pm.response.json();
  pm.expect(b.lanAddress).to.be.a('string').and.not.empty;
  pm.expect(b.backendPort).to.be.a('number');
  pm.expect(b.frontendPort).to.be.a('number');
});
"""),

    # ---------------------------------------------------------------- 02 sala
    # Spec 003 (AC-57): la pantalla de título ya no envía nombres de equipo; los pone el capitán.
    ("02 sala", "createRoom sin nombres de equipo (AC-01, AC-02, AC-57)", "POST", "/api/rooms",
     {"rounds": 3, "targetScore": 1000, "turnSeconds": 0, "autoStrikeOnTimeout": True}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
const b = pm.response.json();
pm.test('AC-01 código de 4 letras sin I/O y tokens', () => {
  pm.expect(b.code).to.match(/^[A-HJ-NP-Z]{4}$/);
  pm.expect(b.presenterToken).to.be.a('string').and.not.empty;
  pm.expect(b.presenterClaimToken).to.be.a('string').and.not.empty;
  pm.expect(b.presenterClaimToken).to.not.equal(b.presenterToken);
});
pm.test('AC-02 configuración aplicada y sala en LOBBY', () => {
  expectPresenterState(b.state);
  pm.expect(b.state.status).to.equal('LOBBY');
  pm.expect(b.state.totalRounds).to.equal(3);
  pm.expect(b.state.targetScore).to.equal(1000);
  pm.expect(b.state.presenterConnected).to.equal(false);
  pm.expect(team(b.state, 'A').name).to.equal('Equipo Rojo');
  pm.expect(team(b.state, 'B').name).to.equal('Equipo Verde');
});
pm.test('AC-62 sin jugadores: captainId null en ambos equipos', () => {
  b.state.teams.forEach(t => pm.expect(t.captainId === null, 'captainId de ' + t.id).to.be.true);
});
pm.test('AC-46 sala sin temporizador (turnSeconds = 0)', () => {
  pm.expect(b.state.turnSeconds).to.equal(0);
  pm.expect(b.state.autoStrikeOnTimeout).to.equal(true);
});
pm.collectionVariables.set('roomCode', b.code);
pm.collectionVariables.set('presenterToken', b.presenterToken);
pm.collectionVariables.set('presenterClaimToken', b.presenterClaimToken);
pm.collectionVariables.set('hiddenTexts', '[]');
"""),
    ("02 sala", "createRoom con valores por defecto (AC-02)", "POST", "/api/rooms", {}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
const b = pm.response.json();
pm.test('AC-02 por defecto: Equipo Rojo / Equipo Verde, 5 rondas, meta 300', () => {
  pm.expect(b.state.totalRounds).to.equal(5);
  pm.expect(b.state.targetScore).to.equal(300);
  pm.expect(b.state.teams.find(t => t.id === 'A').name).to.equal('Equipo Rojo');
  pm.expect(b.state.teams.find(t => t.id === 'B').name).to.equal('Equipo Verde');
});
pm.test('AC-46 AC-53 AC-134 por defecto: turnSeconds 10, autoStrikeOnTimeout true, soundEnabled true', () => {
  pm.expect(b.state.turnSeconds).to.equal(10);
  pm.expect(b.state.autoStrikeOnTimeout).to.equal(true);
  pm.expect(b.state.soundEnabled).to.equal(true);
});
pm.test('AC-01 código distinto al de la otra sala activa', () => pm.expect(b.code).to.not.equal(pm.collectionVariables.get('roomCode')));
pm.collectionVariables.set('otherPresenterToken', b.presenterToken);
pm.collectionVariables.set('otherRoomCode', b.code);
"""),
    ("02 sala", "createRoom inválida → 400 (AC-02)", "POST", "/api/rooms", {"rounds": 9}, {}, """
expectProblem(400);
"""),
    ("02 sala", "createRoom turnSeconds 9 → 400 (AC-46)", "POST", "/api/rooms", {"turnSeconds": 9}, {}, """
expectProblem(400);
"""),
    ("02 sala", "createRoom turnSeconds 61 → 400 (AC-46)", "POST", "/api/rooms", {"turnSeconds": 61}, {}, """
expectProblem(400);
"""),
    ("02 sala", "createRoom turnSeconds -1 → 400 (AC-46)", "POST", "/api/rooms", {"turnSeconds": -1}, {}, """
expectProblem(400);
"""),
    ("02 sala", "createRoom turnSeconds 10 sin X automática (AC-46)", "POST", "/api/rooms",
     {"turnSeconds": 10, "autoStrikeOnTimeout": False}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
const b = pm.response.json();
pm.test('AC-46 turnSeconds 10 y autoStrikeOnTimeout false', () => {
  expectPresenterState(b.state);
  pm.expect(b.state.turnSeconds).to.equal(10);
  pm.expect(b.state.autoStrikeOnTimeout).to.equal(false);
});
"""),
    ("02 sala", "getRoom (público)", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const s = pm.response.json();
pm.test('forma RoomState sin datos del presentador (AC-25)', () => { expectRoomState(s); expectPublicNoLeak(s); });
pm.test('AC-32 presenterConnected = false antes del canje', () => pm.expect(s.presenterConnected).to.equal(false));
"""),
    ("02 sala", "getRoom inexistente → 404", "GET", "/api/rooms/QZQZ", None, {}, """
expectProblem(404, 'ROOM_NOT_FOUND');
"""),
    ("02 sala", "getPresenterView", "GET", "/api/rooms/{{roomCode}}/presenter-view", None, P, ok(200, "getPresenterView")),

    # ---------------------------------------------------------------- 03 presentador (QR)
    ("03 presentador", "claimPresenter (AC-30)", "POST", "/api/rooms/{{roomCode}}/presenter/claim",
     {"claimToken": "{{presenterClaimToken}}"}, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const b = pm.response.json();
pm.test('AC-30 entrega presenterToken; AC-32 presenterConnected = true', () => {
  pm.expect(b.presenterToken).to.be.a('string').and.not.empty;
  expectPresenterState(b.state);
  pm.expect(b.state.presenterConnected).to.equal(true);
});
pm.collectionVariables.set('oldPhoneToken', b.presenterToken);
"""),
    ("03 presentador", "claimPresenter otra vez → 409 CLAIM_USED (AC-30)", "POST", "/api/rooms/{{roomCode}}/presenter/claim",
     {"claimToken": "{{presenterClaimToken}}"}, {}, """
expectProblem(409, 'CLAIM_USED');
"""),
    ("03 presentador", "claimPresenter inválido → 403 CLAIM_INVALID", "POST", "/api/rooms/{{roomCode}}/presenter/claim",
     {"claimToken": "no-es-un-claim"}, {}, """
expectProblem(403, 'CLAIM_INVALID');
"""),
    ("03 presentador", "regeneratePresenterClaim (AC-33)", "POST", "/api/rooms/{{roomCode}}/presenter/claim-token", None, P, """
pm.test('HTTP 200 con claimToken nuevo', () => {
  pm.response.to.have.status(200);
  const b = pm.response.json();
  pm.expect(b.claimToken).to.be.a('string').and.not.empty;
  pm.expect(b.claimToken).to.not.equal(pm.collectionVariables.get('presenterClaimToken'));
  pm.collectionVariables.set('presenterClaimToken', b.claimToken);
});
"""),
    ("03 presentador", "getRoom tras regenerar: presenterConnected = false (AC-33)", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
pm.test('AC-33 presenterConnected vuelve a false', () => pm.expect(pm.response.json().presenterConnected).to.equal(false));
"""),
    ("03 presentador", "getPresenterView con el celular anterior → 403 (AC-33)", "GET", "/api/rooms/{{roomCode}}/presenter-view",
     None, {"X-Presenter-Token": "{{oldPhoneToken}}"}, """
expectProblem(403);
"""),
    ("03 presentador", "getPresenterView con el token de la laptop sigue OK (AC-33)", "GET", "/api/rooms/{{roomCode}}/presenter-view",
     None, P, ok(200, "laptop")),
    ("03 presentador", "claimPresenter con el QR nuevo (AC-30, AC-33)", "POST", "/api/rooms/{{roomCode}}/presenter/claim",
     {"claimToken": "{{presenterClaimToken}}"}, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const b = pm.response.json();
pm.test('presenterConnected = true', () => pm.expect(b.state.presenterConnected).to.equal(true));
pm.collectionVariables.set('phoneToken', b.presenterToken);
"""),

    # ---------------------------------------------------------------- 04 jugadores
    ("04 jugadores", "startGame sin jugadores → 409 TEAM_EMPTY (AC-10)", "POST", "/api/rooms/{{roomCode}}/start", None, PHONE, """
expectProblem(409, 'TEAM_EMPTY');
"""),
    ("04 jugadores", "joinRoom Lupita (A) (AC-05)", "POST", "/api/rooms/{{roomCode}}/players", {"name": "Lupita", "team": "A"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
const b = pm.response.json();
pm.test('forma JoinRoomResponse', () => {
  pm.expect(b.playerToken).to.be.a('string').and.not.empty;
  pm.expect(b.player.name).to.equal('Lupita');
  pm.expect(b.player.team).to.equal('A');
  pm.expect(b.player.id).to.match(/^[0-9a-fA-F-]{36}$/);
});
pm.collectionVariables.set('playerToken', b.playerToken);
pm.collectionVariables.set('playerId', b.player.id);
"""),
    ("04 jugadores", "joinRoom nombre repetido → 409 NAME_TAKEN (AC-06)", "POST", "/api/rooms/{{roomCode}}/players",
     {"name": "LUPITA", "team": "B"}, {}, """
expectProblem(409, 'NAME_TAKEN');
"""),
    ("04 jugadores", "joinRoom nombre corto → 400 (AC-05)", "POST", "/api/rooms/{{roomCode}}/players", {"name": "L", "team": "B"}, {}, """
expectProblem(400);
"""),
    ("04 jugadores", "startGame con un equipo vacío → 409 TEAM_EMPTY (AC-10)", "POST", "/api/rooms/{{roomCode}}/start", None, PHONE, """
expectProblem(409, 'TEAM_EMPTY');
"""),
    ("04 jugadores", "joinRoom Pancho (B) (AC-05)", "POST", "/api/rooms/{{roomCode}}/players", {"name": "Pancho", "team": "B"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
pm.test('equipo B', () => pm.expect(pm.response.json().player.team).to.equal('B'));
pm.collectionVariables.set('player2Token', pm.response.json().playerToken);
pm.collectionVariables.set('player2Id', pm.response.json().player.id);
"""),
    ("04 jugadores", "joinRoom Chuy (A), segundo de su equipo (AC-05)", "POST", "/api/rooms/{{roomCode}}/players",
     {"name": "Chuy", "team": "A"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('memberToken', pm.response.json().playerToken);
"""),
    ("04 jugadores", "getMe (AC-09)", "GET", "/api/rooms/{{roomCode}}/players/me", None, {"X-Player-Token": "{{playerToken}}"}, """
pm.test('HTTP 200 con la misma identidad', () => {
  pm.response.to.have.status(200);
  const p = pm.response.json();
  pm.expect(p.id).to.equal(pm.collectionVariables.get('playerId'));
  pm.expect(p.name).to.equal('Lupita');
  pm.expect(p.team).to.equal('A');
});
"""),
    ("04 jugadores", "getMe sin token → 401 (AC-09)", "GET", "/api/rooms/{{roomCode}}/players/me", None, {}, """
expectProblem(401);
"""),

    # ---------------------------------------------------------------- 04b equipos (spec 003, contrato v0.5.0)
    ("04b equipos", "getRoom: el primero de cada equipo es capitán (AC-62)", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const s = pm.response.json();
pm.test('forma RoomState', () => expectRoomState(s));
pm.test('AC-62 captainId A = Lupita, B = Pancho (Chuy no cambia la capitanía)', () => {
  pm.expect(team(s, 'A').captainId).to.equal(pm.collectionVariables.get('playerId'));
  pm.expect(team(s, 'B').captainId).to.equal(pm.collectionVariables.get('player2Id'));
});
"""),
    ("04b equipos", "getPresenterView también trae captainId (AC-62)", "GET", "/api/rooms/{{roomCode}}/presenter-view", None, PHONE,
     ok(200, "presenter-view", """
pm.test('AC-62 captainId en la proyección del presentador', () => {
  pm.expect(team(s, 'A').captainId).to.equal(pm.collectionVariables.get('playerId'));
  pm.expect(team(s, 'B').captainId).to.equal(pm.collectionVariables.get('player2Id'));
});
""")),
    ("04b equipos", "joinRoom en la otra sala (token ajeno)", "POST", "/api/rooms/{{otherRoomCode}}/players",
     {"name": "Ximena", "team": "A"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('otherPlayerToken', pm.response.json().playerToken);
"""),
    ("04b equipos", "renameTeam A por su capitán, recorta espacios (AC-63)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "  Los Tlacuaches  "}, {"X-Player-Token": "{{playerToken}}"}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const s = pm.response.json();
pm.test('forma RoomState pública (sin presenterBoard)', () => { expectRoomState(s); expectPublicNoLeak(s); });
pm.test('AC-63 nombre recortado y guardado; B intacto; sigue en LOBBY', () => {
  pm.expect(team(s, 'A').name).to.equal('Los Tlacuaches');
  pm.expect(team(s, 'B').name).to.equal('Equipo Verde');
  pm.expect(s.status).to.equal('LOBBY');
  pm.expect(team(s, 'A').captainId).to.equal(pm.collectionVariables.get('playerId'));
});
"""),
    ("04b equipos", "renameTeam B por su capitán (AC-63)", "PUT", "/api/rooms/{{roomCode}}/teams/B/name",
     {"name": "Las Ajolotas"}, {"X-Player-Token": "{{player2Token}}"}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const s = pm.response.json();
pm.test('AC-63 B renombrado', () => pm.expect(team(s, 'B').name).to.equal('Las Ajolotas'));
pm.collectionVariables.set('renameVersion', String(s.version));
"""),
    ("04b equipos", "renameTeam al mismo nombre exacto → no-op (AC-63)", "PUT", "/api/rooms/{{roomCode}}/teams/B/name",
     {"name": "Las Ajolotas"}, {"X-Player-Token": "{{player2Token}}"}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const s = pm.response.json();
pm.test('mismo nombre y la versión no sube', () => {
  pm.expect(team(s, 'B').name).to.equal('Las Ajolotas');
  pm.expect(s.version).to.equal(Number(pm.collectionVariables.get('renameVersion')));
});
"""),
    ("04b equipos", "renameTeam sin token → 401 (AC-64)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "Sin Token"}, {}, """
expectProblem(401, 'UNAUTHORIZED');
"""),
    ("04b equipos", "renameTeam por un jugador que no es capitán → 403 NOT_CAPTAIN (AC-64)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "Usurpadores"}, {"X-Player-Token": "{{memberToken}}"}, """
expectProblem(403, 'NOT_CAPTAIN');
"""),
    ("04b equipos", "renameTeam por el capitán del otro equipo → 403 NOT_CAPTAIN (AC-64)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "Usurpadores"}, {"X-Player-Token": "{{player2Token}}"}, """
expectProblem(403, 'NOT_CAPTAIN');
"""),
    ("04b equipos", "renameTeam con token de otra sala → 403 NOT_CAPTAIN (AC-64)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "Usurpadores"}, {"X-Player-Token": "{{otherPlayerToken}}"}, """
expectProblem(403, 'NOT_CAPTAIN');
"""),
    ("04b equipos", "renameTeam en sala inexistente → 404 (AC-64)", "PUT", "/api/rooms/QZQZ/teams/A/name",
     {"name": "Los Tlacuaches"}, {"X-Player-Token": "{{playerToken}}"}, """
expectProblem(404, 'ROOM_NOT_FOUND');
"""),
    ("04b equipos", "renameTeam con el nombre del otro equipo (mayúsculas) → 409 TEAM_NAME_TAKEN (AC-64)", "PUT",
     "/api/rooms/{{roomCode}}/teams/A/name", {"name": "LAS AJOLOTAS"}, {"X-Player-Token": "{{playerToken}}"}, """
expectProblem(409, 'TEAM_NAME_TAKEN');
"""),
    ("04b equipos", "renameTeam de 1 carácter → 400 VALIDATION_ERROR (AC-64)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": " X "}, {"X-Player-Token": "{{playerToken}}"}, """
expectProblem(400, 'VALIDATION_ERROR');
"""),
    ("04b equipos", "renameTeam de 21 caracteres → 400 VALIDATION_ERROR (AC-64)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "Veintiún caracteres!!"}, {"X-Player-Token": "{{playerToken}}"}, """
expectProblem(400, 'VALIDATION_ERROR');
"""),
    ("04b equipos", "getRoom: los rechazos no cambiaron los nombres (AC-64, AC-66)", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const s = pm.response.json();
pm.test('nombres actuales y versión intacta', () => {
  pm.expect(team(s, 'A').name).to.equal('Los Tlacuaches');
  pm.expect(team(s, 'B').name).to.equal('Las Ajolotas');
  pm.expect(s.version).to.equal(Number(pm.collectionVariables.get('renameVersion')));
});
"""),

    # ---------------------------------------------------------------- 05 seguridad
    ("05 seguridad", "startGame sin token → 401 (AC-26)", "POST", "/api/rooms/{{roomCode}}/start", None, {}, """
expectProblem(401);
"""),
    ("05 seguridad", "startGame con token de otra sala → 403 (AC-26)", "POST", "/api/rooms/{{roomCode}}/start", None,
     {"X-Presenter-Token": "{{otherPresenterToken}}"}, """
expectProblem(403);
"""),
    ("05 seguridad", "getPresenterView con playerToken → 401/403 (AC-26)", "GET", "/api/rooms/{{roomCode}}/presenter-view", None,
     {"X-Presenter-Token": "{{playerToken}}"}, """
pm.test('rechazado', () => pm.expect([401, 403]).to.include(pm.response.code));
"""),
    ("05 seguridad", "listQuestions sin token → 401", "GET", "/api/questions", None, {}, """
expectProblem(401);
"""),
    ("05 seguridad", "listQuestions (presentador)", "GET", "/api/questions", None, P, """
pm.test('HTTP 200 con ≥ 10 preguntas y sus respuestas (RNF-5)', () => {
  pm.response.to.have.status(200);
  const qs = pm.response.json();
  pm.expect(qs).to.be.an('array').with.length.at.least(10);
  qs.forEach(q => {
    pm.expect(q.text).to.be.a('string').and.not.empty;
    pm.expect(q.answers.reduce((a, x) => a + x.points, 0)).to.be.at.most(100);
  });
});
"""),

    # ---------------------------------------------------------------- 05b sonido (spec 002, AC-53)
    ("05b sonido", "setSound false (AC-53)", "POST", "/api/rooms/{{roomCode}}/sound", {"enabled": False}, PHONE,
     ok(200, "setSound false", """
pm.test('AC-53 soundEnabled = false; el estado del juego no cambia', () => {
  pm.expect(s.soundEnabled).to.equal(false);
  pm.expect(s.status).to.equal('LOBBY');
});
""")),
    ("05b sonido", "getRoom refleja soundEnabled = false (AC-53)", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
pm.test('AC-53 soundEnabled viaja en RoomState', () => pm.expect(pm.response.json().soundEnabled).to.equal(false));
"""),
    ("05b sonido", "setSound true (AC-53)", "POST", "/api/rooms/{{roomCode}}/sound", {"enabled": True}, P,
     ok(200, "setSound true", "pm.test('AC-53 soundEnabled = true', () => pm.expect(s.soundEnabled).to.equal(true));")),
    ("05b sonido", "setSound sin token → 401 (AC-53, AC-26)", "POST", "/api/rooms/{{roomCode}}/sound", {"enabled": False}, {}, """
expectProblem(401, 'UNAUTHORIZED');
"""),
    ("05b sonido", "setSound con token de otra sala → 403 (AC-53, AC-26)", "POST", "/api/rooms/{{roomCode}}/sound",
     {"enabled": False}, {"X-Presenter-Token": "{{otherPresenterToken}}"}, """
expectProblem(403, 'FORBIDDEN');
"""),
    ("05b sonido", "setSound sin enabled → 400 (AC-53)", "POST", "/api/rooms/{{roomCode}}/sound", {}, PHONE, """
expectProblem(400);
"""),
    ("05b sonido", "setSound en sala inexistente → 404 (AC-53)", "POST", "/api/rooms/QZQZ/sound", {"enabled": False}, PHONE, """
expectProblem(404, 'ROOM_NOT_FOUND');
"""),
    ("05b sonido", "getRoom: el sonido sigue activado tras los rechazos", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('soundEnabled = true', () => pm.expect(pm.response.json().soundEnabled).to.equal(true));
"""),

    # ---------------------------------------------------------------- 06 ronda 1 (con robo acertado)
    ("06 ronda 1", "startGame (AC-11)", "POST", "/api/rooms/{{roomCode}}/start", None, PHONE, ok(200, "startGame", """
pm.test('AC-11 FACE_OFF de la ronda 1, ×1, sin strikes ni bote', () => {
  pm.expect(s.status).to.equal('FACE_OFF');
  pm.expect(s.round.number).to.equal(1);
  pm.expect(s.round.multiplier).to.equal(1);
  pm.expect(s.round.strikes).to.equal(0);
  pm.expect(s.round.pot).to.equal(0);
  pm.expect(s.presenterBoard.every(b => !b.revealed)).to.be.true;
  pm.expect(s.round.faceOff.playerA).to.equal(pm.collectionVariables.get('playerId'));
});
pm.test('AC-35 FACE_OFF sin buzz: activeTeam null; AC-41 strikesByTeam {0, 0}; AC-47 sin turnDeadline', () => {
  pm.expect(s.round.activeTeam == null).to.be.true;
  pm.expect(s.round.strikesByTeam).to.eql({ A: 0, B: 0 });
  pm.expect(s.round.turnDeadline == null).to.be.true;
});
pm.collectionVariables.set('p1', String(s.presenterBoard.find(b => b.position === 1).points));
pm.collectionVariables.set('p2', String(s.presenterBoard.find(b => b.position === 2).points));
""")),
    ("06 ronda 1", "getRoom sin fuga de respuestas (AC-25)", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
const s = pm.response.json();
pm.test('AC-25 ninguna respuesta oculta en la vista pública', () => { expectRoomState(s); expectPublicNoLeak(s); });
"""),
    ("06 ronda 1", "joinRoom tras iniciar → 409 ROOM_NOT_IN_LOBBY (AC-07)", "POST", "/api/rooms/{{roomCode}}/players",
     {"name": "Tardío", "team": "A"}, {}, """
expectProblem(409, 'ROOM_NOT_IN_LOBBY');
"""),
    ("06 ronda 1", "renameTeam fuera de LOBBY → 409 ROOM_NOT_IN_LOBBY (AC-64)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "Tarde"}, {"X-Player-Token": "{{playerToken}}"}, """
expectProblem(409, 'ROOM_NOT_IN_LOBBY');
"""),
    ("06 ronda 1", "nextRound en FACE_OFF → 409 INVALID_STATE", "POST", "/api/rooms/{{roomCode}}/next-round", None, PHONE, """
expectProblem(409, 'INVALID_STATE');
"""),
    ("06 ronda 1", "markStrike en cara a cara sin buzz → 409 NO_ACTIVE_TEAM (AC-40)", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE, """
expectProblem(409, 'NO_ACTIVE_TEAM');
"""),
    ("06 ronda 1", "getPresenterView: la X rechazada no cambió nada (AC-40)", "GET", "/api/rooms/{{roomCode}}/presenter-view",
     None, PHONE, ok(200, "presenter-view", """
pm.test('FACE_OFF sin strikes ni equipo en turno', () => {
  pm.expect(s.status).to.equal('FACE_OFF');
  pm.expect(s.round.strikes).to.equal(0);
  pm.expect(s.round.strikesByTeam).to.eql({ A: 0, B: 0 });
  pm.expect(s.round.activeTeam == null).to.be.true;
});
""")),
    ("06 ronda 1", "revealAnswer en cara a cara sin buzz → 409 NO_ACTIVE_TEAM (spec 006 AC-100)", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/answers/{{answerId1}}/reveal", None, PHONE, """
expectProblem(409, 'NO_ACTIVE_TEAM');
"""),
    ("06 ronda 1", "assignControl A: corrección manual (AC-15, spec 006 C-28)", "POST", "/api/rooms/{{roomCode}}/rounds/current/control", {"team": "A"}, PHONE,
     ok(200, "assignControl", """
pm.test('PLAYING con control de A', () => {
  pm.expect(s.status).to.equal('PLAYING');
  pm.expect(s.round.controllingTeam).to.equal('A');
});
pm.test('AC-35 PLAYING: activeTeam = controllingTeam', () => pm.expect(s.round.activeTeam).to.equal('A'));
pm.test('spec 006 AC-101 faceOff trae firstTeam y firstAnswerPosition (null sin buzz)', () => {
  pm.expect(s.round.faceOff).to.have.property('firstTeam');
  pm.expect(s.round.faceOff).to.have.property('firstAnswerPosition');
  pm.expect(s.round.faceOff.firstTeam == null).to.be.true;
});
""")),
    ("06 ronda 1", "assignControl en PLAYING → 409 INVALID_STATE", "POST", "/api/rooms/{{roomCode}}/rounds/current/control",
     {"team": "B"}, PHONE, """
expectProblem(409, 'INVALID_STATE');
"""),
    ("06 ronda 1", "revealAnswer #1 (AC-17)", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/answers/{{answerId1}}/reveal", None, PHONE, ok(200, "reveal #1", """
pm.test('el bote suma la #1', () => {
  pm.expect(s.status).to.equal('PLAYING');
  pm.expect(s.round.pot).to.equal(Number(pm.collectionVariables.get('p1')));
});
""")),
    ("06 ronda 1", "revealAnswer #2 (AC-16, AC-17)", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/answers/{{answerId2}}/reveal", None, PHONE, ok(200, "reveal #2", """
const pot = Number(pm.collectionVariables.get('p1')) + Number(pm.collectionVariables.get('p2'));
pm.test('AC-17 suma al bote, no al marcador', () => {
  pm.expect(s.status).to.equal('PLAYING');
  pm.expect(s.round.pot).to.equal(pot);
  pm.expect(score(s, 'A')).to.equal(0);
});
pm.collectionVariables.set('pot', String(pot));
""")),
    ("06 ronda 1", "revealAnswer repetida → 409 INVALID_STATE", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/answers/{{answerId2}}/reveal", None, PHONE, """
expectProblem(409, 'INVALID_STATE');
"""),
    ("06 ronda 1", "markStrike 1 (AC-16)", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE, ok(200, "strike 1", """
pm.test('PLAYING con 1 strike', () => { pm.expect(s.status).to.equal('PLAYING'); pm.expect(s.round.strikes).to.equal(1); });
pm.test('AC-40 AC-41 la X es del equipo en turno (A)', () => pm.expect(s.round.strikesByTeam).to.eql({ A: 1, B: 0 }));
""")),
    ("06 ronda 1", "markStrike 2", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE, ok(200, "strike 2", """
pm.test('PLAYING con 2 strikes', () => pm.expect(s.round.strikes).to.equal(2));
""")),
    ("06 ronda 1", "markStrike 3 → STEAL (AC-18)", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE,
     ok(200, "strike 3", """
pm.test('AC-18 STEAL con 3 strikes', () => { pm.expect(s.status).to.equal('STEAL'); pm.expect(s.round.strikes).to.equal(3); });
pm.test('AC-35 STEAL: activeTeam = rival (B); AC-41 strikesByTeam {3, 0}', () => {
  pm.expect(s.round.activeTeam).to.equal('B');
  pm.expect(s.round.strikesByTeam).to.eql({ A: 3, B: 0 });
});
""")),
    ("06 ronda 1", "revealAnswer #3 en robo → bote para B (AC-19, AC-20)", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/answers/{{answerId3}}/reveal", None, PHONE, ok(200, "robo", """
const pot = Number(pm.collectionVariables.get('pot'));
pm.test('AC-19 robo acertado: ROUND_END, potWinner B, B suma bote × 1', () => {
  pm.expect(s.status).to.equal('ROUND_END');
  pm.expect(s.round.potWinner).to.equal('B');
  pm.expect(score(s, 'B')).to.equal(s.round.pot * s.round.multiplier);
  pm.expect(s.round.pot).to.equal(pot); // data-model.md: el bote no crece en STEAL
  pm.expect(score(s, 'A')).to.equal(0);
});
pm.test('AC-20 todas las respuestas quedan reveladas', () => pm.expect(s.presenterBoard.every(b => b.revealed)).to.be.true);
pm.test('AC-35 ROUND_END: activeTeam null', () => pm.expect(s.round.activeTeam == null).to.be.true);
pm.collectionVariables.set('scoreB', String(score(s, 'B')));
""")),
    ("06 ronda 1", "markStrike en ROUND_END → 409 INVALID_STATE", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None,
     PHONE, """
expectProblem(409, 'INVALID_STATE');
"""),

    # ---------------------------------------------------------------- 07 ronda 2 (robo fallido)
    ("07 ronda 2", "nextRound → ronda 2 ×2 (AC-21)", "POST", "/api/rooms/{{roomCode}}/next-round", None, PHONE,
     ok(200, "nextRound", """
pm.test('FACE_OFF ronda 2 con multiplicador ×2 (penúltima de 3)', () => {
  pm.expect(s.status).to.equal('FACE_OFF');
  pm.expect(s.round.number).to.equal(2);
  pm.expect(s.round.multiplier).to.equal(2);
  pm.expect(s.round.pot).to.equal(0);
  pm.expect(s.round.strikes).to.equal(0);
});
pm.collectionVariables.set('p1', String(s.presenterBoard.find(b => b.position === 1).points));
""")),
    ("07 ronda 2", "assignControl A", "POST", "/api/rooms/{{roomCode}}/rounds/current/control", {"team": "A"}, PHONE,
     ok(200, "assignControl")),
    ("07 ronda 2", "revealAnswer #1", "POST", "/api/rooms/{{roomCode}}/rounds/current/answers/{{answerId1}}/reveal", None, PHONE,
     ok(200, "reveal #1")),
    ("07 ronda 2", "markStrike 1", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE, ok(200, "strike")),
    ("07 ronda 2", "markStrike 2", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE, ok(200, "strike")),
    ("07 ronda 2", "markStrike 3 → STEAL", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE,
     ok(200, "strike", "pm.test('STEAL', () => pm.expect(s.status).to.equal('STEAL'));")),
    ("07 ronda 2", "markStrike en robo → bote para A ×2 (AC-19, AC-21)", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike",
     None, PHONE, ok(200, "robo fallido", """
pm.test('AC-19 robo fallido: potWinner A; AC-21 bote × 2', () => {
  pm.expect(s.status).to.equal('ROUND_END');
  pm.expect(s.round.potWinner).to.equal('A');
  pm.expect(s.round.pot).to.equal(Number(pm.collectionVariables.get('p1')));
  pm.expect(score(s, 'A')).to.equal(s.round.pot * 2);
  pm.expect(score(s, 'B')).to.equal(Number(pm.collectionVariables.get('scoreB')));
});
pm.test('AC-40 AC-41 la X del robo es del equipo que roba (B)', () => pm.expect(s.round.strikesByTeam).to.eql({ A: 3, B: 1 }));
""")),

    # ---------------------------------------------------------------- 08 ronda 3 y fin
    ("08 ronda 3 y fin", "nextRound → ronda 3 ×3 (AC-21)", "POST", "/api/rooms/{{roomCode}}/next-round", None, PHONE,
     ok(200, "nextRound", """
pm.test('FACE_OFF ronda 3 con ×3 (última)', () => {
  pm.expect(s.round.number).to.equal(3);
  pm.expect(s.round.multiplier).to.equal(3);
});
""")),
    # T727: antes 200 (C-2 original); desde la spec 002 una X sin buzz no tiene equipo en turno (AC-40).
    ("08 ronda 3 y fin", "markStrike en cara a cara sin buzz → 409 NO_ACTIVE_TEAM (AC-40)", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE, """
expectProblem(409, 'NO_ACTIVE_TEAM');
"""),
    ("08 ronda 3 y fin", "assignControl B", "POST", "/api/rooms/{{roomCode}}/rounds/current/control", {"team": "B"}, PHONE,
     ok(200, "assignControl", "pm.test('PLAYING, control B', () => pm.expect(s.round.controllingTeam).to.equal('B'));")),
    ("08 ronda 3 y fin", "markStrike (hasta STEAL)", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE, ok(200, "strike", """
// Hacen falta 3 X (la del cara a cara fue rechazada). Se repite hasta STEAL.
if (pm.response.code === 200 && s.status === 'PLAYING') postman.setNextRequest(pm.info.requestName);
""")),
    ("08 ronda 3 y fin", "markStrike en robo → ROUND_END", "POST", "/api/rooms/{{roomCode}}/rounds/current/strike", None, PHONE,
     ok(200, "robo fallido", "pm.test('ROUND_END, potWinner B', () => { pm.expect(s.status).to.equal('ROUND_END'); pm.expect(s.round.potWinner).to.equal('B'); });")),
    ("08 ronda 3 y fin", "rematch antes de GAME_OVER → 409 INVALID_STATE", "POST", "/api/rooms/{{roomCode}}/rematch", None, PHONE, """
expectProblem(409, 'INVALID_STATE');
"""),
    ("08 ronda 3 y fin", "nextRound tras la última → GAME_OVER (AC-22)", "POST", "/api/rooms/{{roomCode}}/next-round", None, PHONE,
     ok(200, "GAME_OVER", """
pm.test('AC-22 GAME_OVER con ganador por marcador', () => {
  pm.expect(s.status).to.equal('GAME_OVER');
  const a = score(s, 'A'), b = score(s, 'B');
  pm.expect(s.winner).to.equal(a === b ? 'TIE' : (a > b ? 'A' : 'B'));
});
""")),
    ("08 ronda 3 y fin", "getRoom en GAME_OVER muestra winner", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200 con winner', () => {
  pm.response.to.have.status(200);
  pm.expect(['A', 'B', 'TIE']).to.include(pm.response.json().winner);
});
"""),
    ("08 ronda 3 y fin", "rematch → LOBBY (AC-23)", "POST", "/api/rooms/{{roomCode}}/rematch", None, PHONE, ok(200, "rematch", """
pm.test('AC-23 LOBBY con los mismos jugadores y marcador en cero', () => {
  pm.expect(s.status).to.equal('LOBBY');
  pm.expect(s.teams.every(t => t.score === 0)).to.be.true;
  const names = s.teams.flatMap(t => t.players.map(p => p.name));
  pm.expect(names).to.have.members(['Lupita', 'Chuy', 'Pancho']);
  pm.expect(s.round == null).to.be.true;
  pm.expect(s.winner == null).to.be.true;
});
pm.test('AC-67 la revancha conserva nombres y capitanes', () => {
  pm.expect(team(s, 'A').name).to.equal('Los Tlacuaches');
  pm.expect(team(s, 'B').name).to.equal('Las Ajolotas');
  pm.expect(team(s, 'A').captainId).to.equal(pm.collectionVariables.get('playerId'));
  pm.expect(team(s, 'B').captainId).to.equal(pm.collectionVariables.get('player2Id'));
});
""")),
    ("08 ronda 3 y fin", "renameTeam tras la revancha (de nuevo en LOBBY) (AC-63, AC-67)", "PUT", "/api/rooms/{{roomCode}}/teams/A/name",
     {"name": "Los Cacomixtles"}, {"X-Player-Token": "{{playerToken}}"}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
pm.test('el capitán puede volver a nombrar al equipo', () => pm.expect(team(pm.response.json(), 'A').name).to.equal('Los Cacomixtles'));
"""),

    # ---------------------------------------------------------------- 12 control del presentador (spec 005)
    ("12 spec 005", "createRoom con turnSeconds 10 (spec 005)", "POST", "/api/rooms", {"rounds": 3, "turnSeconds": 10}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
const b = pm.response.json();
pm.collectionVariables.set('r5Code', b.code);
pm.collectionVariables.set('r5Token', b.presenterToken);
"""),
    ("12 spec 005", "joinRoom A (spec 005)", "POST", "/api/rooms/{{r5Code}}/players", {"name": "Toña", "team": "A"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
"""),
    ("12 spec 005", "joinRoom B (spec 005)", "POST", "/api/rooms/{{r5Code}}/players", {"name": "Memo", "team": "B"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
"""),
    ("12 spec 005", "controlTimer en LOBBY → 409 INVALID_STATE (AC-81)", "POST", "/api/rooms/{{r5Code}}/rounds/current/timer",
     {"action": "START"}, {"X-Presenter-Token": "{{r5Token}}"}, """
expectProblem(409, 'INVALID_STATE');
"""),
    ("12 spec 005", "startGame: temporizador detenido y completo (AC-79, AC-82)", "POST", "/api/rooms/{{r5Code}}/start", None, {"X-Presenter-Token": "{{r5Token}}"},
     ok(200, "startGame", """
pm.test('AC-79 AC-82 detenido, sin turnDeadline y con 10000 ms', () => {
  pm.expect(s.round.timerRunning).to.equal(false);
  pm.expect(s.round.turnDeadline == null).to.be.true;
  pm.expect(s.round.timerRemainingMs).to.equal(10000);
});
""")),
    ("12 spec 005", "controlTimer START (AC-80, AC-81)", "POST", "/api/rooms/{{r5Code}}/rounds/current/timer", {"action": "START"}, {"X-Presenter-Token": "{{r5Token}}"},
     ok(200, "controlTimer START", """
pm.test('AC-80 corre: timerRunning y turnDeadline ≈ ahora + 10 s', () => {
  pm.expect(s.round.timerRunning).to.equal(true);
  const ms = Date.parse(s.round.turnDeadline) - Date.now();
  pm.expect(ms).to.be.within(7000, 11000);
});
""")),
    ("12 spec 005", "controlTimer PAUSE (AC-80)", "POST", "/api/rooms/{{r5Code}}/rounds/current/timer", {"action": "PAUSE"}, {"X-Presenter-Token": "{{r5Token}}"},
     ok(200, "controlTimer PAUSE", """
pm.test('AC-80 congelado: sin turnDeadline y con lo que quedaba', () => {
  pm.expect(s.round.timerRunning).to.equal(false);
  pm.expect(s.round.turnDeadline == null).to.be.true;
  pm.expect(s.round.timerRemainingMs).to.be.within(1, 10000);
});
""")),
    ("12 spec 005", "controlTimer RESET (AC-80)", "POST", "/api/rooms/{{r5Code}}/rounds/current/timer", {"action": "RESET"}, {"X-Presenter-Token": "{{r5Token}}"},
     ok(200, "controlTimer RESET", """
pm.test('AC-80 detenido y completo', () => {
  pm.expect(s.round.timerRunning).to.equal(false);
  pm.expect(s.round.timerRemainingMs).to.equal(10000);
});
""")),
    ("12 spec 005", "controlTimer acción inválida → 400 (AC-81)", "POST", "/api/rooms/{{r5Code}}/rounds/current/timer",
     {"action": "LATER"}, {"X-Presenter-Token": "{{r5Token}}"}, """
expectProblem(400, 'VALIDATION_ERROR');
"""),
    ("12 spec 005", "controlTimer sin token → 401 (AC-81)", "POST", "/api/rooms/{{r5Code}}/rounds/current/timer",
     {"action": "START"}, {}, """
expectProblem(401, 'UNAUTHORIZED');
"""),
    ("12 spec 005", "controlTimer con token de otra sala → 403 (AC-81)", "POST", "/api/rooms/{{r5Code}}/rounds/current/timer",
     {"action": "START"}, P, """
expectProblem(403, 'FORBIDDEN');
"""),
    ("12 spec 005", "controlTimer en sala sin temporizador → 409 TIMER_DISABLED (AC-81)", "POST",
     "/api/rooms/{{roomCode}}/rounds/current/timer", {"action": "START"}, P, """
pm.test('la sala principal está en LOBBY: INVALID_STATE va antes que TIMER_DISABLED', () => {
  pm.response.to.have.status(409);
  pm.expect(['INVALID_STATE', 'TIMER_DISABLED']).to.include(pm.response.json().code);
});
"""),
    ("12 spec 005", "closeRoom sin token → 401 (AC-91)", "DELETE", "/api/rooms/{{r5Code}}", None, {}, """
expectProblem(401, 'UNAUTHORIZED');
"""),
    ("12 spec 005", "closeRoom con token de otra sala → 403 (AC-91)", "DELETE", "/api/rooms/{{r5Code}}", None, P, """
expectProblem(403, 'FORBIDDEN');
"""),
    ("12 spec 005", "closeRoom → 204 (AC-91)", "DELETE", "/api/rooms/{{r5Code}}", None, {"X-Presenter-Token": "{{r5Token}}"}, """
pm.test('HTTP 204 sin cuerpo', () => pm.response.to.have.status(204));
"""),
    ("12 spec 005", "getRoom tras cerrar → 404 (AC-91)", "GET", "/api/rooms/{{r5Code}}", None, {}, """
expectProblem(404, 'ROOM_NOT_FOUND');
"""),
    ("12 spec 005", "closeRoom de nuevo → 404 (AC-91)", "DELETE", "/api/rooms/{{r5Code}}", None, {"X-Presenter-Token": "{{r5Token}}"}, """
expectProblem(404, 'ROOM_NOT_FOUND');
"""),
    ("12 spec 005", "la sala principal sigue viva (AC-91)", "GET", "/api/rooms/{{roomCode}}", None, {}, """
pm.test('HTTP 200', () => pm.response.to.have.status(200));
"""),

    # ---------------------------------------------------------------- 13 cambiar pregunta (spec 008)
    ("13 spec 008", "createRoom (spec 008)", "POST", "/api/rooms", {"rounds": 3}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
pm.collectionVariables.set('r8Code', pm.response.json().code);
pm.collectionVariables.set('r8Token', pm.response.json().presenterToken);
"""),
    ("13 spec 008", "changeQuestion en LOBBY → 409 INVALID_STATE (AC-122)", "POST", "/api/rooms/{{r8Code}}/rounds/current/question", None, {"X-Presenter-Token": "{{r8Token}}"}, """
expectProblem(409, 'INVALID_STATE');
"""),
    ("13 spec 008", "joinRoom A (spec 008)", "POST", "/api/rooms/{{r8Code}}/players", {"name": "Toño", "team": "A"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
"""),
    ("13 spec 008", "joinRoom B (spec 008)", "POST", "/api/rooms/{{r8Code}}/players", {"name": "Rosa", "team": "B"}, {}, """
pm.test('HTTP 201', () => pm.response.to.have.status(201));
"""),
    ("13 spec 008", "startGame (spec 008)", "POST", "/api/rooms/{{r8Code}}/start", None, {"X-Presenter-Token": "{{r8Token}}"}, ok(200, "startGame", """
pm.collectionVariables.set('r8Question', s.round.question);
""")),
    ("13 spec 008", "changeQuestion sin token → 401 (AC-122)", "POST", "/api/rooms/{{r8Code}}/rounds/current/question", None, {}, """
expectProblem(401, 'UNAUTHORIZED');
"""),
    ("13 spec 008", "changeQuestion con token de otra sala → 403 (AC-122)", "POST", "/api/rooms/{{r8Code}}/rounds/current/question", None, P, """
expectProblem(403, 'FORBIDDEN');
"""),
    ("13 spec 008", "changeQuestion → otra pregunta, mismo cara a cara (AC-122)", "POST", "/api/rooms/{{r8Code}}/rounds/current/question", None, {"X-Presenter-Token": "{{r8Token}}"},
     ok(200, "changeQuestion", """
pm.test('AC-122 pregunta nueva en la misma ronda, sin buzzer ni respuestas reveladas', () => {
  pm.expect(s.status).to.equal('FACE_OFF');
  pm.expect(s.round.number).to.equal(1);
  pm.expect(s.round.question).to.not.equal(pm.collectionVariables.get('r8Question'));
  pm.expect(s.round.faceOff.buzzWinner == null).to.be.true;
  pm.expect(s.presenterBoard.every(b => !b.revealed)).to.be.true;
});
pm.collectionVariables.set('r8Question', s.round.question);
""")),
    ("13 spec 008", "getRoom muestra la pregunta nueva (AC-122)", "GET", "/api/rooms/{{r8Code}}", None, {}, """
pm.test('la vista pública tiene la pregunta nueva', () => pm.expect(pm.response.json().round.question).to.equal(pm.collectionVariables.get('r8Question')));
"""),
    ("13 spec 008", "assignControl A (spec 008)", "POST", "/api/rooms/{{r8Code}}/rounds/current/control", {"team": "A"}, {"X-Presenter-Token": "{{r8Token}}"}, ok(200, "assignControl")),
    ("13 spec 008", "changeQuestion en PLAYING → 409 INVALID_STATE (AC-122)", "POST", "/api/rooms/{{r8Code}}/rounds/current/question", None, {"X-Presenter-Token": "{{r8Token}}"}, """
expectProblem(409, 'INVALID_STATE');
"""),
]


def build() -> dict:
    folders: dict[str, list] = {}
    for folder, name, method, path, body, headers, script in FLOW:
        hdrs = [{"key": k, "value": v} for k, v in headers.items()]
        if body is not None:
            hdrs.insert(0, {"key": "Content-Type", "value": "application/json"})
        raw_path = path.split("?")[0]
        item = {
            "name": name,
            "request": {
                "method": method,
                "header": hdrs,
                "url": {"raw": "{{baseUrl}}" + path, "host": ["{{baseUrl}}"], "path": [p for p in raw_path.split("/") if p]},
            },
            # Los scripts de colección y de petición no comparten ámbito: las ayudas se anteponen a cada prueba.
            "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": (SHAPES.strip("\n") + "\n// ---\n" + script.strip("\n")).split("\n")}}],
        }
        if body is not None:
            item["request"]["body"] = {
                "mode": "raw",
                "raw": json.dumps(body, ensure_ascii=False, indent=2),
                "options": {"raw": {"language": "json"}},
            }
        folders.setdefault(folder, []).append(item)
    return {
        "info": {
            "name": "Cien Mexicanos API",
            "description": (
                "Flujo encadenado derivado de specs/001-juego-base/contracts/openapi.yaml (v0.9.0, specs 001, 002, 003, 005, 006, 008 y 010). "
                "Ejecutar completo y en orden contra un backend recién levantado. Regenerar al cambiar el contrato: "
                "python3 postman/scripts/generate_collection.py"
            ),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "variable": [
            {"key": k, "value": ""}
            for k in [
                "roomCode", "presenterToken", "presenterClaimToken", "oldPhoneToken", "phoneToken",
                "otherPresenterToken", "playerToken", "playerId", "answerId1", "answerId2", "answerId3",
                "answerCount", "hiddenTexts", "p1", "p2", "pot", "scoreB",
                "otherRoomCode", "player2Token", "player2Id", "memberToken", "otherPlayerToken", "renameVersion",
                "r5Code", "r5Token", "r8Code", "r8Token", "r8Question",
            ]
        ],
        "item": [{"name": k, "item": v} for k, v in folders.items()],
    }


if __name__ == "__main__":
    out = ROOT / "collections" / "cien-mexicanos.postman_collection.json"
    out.write_text(json.dumps(build(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    env = {"name": "local", "values": [{"key": "baseUrl", "value": "http://localhost:8080", "enabled": True}]}
    (ROOT / "environments" / "local.postman_environment.json").write_text(json.dumps(env, indent=2) + "\n", encoding="utf-8")
    print(f"OK → {out.relative_to(ROOT.parent)} ({len(FLOW)} peticiones)")
