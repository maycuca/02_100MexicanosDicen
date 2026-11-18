package mx.cienmexicanos.game.domain;

import java.time.Instant;

/** Reglas puras de puntuación, fin de partida (AC-17…AC-22) y temporizador de turno (spec 002, AC-46, AC-47). */
public final class GameRules {

    public static final int MAX_STRIKES = 3;
    public static final int MIN_ROUNDS = 3;
    public static final int MAX_ROUNDS = 8;
    /** AC-46: 0 = sin temporizador; si no, entre 10 y 60 s (20 por defecto). */
    public static final int NO_TIMER = 0;
    public static final int MIN_TURN_SECONDS = 10;
    public static final int MAX_TURN_SECONDS = 60;
    public static final int DEFAULT_TURN_SECONDS = 20;

    private GameRules() {}

    /** AC-21: ×3 en la última ronda, ×2 en la penúltima, ×1 en el resto. */
    public static int multiplier(int round, int totalRounds) {
        if (round < 1 || round > totalRounds) {
            throw new IllegalArgumentException("Ronda fuera de rango: " + round + "/" + totalRounds);
        }
        if (round == totalRounds) {
            return 3;
        }
        if (round == totalRounds - 1) {
            return 2;
        }
        return 1;
    }

    /** AC-18: tres strikes abren el robo. */
    public static boolean opensSteal(int strikes) {
        return strikes >= MAX_STRIKES;
    }

    /** AC-19: en el robo, un acierto da el bote al rival; una X lo deja con el equipo con control. */
    public static TeamId stealWinner(TeamId controllingTeam, boolean stealSucceeded) {
        return stealSucceeded ? controllingTeam.rival() : controllingTeam;
    }

    /** AC-20: puntos que suma el ganador de la ronda. */
    public static int roundAward(int pot, int multiplier) {
        return pot * multiplier;
    }

    /** AC-22: termina si alguien alcanzó la meta o si se jugó la última ronda. */
    public static boolean isGameOver(int scoreA, int scoreB, int targetScore, int round, int totalRounds) {
        return scoreA >= targetScore || scoreB >= targetScore || round >= totalRounds;
    }

    /** AC-22: "A", "B" o "TIE". */
    public static String winner(int scoreA, int scoreB) {
        if (scoreA == scoreB) {
            return "TIE";
        }
        return scoreA > scoreB ? TeamId.A.name() : TeamId.B.name();
    }

    /** AC-46: 0 o 10–60. */
    public static boolean isValidTurnSeconds(int turnSeconds) {
        return turnSeconds == NO_TIMER || (turnSeconds >= MIN_TURN_SECONDS && turnSeconds <= MAX_TURN_SECONDS);
    }

    /** AC-47: vencimiento del turno, o null si no hay equipo en turno o no hay temporizador. */
    public static Instant turnDeadline(TeamId activeTeam, int turnSeconds, Instant now) {
        if (activeTeam == null || turnSeconds == NO_TIMER) {
            return null;
        }
        return now.plusSeconds(turnSeconds);
    }
}
