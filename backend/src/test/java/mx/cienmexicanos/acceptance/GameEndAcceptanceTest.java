package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** US-7 Multiplicadores, fin de partida y revancha — nextRound, rematch (AC-20…AC-23). */
@Tag("acceptance")
@DisplayName("Aceptación · Multiplicadores y fin de partida")
class GameEndAcceptanceTest extends AcceptanceTestBase {

    @ParameterizedTest(name = "AC-21 con {0} rondas los multiplicadores son {1}")
    @CsvSource({
        "3, 1-2-3",
        "4, 1-1-2-3",
        "5, 1-1-1-2-3",
    })
    void multipliersPerRound(int rounds, String expected) {
        List<Integer> multipliers = java.util.Arrays.stream(expected.split("-")).map(Integer::valueOf).toList();
        Game g = startedGame(rounds, 1000);
        int expectedA = 0;
        for (int i = 0; i < rounds; i++) {
            Resp pv = presenterView(g);
            assertThat(pv.intAt("$.round.number")).isEqualTo(i + 1);
            assertThat(pv.intAt("$.round.multiplier")).as("multiplicador ronda %d", i + 1).isEqualTo(multipliers.get(i));

            int pot = playRoundKeepingPot(g, "A");
            expectedA += pot * multipliers.get(i);
            Resp end = presenterView(g);
            assertThat(scoreOf(end, "A")).as("AC-20 bote × multiplicador al cerrar la ronda %d", i + 1).isEqualTo(expectedA);

            Resp next = expectStatus(nextRound(g), 200);
            if (i < rounds - 1) {
                assertThat(next.roomStatus()).isEqualTo("FACE_OFF");
                assertThat(next.intAt("$.round.number")).isEqualTo(i + 2);
                assertThat(next.intAt("$.round.strikes")).isZero();
                assertThat(next.intAt("$.round.pot")).isZero();
            }
        }
    }

    @Test
    @DisplayName("AC-22 la partida termina al cierre de la ronda en que un equipo alcanza la meta")
    void gameOverWhenTargetReached() {
        Game g = startedGame(5, 50);
        List<Slot> board = board(g);
        int total = board.stream().mapToInt(Slot::points).sum();
        assumeTrue(total >= 50, "la pregunta sorteada suma al menos 50 puntos");

        winFaceOff(g, "B");
        for (Slot s : board.subList(1, board.size())) {
            expectStatus(reveal(g, s.answerId()), 200);
        }
        Resp end = presenterView(g);
        assertThat(end.roomStatus()).isEqualTo("ROUND_END");
        assertThat(scoreOf(end, "B")).isGreaterThanOrEqualTo(50);

        Resp over = expectStatus(nextRound(g), 200);
        assertThat(over.roomStatus()).isEqualTo("GAME_OVER");
        assertThat((String) over.read("$.winner")).isEqualTo("B");
        assertThat((String) getRoom(g.code()).read("$.winner")).isEqualTo("B");
    }

    @Test
    @DisplayName("AC-22 la partida termina al acabar todas las rondas y gana el de mayor marcador")
    void gameOverAfterLastRound() {
        Game g = startedGame(3, 1000);
        playRoundKeepingPot(g, "A");
        expectStatus(nextRound(g), 200);
        playRoundWithEmptyPot(g, "B");
        expectStatus(nextRound(g), 200);
        playRoundWithEmptyPot(g, "B");
        Resp over = expectStatus(nextRound(g), 200);

        assertThat(over.roomStatus()).isEqualTo("GAME_OVER");
        assertThat(scoreOf(over, "A")).isPositive();
        assertThat(scoreOf(over, "B")).isZero();
        assertThat((String) over.read("$.winner")).isEqualTo("A");
    }

    @Test
    @DisplayName("AC-22 empate al terminar todas las rondas → winner TIE")
    void tieAtTheEnd() {
        Game g = startedGame(3, 1000);
        for (int i = 0; i < 3; i++) {
            playRoundWithEmptyPot(g, i % 2 == 0 ? "A" : "B");
            expectStatus(nextRound(g), 200);
        }
        Resp over = presenterView(g);
        assertThat(over.roomStatus()).isEqualTo("GAME_OVER");
        assertThat(scoreOf(over, "A")).isEqualTo(scoreOf(over, "B"));
        assertThat((String) over.read("$.winner")).isEqualTo("TIE");
        assertThat((String) getRoom(g.code()).read("$.winner")).isEqualTo("TIE");
    }

    @Test
    @DisplayName("AC-22 winner es null mientras la partida no termina")
    void winnerNullBeforeGameOver() {
        Game g = startedGame(3, 1000);
        assertThat((Object) getRoom(g.code()).readOrNull("$.winner")).isNull();
    }

    @Test
    @DisplayName("AC-23 revancha desde GAME_OVER: vuelve a LOBBY con los mismos jugadores y marcador en cero")
    void rematchReturnsToLobby() {
        Game g = startedGame(3, 1000);
        playRoundKeepingPot(g, "A");
        expectStatus(nextRound(g), 200);
        playRoundWithEmptyPot(g, "A");
        expectStatus(nextRound(g), 200);
        playRoundWithEmptyPot(g, "A");
        expectStatus(nextRound(g), 200);

        Resp lobby = expectStatus(rematch(g), 200);
        assertThat(lobby.roomStatus()).isEqualTo("LOBBY");
        assertThat(lobby.<Integer>list("$.teams[*].score")).containsOnly(0);
        assertThat(lobby.<String>list("$.teams[*].players[*].id"))
                .containsExactlyInAnyOrderElementsOf(g.players().stream().map(Joined::id).toList());
        assertThat((Object) lobby.readOrNull("$.round")).isNull();
        assertThat((Object) lobby.readOrNull("$.winner")).isNull();

        // Los mismos jugadores pueden jugar otra vez.
        Resp again = expectStatus(start(g), 200);
        assertThat(again.roomStatus()).isEqualTo("FACE_OFF");
        assertThat(again.intAt("$.round.number")).isEqualTo(1);
    }
}
