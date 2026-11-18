package mx.cienmexicanos.game.domain;

import java.util.UUID;

/** Evento puntual producido por un comando; los campos opcionales pueden ser null. */
public record DomainEvent(GameEventType type, UUID playerId, TeamId team, Integer position, Integer points,
                          Integer strikes, Boolean enabled) {

    public DomainEvent(GameEventType type, UUID playerId, TeamId team, Integer position, Integer points,
                       Integer strikes) {
        this(type, playerId, team, position, points, strikes, null);
    }

    static DomainEvent of(GameEventType type) {
        return new DomainEvent(type, null, null, null, null, null);
    }

    static DomainEvent forTeam(GameEventType type, TeamId team) {
        return new DomainEvent(type, null, team, null, null, null);
    }
}
