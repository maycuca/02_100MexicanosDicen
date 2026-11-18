package mx.cienmexicanos.common.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.cienmexicanos.game.domain.TeamId;

/** Esquema JoinRoomRequest (AC-05). */
public record JoinRoomRequest(@NotNull @Size(min = 2, max = 20) String name, @NotNull TeamId team) {}
