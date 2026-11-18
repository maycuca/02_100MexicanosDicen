package mx.cienmexicanos.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import mx.cienmexicanos.game.domain.GameEventType;
import mx.cienmexicanos.game.domain.TeamId;

/** Mensaje GameEvent de asyncapi.yaml ({@code /topic/rooms/{code}/events}); {@code enabled} solo en SOUND_TOGGLED. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameEventMessage(GameEventType type, long version, UUID playerId, TeamId team, Integer position,
                               Integer points, Integer strikes, Boolean enabled) {}
