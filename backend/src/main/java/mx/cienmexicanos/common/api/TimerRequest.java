package mx.cienmexicanos.common.api;

import jakarta.validation.constraints.NotNull;
import mx.cienmexicanos.game.domain.TimerAction;

/** Esquema TimerRequest (spec 005, AC-81). */
public record TimerRequest(@NotNull TimerAction action) {}
