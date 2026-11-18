package mx.cienmexicanos.game.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ronda en curso: tablero, strikes, bote y control (AC-15…AC-20); equipo en turno, X por equipo y vencimiento del
 * turno (spec 002, AC-35, AC-41, AC-47). Spec 005 (AC-79…AC-84): el temporizador solo lo mueve el presentador.
 */
public final class Round {

    private final int number;
    private final int multiplier;
    private final QuestionSnapshot question;
    private final List<Slot> slots;
    private final FaceOff faceOff;
    private int strikes;
    private int pot;
    private TeamId controllingTeam;
    private TeamId winner;
    private TeamId activeTeam;
    private final long timerFullMs;
    /** Spec 005, AC-82: corre mientras {@code turnDeadline} no es null. */
    private Instant turnDeadline;
    /** Con el temporizador detenido: lo que queda; corriendo: lo que quedaba al iniciarlo. */
    private long timerRemainingMs;
    private final Map<TeamId, Integer> strikesByTeam = new EnumMap<>(Map.of(TeamId.A, 0, TeamId.B, 0));

    Round(int number, int multiplier, QuestionSnapshot question, FaceOff faceOff) {
        this(number, multiplier, question, faceOff, 0);
    }

    /** AC-79: la ronda empieza con el temporizador detenido y completo. */
    Round(int number, int multiplier, QuestionSnapshot question, FaceOff faceOff, long timerFullMs) {
        this.timerFullMs = timerFullMs;
        this.timerRemainingMs = timerFullMs;
        this.number = number;
        this.multiplier = multiplier;
        this.question = question;
        this.slots = question.answers().stream().map(Slot::new).toList();
        this.faceOff = faceOff;
    }

    public int number() { return number; }
    public int multiplier() { return multiplier; }
    public QuestionSnapshot question() { return question; }
    public List<Slot> slots() { return Collections.unmodifiableList(slots); }
    public FaceOff faceOff() { return faceOff; }
    public int strikes() { return strikes; }
    public int pot() { return pot; }
    public Optional<TeamId> controllingTeam() { return Optional.ofNullable(controllingTeam); }
    /** Equipo que se llevó el bote; vacío mientras la ronda no termina. */
    public Optional<TeamId> winner() { return Optional.ofNullable(winner); }
    /** AC-35: equipo en turno; vacío si nadie está respondiendo. */
    public Optional<TeamId> activeTeam() { return Optional.ofNullable(activeTeam); }
    /** AC-47, AC-82: vencimiento del turno; vacío si el temporizador no está corriendo. */
    public Optional<Instant> turnDeadline() { return Optional.ofNullable(turnDeadline); }
    /** AC-82. */
    public boolean timerRunning() { return turnDeadline != null; }
    /** AC-82: detenido, lo que queda; corriendo, lo que quedaba al iniciarlo (la cuenta usa {@code turnDeadline}). */
    public long timerRemainingMs() { return timerRemainingMs; }
    /** AC-41: X que cuentan por equipo. */
    public int strikesOf(TeamId team) { return strikesByTeam.get(team); }

    boolean allRevealed() {
        return slots.stream().allMatch(Slot::revealed);
    }

    Slot slot(long answerId) {
        return slots.stream()
                .filter(s -> s.answerId() == answerId)
                .findFirst()
                .orElseThrow(() -> new GameException(ErrorCode.ANSWER_NOT_FOUND,
                        "La respuesta " + answerId + " no pertenece a la ronda actual"));
    }

    void addToPot(int points) {
        pot += points;
    }

    /** AC-16, AC-41: X del equipo con control en PLAYING; cuenta en {@code strikes} y en {@code strikesByTeam}. */
    int addStrike() {
        strikes++;
        strikesByTeam.put(controllingTeam, strikes);
        return strikes;
    }

    /** AC-41: la X del equipo que roba; no cambia {@code strikes}. */
    int addStealStrike(TeamId stealingTeam) {
        return strikesByTeam.merge(stealingTeam, 1, Integer::sum);
    }

    /** AC-35; spec 005 C-25: cambiar de turno no toca el temporizador. */
    void setTurn(TeamId team) {
        activeTeam = team;
    }

    /** AC-80: corre desde lo que queda, o desde la duración completa si queda 0. */
    boolean startTimer(Instant now) {
        if (turnDeadline != null) {
            return false;
        }
        if (timerRemainingMs <= 0) {
            timerRemainingMs = timerFullMs;
        }
        turnDeadline = now.plusMillis(timerRemainingMs);
        return true;
    }

    /** AC-80: congela lo que queda. */
    boolean pauseTimer(Instant now) {
        if (turnDeadline == null) {
            return false;
        }
        timerRemainingMs = Math.max(0, Duration.between(now, turnDeadline).toMillis());
        turnDeadline = null;
        return true;
    }

    /** AC-80, AC-84: detenido y con la duración completa. */
    boolean resetTimer() {
        if (turnDeadline == null && timerRemainingMs == timerFullMs) {
            return false;
        }
        turnDeadline = null;
        timerRemainingMs = timerFullMs;
        return true;
    }

    /** AC-83: se detiene en 0 y no se reinicia. */
    void expireTimer() {
        turnDeadline = null;
        timerRemainingMs = 0;
    }

    void setControllingTeam(TeamId team) {
        controllingTeam = team;
    }

    /** AC-20: revela lo que queda en el tablero y fija al ganador del bote. */
    void close(TeamId winner) {
        this.winner = winner;
        resetTimer(); // AC-84
        slots.forEach(Slot::reveal);
    }
}
