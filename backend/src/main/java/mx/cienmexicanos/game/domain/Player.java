package mx.cienmexicanos.game.domain;

import java.util.UUID;

/** Jugador de una sala. El {@code token} nunca se expone en las proyecciones. */
public final class Player {

    private final UUID id;
    private final UUID token;
    private final String name;
    private final TeamId team;
    private boolean connected;

    public Player(UUID id, UUID token, String name, TeamId team) {
        this.id = id;
        this.token = token;
        this.name = name;
        this.team = team;
        this.connected = true;
    }

    public UUID id() { return id; }
    public UUID token() { return token; }
    public String name() { return name; }
    public TeamId team() { return team; }
    public boolean connected() { return connected; }

    void setConnected(boolean connected) {
        this.connected = connected;
    }
}
