package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * AC-25: los jugadores nunca reciben texto ni puntos de respuestas no reveladas, ni por REST público
 * ({@code GET /api/rooms/{code}}) ni por {@code /topic/rooms/{code}/state}.
 */
@Tag("acceptance")
@DisplayName("Aceptación · Sin fuga de respuestas ocultas (AC-25)")
class DataLeakAcceptanceTest extends AcceptanceTestBase {

    /** Verifica un snapshot público contra las respuestas que siguen ocultas. */
    static void assertNoHiddenData(String json, List<Slot> hidden, List<Slot> revealed) {
        assertThat(json).as("la vista pública no incluye el tablero del presentador").doesNotContain("presenterBoard");
        assertThat(json).as("la vista pública no expone ids de respuestas").doesNotContain("answerId");
        for (Slot s : hidden) {
            assertThat(json).as("texto oculto '%s' filtrado en: %s", s.text(), json).doesNotContain(quote(s.text()));
        }
        List<Map<String, Object>> board;
        try {
            board = JsonPath.read(json, "$.round.board");
        } catch (PathNotFoundException | ClassCastException e) {
            return; // sin ronda (LOBBY) no hay tablero
        }
        if (board == null) {
            return;
        }
        for (Map<String, Object> slot : board) {
            int position = ((Number) slot.get("position")).intValue();
            if (Boolean.TRUE.equals(slot.get("revealed"))) {
                Slot expected = revealed.stream().filter(r -> r.position() == position).findFirst().orElse(null);
                if (expected != null) {
                    assertThat(slot.get("text")).isEqualTo(expected.text());
                    assertThat(((Number) slot.get("points")).intValue()).isEqualTo(expected.points());
                }
            } else {
                assertThat(slot.get("text")).as("text de la casilla oculta %d", position).isNull();
                assertThat(slot.get("points")).as("points de la casilla oculta %d", position).isNull();
            }
        }
    }

    @Test
    @DisplayName("AC-25 GET /api/rooms/{code} no contiene text/points de respuestas ocultas en ningún estado de la ronda")
    void restSnapshotHasNoHiddenAnswers() {
        Game g = startedGame(3, 1000);
        List<Slot> board = board(g);

        assertNoHiddenData(getRoom(g.code()).body(), board, List.of());           // FACE_OFF, nada revelado
        winFaceOff(g, "A");                                                       // revela #1
        assertNoHiddenData(getRoom(g.code()).body(), board.subList(1, board.size()), board.subList(0, 1));
        expectStatus(reveal(g, board.get(2).answerId()), 200);                    // revela #3
        List<Slot> hidden = board.stream().filter(s -> s.position() != 1 && s.position() != 3).toList();
        List<Slot> shown = board.stream().filter(s -> s.position() == 1 || s.position() == 3).toList();
        assertNoHiddenData(getRoom(g.code()).body(), hidden, shown);
        strikeUntilSteal(g);
        assertNoHiddenData(getRoom(g.code()).body(), hidden, shown);              // STEAL
    }

    @Test
    @DisplayName("AC-25 los snapshots de /topic/rooms/{code}/state no contienen text/points de respuestas ocultas")
    void stompSnapshotsHaveNoHiddenAnswers() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Joined spectator = join(g.code(), uniqueName("Mirón"), "A");
        // Sesión aparte para el intento de espiar: el servidor puede rechazarlo con ERROR y cerrar esa sesión.
        try (StompTestClient playerClient = connectPlayer(spectator);
                StompTestClient sneakyClient = connectPlayer(spectator);
                StompTestClient presenterClient = connectPresenter(g)) {
            StompTestClient.Inbox state = playerClient.subscribe(stateTopic(g.code()));
            StompTestClient.Inbox sneaky = sneakyClient.subscribe(presenterTopic(g.code()));
            StompTestClient.Inbox presenterInbox = presenterClient.subscribe(presenterTopic(g.code()));

            expectStatus(start(g), 200);
            List<Slot> board = board(g);
            winFaceOff(g, "A");
            expectStatus(reveal(g, board.get(1).answerId()), 200);
            expectStatus(strike(g), 200);

            String last = state.await(json -> "PLAYING".equals(JsonPath.read(json, "$.status"))
                    && ((Number) JsonPath.read(json, "$.round.strikes")).intValue() == 1, TIMEOUT).payload();
            assertThat(last).isNotBlank();
            // Control positivo: el presentador autenticado sí recibe el tablero completo.
            presenterInbox.await(json -> json.contains("presenterBoard"), TIMEOUT);

            List<Slot> hidden = board.subList(2, board.size());
            List<Slot> shown = board.subList(0, 2);
            for (String json : state.payloads()) {
                assertNoHiddenData(json, hidden, shown);
            }
            assertThat(sneaky.payloads())
                    .as("un jugador no recibe mensajes de /topic/rooms/{code}/presenter")
                    .isEmpty();
        }
    }
}
