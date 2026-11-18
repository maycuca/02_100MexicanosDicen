package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Spec 008 US-34 — Cambiar la pregunta de la ronda (AC-122). T1321. */
@Tag("acceptance")
@DisplayName("Aceptación 008 · Cambiar pregunta")
class ChangeQuestionAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("AC-122 cambiar la pregunta publica QUESTION_CHANGED, reinicia el buzzer y nunca repite una pregunta de la sala")
    void changesWithoutRepeating() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            Resp started = expectStatus(start(g), 200);
            Set<String> seen = new HashSet<>();
            seen.add((String) started.read("$.round.question"));
            buzzBy(g, "A");
            int mark = w.mark();

            Resp changed = expectStatus(changeQuestion(g), 200);
            w.awaitEvent(mark, "QUESTION_CHANGED", TIMEOUT);
            assertThat(changed.roomStatus()).isEqualTo("FACE_OFF");
            assertThat((Object) changed.readOrNull("$.round.faceOff.buzzWinner")).as("sin buzzer").isNull();
            assertThat((Object) changed.readOrNull("$.round.activeTeam")).isNull();
            assertThat(seen.add((String) changed.read("$.round.question"))).as("pregunta nueva").isTrue();
            assertThat((String) expectStatus(getRoom(g.code()), 200).read("$.round.question"))
                    .isEqualTo(changed.read("$.round.question"));

            for (int i = 0; i < 4; i++) {
                String q = expectStatus(changeQuestion(g), 200).read("$.round.question");
                assertThat(seen.add(q)).as("AC-122 no se repite: %s", q).isTrue();
            }
        }
    }

    @Test
    @DisplayName("AC-122 409 INVALID_STATE en LOBBY, tras revelar y en PLAYING; 401 sin token y 403 con token ajeno")
    void errors() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        expectProblem(changeQuestion(g), 409, "INVALID_STATE");
        expectStatus(start(g), 200);
        expectProblem(post("/api/rooms/" + g.code() + "/rounds/current/question", null), 401, "UNAUTHORIZED");
        expectProblem(post("/api/rooms/" + g.code() + "/rounds/current/question", null, presenter("otro")), 403, "FORBIDDEN");
        buzzBy(g, "A");
        expectStatus(reveal(g, slotAt(g, 2).answerId()), 200);
        expectProblem(changeQuestion(g), 409, "INVALID_STATE");
        expectStatus(reveal(g, slotAt(g, 1).answerId()), 200); // B gana el control → PLAYING
        expectProblem(changeQuestion(g), 409, "INVALID_STATE");
    }
}
