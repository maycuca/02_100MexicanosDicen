package mx.cienmexicanos.common.api;

import jakarta.validation.constraints.NotNull;

/** Esquema SetSoundRequest (spec 002, AC-53). */
public record SetSoundRequest(@NotNull Boolean enabled) {}
