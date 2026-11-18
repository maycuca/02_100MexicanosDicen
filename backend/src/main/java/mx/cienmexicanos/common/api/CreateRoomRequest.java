package mx.cienmexicanos.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import mx.cienmexicanos.game.domain.GameRules;

/**
 * Esquema CreateRoomRequest; los campos ausentes toman el default del contrato (AC-02, AC-46). El hueco 1–9 de
 * {@code turnSeconds} lo rechaza el dominio con VALIDATION_ERROR.
 */
public record CreateRoomRequest(@Size(min = 1, max = 24) String teamAName,
                                @Size(min = 1, max = 24) String teamBName,
                                @Min(3) @Max(8) Integer rounds,
                                @Min(50) @Max(1000) Integer targetScore,
                                @Min(0) @Max(60) Integer turnSeconds,
                                Boolean autoStrikeOnTimeout) {

    public static final String DEFAULT_TEAM_A = "Equipo Rojo";
    public static final String DEFAULT_TEAM_B = "Equipo Verde";
    public static final int DEFAULT_ROUNDS = 5;
    public static final int DEFAULT_TARGET = 300;

    public static CreateRoomRequest defaults() {
        return new CreateRoomRequest(null, null, null, null, null, null);
    }

    public String teamANameOrDefault() { return teamAName == null ? DEFAULT_TEAM_A : teamAName; }
    public String teamBNameOrDefault() { return teamBName == null ? DEFAULT_TEAM_B : teamBName; }
    public int roundsOrDefault() { return rounds == null ? DEFAULT_ROUNDS : rounds; }
    public int targetScoreOrDefault() { return targetScore == null ? DEFAULT_TARGET : targetScore; }
    public int turnSecondsOrDefault() { return turnSeconds == null ? GameRules.DEFAULT_TURN_SECONDS : turnSeconds; }
    public boolean autoStrikeOnTimeoutOrDefault() { return autoStrikeOnTimeout == null || autoStrikeOnTimeout; }
}
