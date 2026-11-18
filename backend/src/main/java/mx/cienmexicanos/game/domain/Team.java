package mx.cienmexicanos.game.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Equipo: jugadores en orden de llegada, rotación del cara a cara (AC-12) y capitán (spec 003, AC-62). */
public final class Team {

    private final TeamId id;
    private String name;
    private final List<Player> players = new ArrayList<>();
    private int score;
    private int nextFaceOffIndex;

    Team(TeamId id, String name) {
        this.id = id;
        this.name = name;
    }

    public TeamId id() { return id; }
    public String name() { return name; }
    public int score() { return score; }
    public List<Player> players() { return Collections.unmodifiableList(players); }

    /** AC-62: el primer jugador que se unió; vacío si aún no hay jugadores. Los jugadores nunca se eliminan. */
    public Optional<Player> captain() {
        return players.isEmpty() ? Optional.empty() : Optional.of(players.getFirst());
    }

    void rename(String newName) {
        this.name = newName;
    }

    void add(Player player) {
        players.add(player);
    }

    void addScore(int points) {
        score += points;
    }

    /** Siguiente jugador designado, rotando en orden de llegada (AC-12). */
    Player nextFaceOffPlayer() {
        Player p = players.get(nextFaceOffIndex % players.size());
        nextFaceOffIndex++;
        return p;
    }

    void reset() {
        score = 0;
        nextFaceOffIndex = 0;
    }
}
