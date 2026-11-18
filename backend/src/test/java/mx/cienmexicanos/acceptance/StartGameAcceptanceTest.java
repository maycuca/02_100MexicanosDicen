package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** US-3 Iniciar partida y rotación del cara a cara — startGame (AC-10, AC-11, AC-12). */
@Tag("acceptance")
@DisplayName("Aceptación · Iniciar partida")
class StartGameAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("AC-10 no se puede iniciar si un equipo no tiene jugadores → 409 TEAM_EMPTY")
    void cannotStartWithEmptyTeam() {
        Room room = createRoom("{}");
        Game g = new Game(room, List.of(join(room.code(), uniqueName("Ana"), "A")));

        expectProblem(start(g), 409, "TEAM_EMPTY");
        assertThat((String) getRoom(room.code()).read("$.status")).isEqualTo("LOBBY");
    }

    @Test
    @DisplayName("AC-10 no se puede iniciar una sala vacía → 409 TEAM_EMPTY")
    void cannotStartEmptyRoom() {
        Room room = createRoom("{}");
        expectProblem(start(new Game(room, List.of())), 409, "TEAM_EMPTY");
    }

    @Test
    @DisplayName("AC-11 al iniciar la sala pasa a FACE_OFF de la ronda 1 con una pregunta y su tablero oculto")
    void startMovesToFaceOffRoundOne() {
        Game g = lobbyWithTwoPlayers(5, 300);
        Resp r = expectStatus(start(g), 200);

        assertThat((String) r.read("$.status")).isEqualTo("FACE_OFF");
        assertThat(r.intAt("$.round.number")).isEqualTo(1);
        assertThat(r.intAt("$.round.strikes")).isZero();
        assertThat(r.intAt("$.round.pot")).isZero();
        assertThat((String) r.read("$.round.question")).isNotBlank();
        List<Slot> board = presenterBoard(r);
        // data-model.md: 3–8 respuestas; RNF-5: 4–8 en el contenido inicial.
        assertThat(board).hasSizeBetween(3, 8);
        assertThat(board).noneMatch(Slot::revealed);
        assertThat(board).extracting(Slot::position).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, board.size()).boxed().toList());
        assertThat(board.stream().mapToInt(Slot::points).sum()).isLessThanOrEqualTo(100);
        assertThat(board.stream().map(Slot::points).toList())
                .as("posición 1 = la más popular")
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());

        Resp pub = expectStatus(getRoom(g.code()), 200);
        assertThat((String) pub.read("$.status")).isEqualTo("FACE_OFF");
        assertThat((List<?>) pub.read("$.round.board")).hasSize(board.size());
    }

    @Test
    @DisplayName("AC-11 la partida usa preguntas distintas en cada ronda")
    void distinctQuestionsPerRound() {
        int rounds = 8;
        Game g = startedGame(rounds, 1000);
        List<String> questions = new ArrayList<>();
        for (int i = 1; i <= rounds; i++) {
            Resp pv = presenterView(g);
            assertThat(pv.intAt("$.round.number")).isEqualTo(i);
            questions.add(pv.read("$.round.question"));
            playRoundWithEmptyPot(g, "A");
            expectStatus(nextRound(g), 200);
        }
        assertThat(questions).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("AC-12 hay un designado por equipo y rota en orden de llegada al lobby")
    void faceOffRotatesInArrivalOrder() {
        Room room = createRoom(4, 1000);
        Joined a1 = join(room.code(), uniqueName("Ana"), "A");
        Joined b1 = join(room.code(), uniqueName("Beto"), "B");
        Joined a2 = join(room.code(), uniqueName("Alma"), "A");
        Joined b2 = join(room.code(), uniqueName("Bruno"), "B");
        Joined a3 = join(room.code(), uniqueName("Abril"), "A");
        Game g = new Game(room, List.of(a1, b1, a2, b2, a3));
        expectStatus(start(g), 200);

        List<String> expectedA = List.of(a1.id(), a2.id(), a3.id(), a1.id());
        List<String> expectedB = List.of(b1.id(), b2.id(), b1.id(), b2.id());
        for (int i = 0; i < 4; i++) {
            Resp pv = presenterView(g);
            assertThat((String) pv.read("$.round.faceOff.playerA")).as("designado A ronda %d", i + 1).isEqualTo(expectedA.get(i));
            assertThat((String) pv.read("$.round.faceOff.playerB")).as("designado B ronda %d", i + 1).isEqualTo(expectedB.get(i));
            assertThat((Object) pv.readOrNull("$.round.faceOff.buzzWinner")).isNull();
            playRoundWithEmptyPot(g, i % 2 == 0 ? "A" : "B");
            expectStatus(nextRound(g), 200);
        }
    }
}
