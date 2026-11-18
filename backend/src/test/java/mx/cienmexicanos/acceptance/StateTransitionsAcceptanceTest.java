package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tabla de transiciones de data-model.md vista desde la API: todo comando fuera de la tabla → 409 INVALID_STATE,
 * y el estado de la sala no cambia.
 */
@Tag("acceptance")
@DisplayName("Aceptación · Transiciones inválidas (data-model.md)")
class StateTransitionsAcceptanceTest extends AcceptanceTestBase {

    private void expectInvalid(Game g, Function<Game, Resp> command, String expectedStatus) {
        int versionBefore = presenterView(g).intAt("$.version");
        Resp r = command.apply(g);
        expectProblem(r, 409, "INVALID_STATE");
        Resp after = presenterView(g);
        assertThat(after.roomStatus()).isEqualTo(expectedStatus);
        assertThat(after.intAt("$.version")).as("un comando rechazado no cambia la versión").isEqualTo(versionBefore);
    }

    private Game inLobby() {
        return lobbyWithTwoPlayers(3, 1000);
    }

    private Game inPlaying() {
        Game g = startedGame(3, 1000);
        winFaceOff(g, "A");
        return g;
    }

    private Game inSteal() {
        Game g = inPlaying();
        strikeUntilSteal(g);
        return g;
    }

    private Game inRoundEnd() {
        Game g = startedGame(3, 1000);
        playRoundKeepingPot(g, "A");
        return g;
    }

    private Game inGameOver() {
        Game g = startedGame(3, 1000);
        for (int i = 0; i < 3; i++) {
            playRoundWithEmptyPot(g, "A");
            expectStatus(nextRound(g), 200);
        }
        assertThat(presenterView(g).roomStatus()).isEqualTo("GAME_OVER");
        return g;
    }

    private long anyHiddenAnswer(Game g) {
        return board(g).stream().filter(s -> !s.revealed()).findFirst().orElseThrow().answerId();
    }

    @Test
    @DisplayName("LOBBY: reveal, strike, control, next-round y rematch → 409 INVALID_STATE")
    void lobby() {
        Game g = inLobby();
        expectInvalid(g, this::strike, "LOBBY");
        expectInvalid(g, x -> assignControl(x, "A"), "LOBBY");
        expectInvalid(g, this::nextRound, "LOBBY");
        expectInvalid(g, this::rematch, "LOBBY");
        Resp reveal = reveal(g, 1);
        assertThat(reveal.status()).as("reveal en LOBBY: %s", reveal).isIn(404, 409);
    }

    @Test
    @DisplayName("FACE_OFF: start, next-round y rematch → 409 INVALID_STATE")
    void faceOff() {
        Game g = startedGame(3, 1000);
        expectInvalid(g, this::start, "FACE_OFF");
        expectInvalid(g, this::nextRound, "FACE_OFF");
        expectInvalid(g, this::rematch, "FACE_OFF");
    }

    @Test
    @DisplayName("PLAYING: start, control, next-round y rematch → 409 INVALID_STATE")
    void playing() {
        Game g = inPlaying();
        expectInvalid(g, this::start, "PLAYING");
        expectInvalid(g, x -> assignControl(x, "B"), "PLAYING");
        expectInvalid(g, this::nextRound, "PLAYING");
        expectInvalid(g, this::rematch, "PLAYING");
    }

    @Test
    @DisplayName("PLAYING: revelar una respuesta ya revelada → 409 INVALID_STATE")
    void revealTwice() {
        Game g = inPlaying();
        long top = slotAt(g, 1).answerId();
        expectInvalid(g, x -> reveal(x, top), "PLAYING");
    }

    @Test
    @DisplayName("STEAL: start, control, next-round y rematch → 409 INVALID_STATE")
    void steal() {
        Game g = inSteal();
        expectInvalid(g, this::start, "STEAL");
        expectInvalid(g, x -> assignControl(x, "B"), "STEAL");
        expectInvalid(g, this::nextRound, "STEAL");
        expectInvalid(g, this::rematch, "STEAL");
    }

    @Test
    @DisplayName("ROUND_END: start, reveal, strike, control y rematch → 409 INVALID_STATE")
    void roundEnd() {
        Game g = inRoundEnd();
        expectInvalid(g, this::start, "ROUND_END");
        expectInvalid(g, this::strike, "ROUND_END");
        expectInvalid(g, x -> assignControl(x, "A"), "ROUND_END");
        expectInvalid(g, this::rematch, "ROUND_END");
        long any = board(g).getFirst().answerId();
        expectInvalid(g, x -> reveal(x, any), "ROUND_END");
    }

    @Test
    @DisplayName("GAME_OVER: start, reveal, strike, control y next-round → 409 INVALID_STATE")
    void gameOver() {
        Game g = inGameOver();
        expectInvalid(g, this::start, "GAME_OVER");
        expectInvalid(g, this::strike, "GAME_OVER");
        expectInvalid(g, x -> assignControl(x, "A"), "GAME_OVER");
        expectInvalid(g, this::nextRound, "GAME_OVER");
        Resp reveal = reveal(g, 1);
        assertThat(reveal.status()).as("reveal en GAME_OVER: %s", reveal).isIn(404, 409);
    }

    @Test
    @DisplayName("STEAL + reveal y STEAL + strike cierran la ronda (transiciones válidas)")
    void stealTransitionsAreValid() {
        Game g1 = inSteal();
        assertThat(expectStatus(reveal(g1, anyHiddenAnswer(g1)), 200).roomStatus()).isEqualTo("ROUND_END");
        Game g2 = inSteal();
        assertThat(expectStatus(strike(g2), 200).roomStatus()).isEqualTo("ROUND_END");
    }

    @Test
    @DisplayName("cada cambio de estado incrementa version")
    void versionIncreasesOnEveryChange() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v0 = presenterView(g).intAt("$.version");
        int v1 = expectStatus(start(g), 200).intAt("$.version");
        buzzBy(g, "A");
        int v2 = presenterView(g).intAt("$.version");
        int v3 = expectStatus(reveal(g, slotAt(g, 2).answerId()), 200).intAt("$.version"); // → turno de B
        int v4 = expectStatus(reveal(g, slotAt(g, 1).answerId()), 200).intAt("$.version"); // B gana el control (spec 006)
        int v5 = expectStatus(strike(g), 200).intAt("$.version");
        assertThat(java.util.List.of(v0, v1, v2, v3, v4, v5)).isSorted().doesNotHaveDuplicates();
    }
}
