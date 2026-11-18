package mx.cienmexicanos.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import mx.cienmexicanos.game.domain.TeamId;

/** Esquema Team; {@code captainId} siempre presente, null sin jugadores (spec 003, AC-62). */
public record TeamDto(TeamId id, String name, @JsonInclude(JsonInclude.Include.ALWAYS) UUID captainId, int score,
                      List<PlayerDto> players) {}
