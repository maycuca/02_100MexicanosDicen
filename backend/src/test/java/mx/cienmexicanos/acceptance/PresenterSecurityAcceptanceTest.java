package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** US-9 Seguridad mínima: X-Presenter-Token en toda acción de presentador (AC-26). */
@Tag("acceptance")
@DisplayName("Aceptación · Seguridad del presentador")
class PresenterSecurityAcceptanceTest extends AcceptanceTestBase {

    /** (método, path relativo a /api/rooms/{code}, cuerpo) de cada operación protegida en openapi.yaml. */
    static Stream<Arguments> presenterOperations() {
        return Stream.of(
                Arguments.of("getPresenterView", "GET", "/presenter-view", null),
                Arguments.of("regeneratePresenterClaim", "POST", "/presenter/claim-token", null),
                Arguments.of("startGame", "POST", "/start", null),
                Arguments.of("revealAnswer", "POST", "/rounds/current/answers/1/reveal", null),
                Arguments.of("markStrike", "POST", "/rounds/current/strike", null),
                Arguments.of("assignControl", "POST", "/rounds/current/control", "{\"team\":\"A\"}"),
                Arguments.of("nextRound", "POST", "/next-round", null),
                Arguments.of("rematch", "POST", "/rematch", null),
                // Spec 002 AC-53 (T727): operación protegida nueva del contrato v0.4.0.
                Arguments.of("setSound", "POST", "/sound", "{\"enabled\":false}"));
    }

    @ParameterizedTest(name = "AC-26 {0} sin X-Presenter-Token → 401")
    @MethodSource("presenterOperations")
    void missingTokenIs401(String op, String method, String path, String body) {
        Game g = lobbyWithTwoPlayers(3, 300);
        int version = presenterView(g).intAt("$.version");

        Resp r = send(method, "/api/rooms/" + g.code() + path, body);
        assertThat(r.status()).as("%s: %s", op, r).isEqualTo(401);
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertThat(presenterView(g).intAt("$.version")).as("la sala no cambió").isEqualTo(version);
    }

    @ParameterizedTest(name = "AC-26 {0} con token de otra sala → 403")
    @MethodSource("presenterOperations")
    void otherRoomTokenIs403(String op, String method, String path, String body) {
        Game g = lobbyWithTwoPlayers(3, 300);
        Room other = createRoom("{}");
        int version = presenterView(g).intAt("$.version");

        Resp r = send(method, "/api/rooms/" + g.code() + path, body, presenter(other.presenterToken()));
        assertThat(r.status()).as("%s: %s", op, r).isEqualTo(403);
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertThat(presenterView(g).intAt("$.version")).as("la sala no cambió").isEqualTo(version);
    }

    @ParameterizedTest(name = "AC-26 {0} con token inventado → 401/403")
    @MethodSource("presenterOperations")
    void bogusTokenIsRejected(String op, String method, String path, String body) {
        Game g = lobbyWithTwoPlayers(3, 300);
        Resp r = send(method, "/api/rooms/" + g.code() + path, body, presenter("00000000-0000-0000-0000-000000000000"));
        assertThat(r.status()).as("%s: %s", op, r).isIn(401, 403);
    }

    @Test
    @DisplayName("AC-26 un playerToken no sirve como X-Presenter-Token")
    void playerTokenIsNotPresenterToken() {
        Game g = lobbyWithTwoPlayers(3, 300);
        Resp r = start(new Game(new Room(g.code(), g.players().getFirst().token(), null, null), g.players()));
        assertThat(r.status()).as("respuesta: %s", r).isIn(401, 403);
        assertThat((String) getRoom(g.code()).read("$.status")).isEqualTo("LOBBY");
    }

    @Test
    @DisplayName("AC-26 GET /api/questions sin token → 401; con token válido → 200 con respuestas")
    void questionBankRequiresPresenter() {
        assertThat(get("/api/questions").status()).isEqualTo(401);
        Room room = createRoom("{}");
        Resp r = expectStatus(get("/api/questions", presenter(room.presenterToken())), 200);
        assertThat((java.util.List<?>) r.read("$")).hasSizeGreaterThanOrEqualTo(10);
        assertThat((java.util.List<?>) r.read("$[0].answers")).isNotEmpty();
    }

    @Test
    @DisplayName("AC-26 con el token correcto la acción se ejecuta")
    void validTokenWorks() {
        Game g = lobbyWithTwoPlayers(3, 300);
        assertThat(expectStatus(start(g), 200).roomStatus()).isEqualTo("FACE_OFF");
    }
}
