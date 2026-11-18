package mx.cienmexicanos.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla {@code game_result} (historial, data-model.md). */
@Entity
@Table(name = "game_result")
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false)
    private String roomCode;
    @Column(name = "team_a_name", nullable = false)
    private String teamAName;
    @Column(name = "team_b_name", nullable = false)
    private String teamBName;
    @Column(name = "team_a_score", nullable = false)
    private int teamAScore;
    @Column(name = "team_b_score", nullable = false)
    private int teamBScore;
    @Column(name = "winner", nullable = false)
    private String winner;
    @Column(name = "finished_at", nullable = false)
    private String finishedAt;

    protected GameResult() {}

    public GameResult(String roomCode, String teamAName, String teamBName, int teamAScore, int teamBScore,
                      String winner, String finishedAt) {
        this.roomCode = roomCode;
        this.teamAName = teamAName;
        this.teamBName = teamBName;
        this.teamAScore = teamAScore;
        this.teamBScore = teamBScore;
        this.winner = winner;
        this.finishedAt = finishedAt;
    }

    public Long getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getTeamAName() { return teamAName; }
    public String getTeamBName() { return teamBName; }
    public int getTeamAScore() { return teamAScore; }
    public int getTeamBScore() { return teamBScore; }
    public String getWinner() { return winner; }
    public String getFinishedAt() { return finishedAt; }
}
