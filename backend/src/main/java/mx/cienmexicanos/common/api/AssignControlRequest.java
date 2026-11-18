package mx.cienmexicanos.common.api;

import jakarta.validation.constraints.NotNull;
import mx.cienmexicanos.game.domain.TeamId;

/** Esquema AssignControlRequest. */
public record AssignControlRequest(@NotNull TeamId team) {}
