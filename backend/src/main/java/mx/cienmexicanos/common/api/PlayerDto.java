package mx.cienmexicanos.common.api;

import java.util.UUID;
import mx.cienmexicanos.game.domain.TeamId;

/** Esquema Player. */
public record PlayerDto(UUID id, String name, TeamId team, boolean connected) {}
