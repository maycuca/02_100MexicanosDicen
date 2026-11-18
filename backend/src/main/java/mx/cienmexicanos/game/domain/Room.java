package mx.cienmexicanos.game.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregado raíz de una sala activa. Implementa la máquina de estados de {@code data-model.md}:
 * cualquier comando fuera de su estado válido lanza {@link GameException} con {@code INVALID_STATE}.
 * No es thread-safe: quien lo usa debe serializar los comandos por sala (plan D2).
 * Los comandos que fijan el vencimiento del turno reciben el "ahora" como parámetro (spec 002, plan D12).
 */
public final class Room {

    public static final int MIN_NAME = 2;
    public static final int MAX_NAME = 20;
    public static final int MIN_TARGET = 50;
    public static final int MAX_TARGET = 1000;

    private final String code;
    private final UUID hostToken;
    private final int totalRounds;
    private final int targetScore;
    private final int turnSeconds;
    private final boolean autoStrikeOnTimeout;
    private final Map<TeamId, Team> teams = new EnumMap<>(TeamId.class);
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private UUID claimToken;
    private boolean claimUsed;
    private UUID claimedPresenterToken;
    private RoomStatus status = RoomStatus.LOBBY;
    private List<QuestionSnapshot> questions = List.of();
    /** Spec 008 (AC-122): preguntas descartadas con "Cambiar pregunta"; no vuelven a salir en esta sala. */
    private final java.util.Set<Long> discardedQuestions = new java.util.HashSet<>();
    private Round currentRound;
    private String winner;
    private long version;
    private Instant lastActivity;
    private boolean soundEnabled = true;

    /** Sala con el temporizador por defecto: 20 s y X automática al vencer (AC-46). */
    public Room(String code, String teamAName, String teamBName, int totalRounds, int targetScore, Instant now) {
        this(code, teamAName, teamBName, totalRounds, targetScore, GameRules.DEFAULT_TURN_SECONDS, true, now);
    }

    /** AC-02, AC-46. */
    public Room(String code, String teamAName, String teamBName, int totalRounds, int targetScore, int turnSeconds,
                boolean autoStrikeOnTimeout, Instant now) {
        if (totalRounds < GameRules.MIN_ROUNDS || totalRounds > GameRules.MAX_ROUNDS) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "rounds debe estar entre 3 y 8");
        }
        if (targetScore < MIN_TARGET || targetScore > MAX_TARGET) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "targetScore debe estar entre 50 y 1000");
        }
        if (!GameRules.isValidTurnSeconds(turnSeconds)) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "turnSeconds debe ser 0 o estar entre 10 y 60");
        }
        this.code = code;
        this.hostToken = UUID.randomUUID();
        this.claimToken = UUID.randomUUID();
        this.totalRounds = totalRounds;
        this.targetScore = targetScore;
        this.turnSeconds = turnSeconds;
        this.autoStrikeOnTimeout = autoStrikeOnTimeout;
        teams.put(TeamId.A, new Team(TeamId.A, requireTeamName(teamAName)));
        teams.put(TeamId.B, new Team(TeamId.B, requireTeamName(teamBName)));
        this.lastActivity = now;
        this.version = 1;
    }

    private static String requireTeamName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 24) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "El nombre de equipo debe tener de 1 a 24 caracteres");
        }
        return name.strip();
    }

    // ---------------------------------------------------------------- consultas

    public String code() { return code; }
    public UUID hostToken() { return hostToken; }
    public UUID claimToken() { return claimToken; }
    public RoomStatus status() { return status; }
    public int totalRounds() { return totalRounds; }
    public int targetScore() { return targetScore; }
    /** AC-46: 0 = sin temporizador. */
    public int turnSeconds() { return turnSeconds; }
    /** AC-48. */
    public boolean autoStrikeOnTimeout() { return autoStrikeOnTimeout; }
    /** AC-53. */
    public boolean soundEnabled() { return soundEnabled; }
    /** AC-47: vencimiento vigente del turno, si hay. */
    public Optional<Instant> turnDeadline() { return currentRound().flatMap(Round::turnDeadline); }
    public Team team(TeamId id) { return teams.get(id); }
    public Collection<Team> teams() { return teams.values(); }
    public Optional<Round> currentRound() { return Optional.ofNullable(currentRound); }
    /** "A", "B" o "TIE" solo en GAME_OVER (AC-22). */
    public Optional<String> winner() { return Optional.ofNullable(winner); }
    public long version() { return version; }
    public Instant lastActivity() { return lastActivity; }

    /** AC-32: true cuando el QR de presentador vigente ya se canjeó. */
    public boolean presenterConnected() {
        return claimUsed;
    }

    /** AC-26: el token de la laptop o el del presentador que canjeó el QR vigente. */
    public boolean isPresenterToken(String token) {
        if (token == null) {
            return false;
        }
        return hostToken.toString().equals(token)
                || (claimedPresenterToken != null && claimedPresenterToken.toString().equals(token));
    }

    public Optional<Player> playerByToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return allPlayers().stream().filter(p -> p.token().toString().equals(token)).findFirst();
    }

    public Optional<Player> player(UUID playerId) {
        return allPlayers().stream().filter(p -> p.id().equals(playerId)).findFirst();
    }

    public List<Player> allPlayers() {
        List<Player> all = new ArrayList<>();
        teams.values().forEach(t -> all.addAll(t.players()));
        return all;
    }

    /** Devuelve y limpia los eventos producidos desde la última llamada. */
    public List<DomainEvent> drainEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public void touch(Instant now) {
        lastActivity = now;
    }

    // ---------------------------------------------------------------- presentador (AC-30, AC-33)

    /** AC-30: canje de un solo uso del QR de presentador. */
    public UUID claimPresenter(String token) {
        if (token == null || !claimToken.toString().equals(token)) {
            throw new GameException(ErrorCode.CLAIM_INVALID, "El código de presentador no es válido");
        }
        if (claimUsed) {
            throw new GameException(ErrorCode.CLAIM_USED, "Este QR de presentador ya se usó");
        }
        claimUsed = true;
        claimedPresenterToken = UUID.randomUUID();
        emit(DomainEvent.of(GameEventType.PRESENTER_CLAIMED));
        changed();
        return claimedPresenterToken;
    }

    /** AC-33: nuevo QR de presentador; invalida el anterior y la sesión del celular que lo canjeó. */
    public UUID regenerateClaim() {
        claimToken = UUID.randomUUID();
        claimUsed = false;
        claimedPresenterToken = null;
        changed();
        return claimToken;
    }

    // ---------------------------------------------------------------- sonido (AC-53)

    /** AC-53: silencia o activa el sonido de la TV en cualquier estado; sin cambio no publica nada. */
    public void setSound(boolean enabled) {
        if (soundEnabled == enabled) {
            return;
        }
        soundEnabled = enabled;
        emit(new DomainEvent(GameEventType.SOUND_TOGGLED, null, null, null, null, null, enabled));
        changed();
    }

    // ---------------------------------------------------------------- lobby

    /** AC-05, AC-06, AC-07. */
    public Player addPlayer(String rawName, TeamId team) {
        if (status != RoomStatus.LOBBY) {
            throw new GameException(ErrorCode.ROOM_NOT_IN_LOBBY, "La partida ya empezó; no se puede unir");
        }
        if (team == null) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "Elige un equipo");
        }
        String name = rawName == null ? "" : rawName.strip();
        if (name.length() < MIN_NAME || name.length() > MAX_NAME) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "El nombre debe tener de 2 a 20 caracteres");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (allPlayers().stream().anyMatch(p -> p.name().toLowerCase(Locale.ROOT).equals(normalized))) {
            throw new GameException(ErrorCode.NAME_TAKEN, "Ya hay un jugador llamado " + name);
        }
        Player player = new Player(UUID.randomUUID(), UUID.randomUUID(), name, team);
        teams.get(team).add(player);
        emit(new DomainEvent(GameEventType.PLAYER_JOINED, player.id(), team, null, null, null));
        changed();
        return player;
    }

    /**
     * Spec 003, AC-63, AC-64: el capitán nombra a su equipo durante el LOBBY. Orden de validación: capitán
     * ({@code NOT_CAPTAIN}), estado ({@code ROOM_NOT_IN_LOBBY}), longitud ({@code VALIDATION_ERROR}) y nombre del otro
     * equipo ({@code TEAM_NAME_TAKEN}). AC-65: publica {@code TEAM_RENAMED}; el mismo nombre exacto no cambia nada.
     */
    public void renameTeam(String playerToken, TeamId teamId, String rawName) {
        Team team = teamId == null ? null : teams.get(teamId);
        boolean isCaptain = team != null && playerByToken(playerToken)
                .flatMap(p -> team.captain().filter(c -> c.id().equals(p.id())))
                .isPresent();
        if (!isCaptain) {
            throw new GameException(ErrorCode.NOT_CAPTAIN, "Solo el capitán puede nombrar a su equipo");
        }
        if (status != RoomStatus.LOBBY) {
            throw new GameException(ErrorCode.ROOM_NOT_IN_LOBBY, "El nombre del equipo solo se cambia en la sala de espera");
        }
        String name = rawName == null ? "" : rawName.strip();
        if (name.length() < MIN_NAME || name.length() > MAX_NAME) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "El nombre del equipo debe tener de 2 a 20 caracteres");
        }
        Team other = teams.get(teamId == TeamId.A ? TeamId.B : TeamId.A);
        if (other.name().equalsIgnoreCase(name)) {
            throw new GameException(ErrorCode.TEAM_NAME_TAKEN, "El otro equipo ya se llama " + other.name());
        }
        if (team.name().equals(name)) {
            return;
        }
        team.rename(name);
        emit(DomainEvent.forTeam(GameEventType.TEAM_RENAMED, teamId));
        changed();
    }

    /** AC-09: conexión/desconexión del cliente STOMP del jugador. */
    public void setPlayerConnected(UUID playerId, boolean connected) {
        player(playerId).ifPresent(p -> {
            if (p.connected() == connected) {
                return;
            }
            p.setConnected(connected);
            if (!connected) {
                emit(new DomainEvent(GameEventType.PLAYER_LEFT, p.id(), p.team(), null, null, null));
            }
            changed();
        });
    }

    // ---------------------------------------------------------------- partida

    /** AC-10, AC-11: LOBBY → FACE_OFF de la ronda 1. */
    public void start(List<QuestionSnapshot> chosen) {
        requireStatus("start", RoomStatus.LOBBY);
        if (teams.values().stream().anyMatch(t -> t.players().isEmpty())) {
            throw new GameException(ErrorCode.TEAM_EMPTY, "Cada equipo necesita al menos un jugador");
        }
        if (chosen == null || chosen.size() < totalRounds
                || chosen.stream().map(QuestionSnapshot::id).distinct().count() < totalRounds) {
            throw new GameException(ErrorCode.NOT_ENOUGH_QUESTIONS,
                    "Se necesitan " + totalRounds + " preguntas distintas");
        }
        teams.values().forEach(Team::reset);
        questions = List.copyOf(chosen.subList(0, totalRounds));
        winner = null;
        emit(DomainEvent.of(GameEventType.GAME_STARTED));
        beginRound(1);
        changed();
    }

    /** AC-13, AC-14, AC-27: solo el jugador designado en FACE_OFF; gana el primero. AC-35: su equipo queda en turno. */
    public boolean buzz(UUID playerId, Instant now) {
        requireStatus("buzz", RoomStatus.FACE_OFF);
        if (!currentRound.faceOff().isDesignated(playerId)) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "No eres el jugador designado de esta ronda");
        }
        Player p = player(playerId).orElseThrow();
        if (!currentRound.faceOff().registerBuzz(playerId, p.team())) {
            return false;
        }
        emit(new DomainEvent(GameEventType.BUZZ_WON, playerId, p.team(), null, null, null));
        advanceTurn(p.team(), now);
        changed();
        return true;
    }

    /**
     * AC-15…AC-20: revela una respuesta acertada. Spec 006: en FACE_OFF resuelve el cara a cara (AC-92…AC-96) y sin
     * buzzer responde NO_ACTIVE_TEAM (AC-100).
     */
    public void reveal(long answerId, Instant now) {
        requireStatus("reveal", RoomStatus.FACE_OFF, RoomStatus.PLAYING, RoomStatus.STEAL);
        Slot slot = currentRound.slot(answerId);
        if (slot.revealed()) {
            throw new GameException(ErrorCode.INVALID_STATE, "Esa respuesta ya está revelada");
        }
        TeamId answeringTeam = currentRound.activeTeam().orElse(null);
        if (status == RoomStatus.FACE_OFF && answeringTeam == null) {
            throw new GameException(ErrorCode.NO_ACTIVE_TEAM, "Espera a que alguien gane el buzzer");
        }
        slot.reveal();
        if (status != RoomStatus.STEAL) {
            currentRound.addToPot(slot.points()); // AC-17
        }
        emit(new DomainEvent(GameEventType.ANSWER_REVEALED, null, answeringTeam, slot.position(), slot.points(), null));
        switch (status) {
            case FACE_OFF -> resolveFaceOff(currentRound.faceOff().answer(slot.position()), now);
            case PLAYING -> {
                if (currentRound.allRevealed()) {
                    closeRound(currentRound.controllingTeam().orElseThrow()); // AC-18
                }
                advanceTurn(null, now);
            }
            default -> { // STEAL: el robo acertado da el bote al que roba (AC-19)
                closeRound(GameRules.stealWinner(currentRound.controllingTeam().orElseThrow(), true));
                advanceTurn(null, now);
            }
        }
        changed();
    }

    /**
     * AC-15, AC-16, AC-18, AC-19: marca una X al equipo en turno (spec 002, AC-40…AC-43). Fuera de
     * FACE_OFF/PLAYING/STEAL → INVALID_STATE (C-6); sin equipo en turno (FACE_OFF sin buzz) → NO_ACTIVE_TEAM.
     */
    public void strike(Instant now) {
        requireStatus("strike", RoomStatus.FACE_OFF, RoomStatus.PLAYING, RoomStatus.STEAL);
        TeamId team = currentRound.activeTeam().orElseThrow(() -> new GameException(ErrorCode.NO_ACTIVE_TEAM,
                "No hay equipo en turno: espera el buzzer o asigna el control"));
        applyStrike(team, now);
        changed();
    }

    /**
     * AC-48, AC-49: vence el turno agendado. Solo actúa si {@code expectedDeadline} sigue siendo el vencimiento
     * vigente (una acción posterior lo reemplaza o lo borra), así un mismo vencimiento nunca aplica dos X.
     * Spec 005 (AC-83): el temporizador se detiene en 0 y no se reinicia; sin equipo en turno solo avisa (C-26).
     *
     * @return true si el vencimiento se aplicó
     */
    public boolean expireTurn(Instant expectedDeadline, Instant now) {
        if (expectedDeadline == null || !Objects.equals(turnDeadline().orElse(null), expectedDeadline)) {
            return false;
        }
        TeamId team = currentRound.activeTeam().orElse(null);
        currentRound.expireTimer(); // spec 005, AC-83: se queda en 0
        emit(DomainEvent.forTeam(GameEventType.TIMER_EXPIRED, team));
        // Spec 006 AC-98: en el cara a cara el vencimiento siempre es fallo; después, según "X automática".
        if (team != null && (status == RoomStatus.FACE_OFF || autoStrikeOnTimeout)) {
            applyStrike(team, now);
        }
        changed();
        return true;
    }

    /**
     * Spec 005 (AC-80, AC-81): el presentador inicia, para o restablece el temporizador. Fuera de
     * FACE_OFF/PLAYING/STEAL → INVALID_STATE; sala sin temporizador → TIMER_DISABLED. Sin cambio no hay evento.
     *
     * @return true si el temporizador cambió
     */
    public boolean controlTimer(TimerAction action, Instant now) {
        requireStatus("controlTimer", RoomStatus.FACE_OFF, RoomStatus.PLAYING, RoomStatus.STEAL);
        if (turnSeconds == GameRules.NO_TIMER) {
            throw new GameException(ErrorCode.TIMER_DISABLED, "La sala no tiene temporizador");
        }
        if (action == null) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "Indica la acción: START, PAUSE o RESET");
        }
        boolean timerChanged = switch (action) {
            case START -> currentRound.startTimer(now);
            case PAUSE -> currentRound.pauseTimer(now);
            case RESET -> currentRound.resetTimer();
        };
        if (timerChanged) {
            emit(DomainEvent.forTeam(GameEventType.TIMER_CHANGED, currentRound.activeTeam().orElse(null)));
            changed();
        }
        return timerChanged;
    }

    /** Spec 008 (AC-122): preguntas que no pueden salir al cambiar: las de esta partida y las ya descartadas. */
    public java.util.Set<Long> questionIdsToAvoid() {
        java.util.Set<Long> ids = new java.util.HashSet<>(discardedQuestions);
        questions.forEach(q -> ids.add(q.id()));
        return ids;
    }

    /**
     * Spec 008 (AC-122): reemplaza la pregunta de la ronda en FACE_OFF mientras no se haya revelado nada. El cara a
     * cara vuelve a empezar con los mismos designados y el temporizador completo; número y multiplicador se conservan.
     */
    public void changeQuestion(QuestionSnapshot replacement) {
        requireStatus("changeQuestion", RoomStatus.FACE_OFF);
        if (currentRound.slots().stream().anyMatch(Slot::revealed)) {
            throw new GameException(ErrorCode.INVALID_STATE,
                    "Ya se reveló una respuesta: no se puede cambiar la pregunta");
        }
        if (replacement == null) {
            throw new GameException(ErrorCode.NO_MORE_QUESTIONS, "No quedan preguntas disponibles para cambiar");
        }
        int index = currentRound.number() - 1;
        discardedQuestions.add(questions.get(index).id());
        List<QuestionSnapshot> next = new java.util.ArrayList<>(questions);
        next.set(index, replacement);
        questions = List.copyOf(next);
        FaceOff old = currentRound.faceOff();
        currentRound = new Round(currentRound.number(), currentRound.multiplier(), replacement,
                new FaceOff(old.playerA(), old.playerB()), turnSeconds * 1000L);
        emit(DomainEvent.of(GameEventType.QUESTION_CHANGED));
        changed();
    }

    /** Spec 005, AC-91: el presentador cierra la sala; los clientes reciben ROOM_CLOSED. */
    public void close() {
        if (currentRound != null) {
            currentRound.resetTimer();
        }
        emit(DomainEvent.of(GameEventType.ROOM_CLOSED));
        changed();
    }

    /** AC-41…AC-43: misma regla para la X manual y la automática. */
    private void applyStrike(TeamId team, Instant now) {
        switch (status) {
            case FACE_OFF -> { // spec 005 AC-85 / spec 006 AC-94, AC-97: no es X; pasa el turno o termina el cara a cara
                emit(DomainEvent.forTeam(GameEventType.FACE_OFF_MISS, team));
                resolveFaceOff(currentRound.faceOff().miss(), now);
            }
            case PLAYING -> {
                int strikes = currentRound.addStrike();
                emit(new DomainEvent(GameEventType.STRIKE, null, team, null, null, strikes));
                if (GameRules.opensSteal(strikes)) {
                    status = RoomStatus.STEAL;
                    emit(new DomainEvent(GameEventType.STEAL_STARTED, null, team.rival(), null, null, strikes));
                }
                advanceTurn(null, now);
            }
            default -> { // STEAL: la X es del equipo que roba
                int strikes = currentRound.addStealStrike(team);
                emit(new DomainEvent(GameEventType.STRIKE, null, team, null, null, strikes));
                closeRound(GameRules.stealWinner(team.rival(), false));
                advanceTurn(null, now);
            }
        }
    }

    /** AC-15 / spec 006 C-28: corrección manual del control en FACE_OFF; AC-35: queda en turno. */
    public void assignControl(TeamId team, Instant now) {
        requireStatus("assignControl", RoomStatus.FACE_OFF);
        if (team == null) {
            throw new GameException(ErrorCode.VALIDATION_ERROR, "Indica el equipo");
        }
        enterPlaying(team, now);
        changed();
    }

    /** Spec 006: aplica el resultado del cara a cara: pasa el turno o termina con el control para un equipo. */
    private void resolveFaceOff(FaceOff.Resolution resolution, Instant now) {
        if (resolution.ends()) {
            enterPlaying(resolution.team(), now);
        } else {
            advanceTurn(resolution.team(), now);
        }
    }

    /** AC-15, AC-99: FACE_OFF → PLAYING con {@code team}; si ya se reveló todo, la ronda es suya (AC-18). */
    private void enterPlaying(TeamId team, Instant now) {
        currentRound.setControllingTeam(team);
        status = RoomStatus.PLAYING;
        emit(new DomainEvent(GameEventType.CONTROL_ASSIGNED, null, team, null, null, null));
        if (currentRound.allRevealed()) {
            closeRound(team);
        }
        advanceTurn(null, now);
    }

    /** AC-22: ROUND_END → FACE_OFF de la siguiente ronda o GAME_OVER. */
    public void nextRound() {
        requireStatus("nextRound", RoomStatus.ROUND_END);
        int a = teams.get(TeamId.A).score();
        int b = teams.get(TeamId.B).score();
        if (GameRules.isGameOver(a, b, targetScore, currentRound.number(), totalRounds)) {
            status = RoomStatus.GAME_OVER;
            winner = GameRules.winner(a, b);
            TeamId winningTeam = "TIE".equals(winner) ? null : TeamId.valueOf(winner);
            emit(new DomainEvent(GameEventType.GAME_OVER, null, winningTeam, null, null, null));
        } else {
            beginRound(currentRound.number() + 1);
        }
        changed();
    }

    /** AC-23: GAME_OVER → LOBBY con los mismos jugadores; spec 003 AC-67: conserva nombres y capitanes. */
    public void rematch() {
        requireStatus("rematch", RoomStatus.GAME_OVER);
        teams.values().forEach(Team::reset);
        questions = List.of();
        currentRound = null;
        winner = null;
        status = RoomStatus.LOBBY;
        changed();
    }

    // ---------------------------------------------------------------- internos

    private void beginRound(int number) {
        Player a = teams.get(TeamId.A).nextFaceOffPlayer();
        Player b = teams.get(TeamId.B).nextFaceOffPlayer();
        currentRound = new Round(number, GameRules.multiplier(number, totalRounds), questions.get(number - 1),
                new FaceOff(a.id(), b.id()), turnSeconds * 1000L); // AC-79: detenido y completo
        status = RoomStatus.FACE_OFF;
    }

    private void closeRound(TeamId roundWinner) {
        currentRound.close(roundWinner);
        int award = GameRules.roundAward(currentRound.pot(), currentRound.multiplier());
        teams.get(roundWinner).addScore(award);
        status = RoomStatus.ROUND_END;
        emit(new DomainEvent(GameEventType.ROUND_ENDED, null, roundWinner, null, award, currentRound.strikes()));
    }

    /**
     * AC-35, AC-36: fija el equipo en turno según el estado (en FACE_OFF, {@code faceOffTurn}) y publica
     * TURN_CHANGED si cambió. Spec 005 (AC-79, C-25): ya no toca el temporizador.
     */
    private void advanceTurn(TeamId faceOffTurn, Instant now) {
        TeamId next = switch (status) {
            case FACE_OFF -> faceOffTurn;
            case PLAYING -> currentRound.controllingTeam().orElseThrow();
            case STEAL -> currentRound.controllingTeam().orElseThrow().rival();
            default -> null;
        };
        TeamId previous = currentRound.activeTeam().orElse(null);
        currentRound.setTurn(next);
        if (previous != next) {
            emit(DomainEvent.forTeam(GameEventType.TURN_CHANGED, next));
        }
    }

    private void requireStatus(String command, RoomStatus... allowed) {
        for (RoomStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw GameException.invalidState(status, command);
    }

    private void emit(DomainEvent event) {
        pendingEvents.add(event);
    }

    private void changed() {
        version++;
    }
}
