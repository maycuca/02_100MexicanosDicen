package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-2 Unirse por QR — joinRoom y getMe (AC-05, AC-06, AC-07, AC-09). */
@Tag("acceptance")
@DisplayName("Aceptación · Unirse a la sala")
class JoinRoomAcceptanceTest extends AcceptanceTestBase {

    @Test
    @DisplayName("AC-05 un jugador se une con nombre y equipo → 201 con player y playerToken")
    void joinReturnsPlayerAndToken() {
        Room room = createRoom("{}");
        Resp r = expectStatus(joinRaw(room.code(), "Lupita", "A"), 201);

        assertThat((String) r.read("$.playerToken")).isNotBlank();
        assertThat((String) r.read("$.player.id")).matches("[0-9a-fA-F-]{36}");
        assertThat((String) r.read("$.player.name")).isEqualTo("Lupita");
        assertThat((String) r.read("$.player.team")).isEqualTo("A");
        assertThat((Boolean) r.read("$.player.connected")).isNotNull();

        Resp pub = expectStatus(getRoom(room.code()), 200);
        assertThat(pub.<String>list("$.teams[?(@.id == 'A')].players[*].name")).containsExactly("Lupita");
    }

    @ParameterizedTest(name = "AC-05 nombre de longitud válida en el límite: \"{0}\"")
    @ValueSource(strings = {"Al", "Maximiliano Ramírez"})
    void nameBoundariesAccepted(String name) {
        Room room = createRoom("{}");
        expectStatus(joinRaw(room.code(), name, "B"), 201);
    }

    @Test
    @DisplayName("AC-05 nombre de 20 caracteres exactos se acepta")
    void twentyCharsAccepted() {
        Room room = createRoom("{}");
        expectStatus(joinRaw(room.code(), "a".repeat(20), "A"), 201);
    }

    @ParameterizedTest(name = "AC-05 nombre inválido → 400: \"{0}\"")
    @ValueSource(strings = {"", "A", "abcdefghijklmnopqrstu"})
    void invalidNameRejected(String name) {
        Room room = createRoom("{}");
        Resp r = joinRaw(room.code(), name, "A");
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
    }

    @Test
    @DisplayName("AC-05 equipo inexistente o ausente → 400")
    void invalidTeamRejected() {
        Room room = createRoom("{}");
        assertThat(joinRaw(room.code(), "Pepe", "C").status()).isEqualTo(400);
        assertThat(joinRaw(room.code(), "Pepe", null).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("AC-05 unirse a una sala inexistente → 404 ROOM_NOT_FOUND")
    void joinUnknownRoom() {
        expectProblem(joinRaw("QZQZ", "Pepe", "A"), 404, "ROOM_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-06 nombre repetido sin distinguir mayúsculas → 409 NAME_TAKEN")
    void duplicateNameCaseInsensitive() {
        Room room = createRoom("{}");
        join(room.code(), "Lupita", "A");

        expectProblem(joinRaw(room.code(), "lupita", "B"), 409, "NAME_TAKEN");
        expectProblem(joinRaw(room.code(), "LUPITA", "A"), 409, "NAME_TAKEN");
    }

    @Test
    @DisplayName("AC-06 el mismo nombre sí se permite en otra sala")
    void sameNameInOtherRoom() {
        join(createRoom("{}").code(), "Lupita", "A");
        join(createRoom("{}").code(), "Lupita", "A");
    }

    @Test
    @DisplayName("AC-07 no se puede unir después de iniciar → 409 ROOM_NOT_IN_LOBBY")
    void cannotJoinAfterStart() {
        Game g = startedGame(3, 300);
        expectProblem(joinRaw(g.code(), uniqueName("Tarde"), "A"), 409, "ROOM_NOT_IN_LOBBY");
    }

    @Test
    @DisplayName("AC-09 GET /players/me con el playerToken recupera la identidad del jugador")
    void recoverSessionWithToken() {
        Room room = createRoom("{}");
        Joined p = join(room.code(), "Chabelo", "B");

        Resp me = expectStatus(get("/api/rooms/" + room.code() + "/players/me", player(p.token())), 200);
        assertThat((String) me.read("$.id")).isEqualTo(p.id());
        assertThat((String) me.read("$.name")).isEqualTo("Chabelo");
        assertThat((String) me.read("$.team")).isEqualTo("B");
    }

    @Test
    @DisplayName("AC-09 la sesión sigue recuperable después de iniciar la partida")
    void recoverSessionAfterStart() {
        Game g = startedGame(3, 300);
        Joined p = g.players().getFirst();
        Resp me = expectStatus(get("/api/rooms/" + g.code() + "/players/me", player(p.token())), 200);
        assertThat((String) me.read("$.id")).isEqualTo(p.id());
    }

    @Test
    @DisplayName("AC-09 GET /players/me sin token o con token inválido → 401")
    void meWithoutOrBadToken() {
        Room room = createRoom("{}");
        assertThat(get("/api/rooms/" + room.code() + "/players/me").status()).isEqualTo(401);
        assertThat(get("/api/rooms/" + room.code() + "/players/me", player("no-es-un-token")).status()).isEqualTo(401);
    }
}
