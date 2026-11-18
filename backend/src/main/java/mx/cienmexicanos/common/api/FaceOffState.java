package mx.cienmexicanos.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import mx.cienmexicanos.game.domain.TeamId;

/** Propiedad RoundState.faceOff; spec 006 (AC-101): {@code firstTeam} y {@code firstAnswerPosition} (null si no aplican). */
public record FaceOffState(UUID playerA, UUID playerB, UUID buzzWinner,
                           @JsonInclude(JsonInclude.Include.ALWAYS) TeamId firstTeam,
                           @JsonInclude(JsonInclude.Include.ALWAYS) Integer firstAnswerPosition) {}
