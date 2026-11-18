package mx.cienmexicanos.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import mx.cienmexicanos.game.domain.TeamId;

/**
 * Esquema RoundState (público: el tablero usa {@link BoardSlot}); {@code potWinner} solo en ROUND_END.
 * Spec 002 (contrato v0.4.0): {@code activeTeam} (AC-35), {@code strikesByTeam} con las claves A y B (AC-41) y
 * {@code turnDeadline} en ISO-8601 UTC (AC-47) son requeridos: siempre se serializan, con {@code null} si no aplican.
 * Spec 005 (contrato v0.6.0, AC-82): {@code timerRunning} y {@code timerRemainingMs}.
 */
public record RoundState(int number, int multiplier, String question, int strikes, int pot,
                         @JsonInclude(JsonInclude.Include.ALWAYS) TeamId activeTeam,
                         @JsonInclude(JsonInclude.Include.ALWAYS) Map<TeamId, Integer> strikesByTeam,
                         @JsonInclude(JsonInclude.Include.ALWAYS) String turnDeadline,
                         boolean timerRunning, long timerRemainingMs,
                         TeamId controllingTeam, TeamId potWinner, FaceOffState faceOff, List<BoardSlot> board) {}
