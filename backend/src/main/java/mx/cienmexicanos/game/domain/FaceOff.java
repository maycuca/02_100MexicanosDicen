package mx.cienmexicanos.game.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Cara a cara de una ronda: un jugador designado por equipo (AC-12) y el primer buzz (AC-14).
 * Spec 006 (AC-92…AC-97): dos intentos como máximo y resolución automática del control.
 */
public final class FaceOff {

    /** Resultado de una respuesta o un fallo: el turno pasa a {@code team} o el cara a cara termina con su control. */
    public record Resolution(TeamId team, boolean ends) {
        static Resolution passTo(TeamId team) { return new Resolution(team, false); }
        static Resolution control(TeamId team) { return new Resolution(team, true); }
    }

    private final UUID playerA;
    private final UUID playerB;
    private UUID buzzWinner;
    /** Equipo del buzzer (primer intento); null antes del buzz. */
    private TeamId firstTeam;
    /** Posición que abrió el primer equipo; null si no abrió ninguna. */
    private Integer firstAnswerPosition;
    /** 0 antes del buzz, 1 = responde el primero, 2 = responde el segundo. */
    private int attempt;

    FaceOff(UUID playerA, UUID playerB) {
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public UUID playerA() { return playerA; }
    public UUID playerB() { return playerB; }
    public Optional<UUID> buzzWinner() { return Optional.ofNullable(buzzWinner); }
    public Optional<TeamId> firstTeam() { return Optional.ofNullable(firstTeam); }
    public Optional<Integer> firstAnswerPosition() { return Optional.ofNullable(firstAnswerPosition); }
    public int attempt() { return attempt; }

    boolean isDesignated(UUID playerId) {
        return playerA.equals(playerId) || playerB.equals(playerId);
    }

    /** @return true si este buzz es el primero; los posteriores se ignoran (AC-14). Su equipo responde primero (AC-92). */
    boolean registerBuzz(UUID playerId, TeamId team) {
        if (buzzWinner != null) {
            return false;
        }
        buzzWinner = playerId;
        firstTeam = team;
        attempt = 1;
        return true;
    }

    /**
     * AC-92, AC-93, AC-95, AC-96: el equipo en turno abrió la respuesta {@code position}.
     * Primer intento: la #1 da el control; otra pasa el turno al rival. Segundo intento: gana la mejor respuesta.
     */
    Resolution answer(int position) {
        if (attempt == 1) {
            if (position == 1) {
                return Resolution.control(firstTeam);
            }
            firstAnswerPosition = position;
            attempt = 2;
            return Resolution.passTo(firstTeam.rival());
        }
        TeamId second = firstTeam.rival();
        boolean beats = firstAnswerPosition == null || position < firstAnswerPosition;
        return Resolution.control(beats ? second : firstTeam);
    }

    /** AC-94, AC-97: fallo (botón o tiempo). Primero → turno al rival; segundo → control al otro equipo (el primero). */
    Resolution miss() {
        if (attempt == 1) {
            attempt = 2;
            return Resolution.passTo(firstTeam.rival());
        }
        return Resolution.control(firstTeam);
    }
}
