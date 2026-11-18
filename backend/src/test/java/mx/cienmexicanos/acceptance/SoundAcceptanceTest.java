package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 002 US-14 — El presentador silencia o reactiva el sonido de la TV: {@code POST /api/rooms/{code}/sound},
 * {@code RoomState.soundEnabled} y el evento {@code SOUND_TOGGLED} (AC-53). T724.
 */
@Tag("acceptance")
@DisplayName("Aceptación 002 · Sonido de la TV")
class SoundAcceptanceTest extends AcceptanceTestBase {

    private Resp soundRaw(Game g, String body, String... headers) {
        return post("/api/rooms/" + g.code() + "/sound", body, headers);
    }

    @Test
    @DisplayName("AC-53 soundEnabled = true por defecto en el snapshot público y en el del presentador")
    void soundEnabledByDefault() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        assertThat((Boolean) expectStatus(getRoom(g.code()), 200).read("$.soundEnabled")).isTrue();
        assertThat((Boolean) presenterView(g).read("$.soundEnabled")).isTrue();
    }

    @Test
    @DisplayName("AC-53 silenciar y reactivar: 200 con PresenterRoomState, soundEnabled cambia y la versión sube")
    void toggleSound() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v0 = presenterView(g).intAt("$.version");

        Resp off = expectStatus(setSound(g, false), 200);
        assertThat(off.contentType()).startsWith("application/json");
        assertThat((Boolean) off.read("$.soundEnabled")).isFalse();
        assertThat(off.roomStatus()).as("el sonido no cambia el estado del juego").isEqualTo("LOBBY");
        assertThat(off.intAt("$.version")).isGreaterThan(v0);
        assertThat((Boolean) expectStatus(getRoom(g.code()), 200).read("$.soundEnabled")).isFalse();

        Resp on = expectStatus(setSound(g, true), 200);
        assertThat((Boolean) on.read("$.soundEnabled")).isTrue();
        assertThat(on.intAt("$.version")).isGreaterThan(off.intAt("$.version"));
        assertThat((Boolean) expectStatus(getRoom(g.code()), 200).read("$.soundEnabled")).isTrue();
    }

    @Test
    @DisplayName("AC-53 se puede silenciar en plena ronda sin alterar la partida")
    void toggleDuringRound() {
        Game g = startedGame(3, 1000);
        Resp control = winFaceOff(g, "A");
        Resp off = expectStatus(setSound(g, false), 200);
        assertThat((Boolean) off.read("$.soundEnabled")).isFalse();
        assertThat(off.roomStatus()).isEqualTo("PLAYING");
        assertThat(off.intAt("$.round.pot")).isEqualTo(control.intAt("$.round.pot"));
        assertThat(off.intAt("$.round.strikes")).isEqualTo(control.intAt("$.round.strikes"));
        assertThat(activeTeam(off)).isEqualTo("A");
        assertThat((Object) off.read("$.presenterBoard")).as("respuesta PresenterRoomState").isNotNull();
    }

    @Test
    @DisplayName("AC-53 SOUND_TOGGLED con enabled y el snapshot de /state con soundEnabled")
    void soundToggledEventAndBroadcast() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            int mark = w.mark();
            Resp off = expectStatus(setSound(g, false), 200);
            String event = w.awaitEvent(mark, "SOUND_TOGGLED", TIMEOUT);
            assertThat((Boolean) JsonPath.read(event, "$.enabled")).isFalse();
            assertThat((Object) JsonPath.read(event, "$.version")).isInstanceOf(Number.class);
            String snapshot = w.awaitStateVersion(off.intAt("$.version"), TIMEOUT);
            assertThat((Boolean) JsonPath.read(snapshot, "$.soundEnabled")).isFalse();

            int mark2 = w.mark();
            Resp on = expectStatus(setSound(g, true), 200);
            assertThat((Boolean) JsonPath.read(w.awaitEvent(mark2, "SOUND_TOGGLED", TIMEOUT), "$.enabled")).isTrue();
            assertThat((Boolean) JsonPath.read(w.awaitStateVersion(on.intAt("$.version"), TIMEOUT), "$.soundEnabled")).isTrue();
        }
    }

    // ------------------------------------------------------------------ seguridad (AC-26 aplicado a AC-53)

    @Test
    @DisplayName("AC-53 AC-26 POST /sound sin X-Presenter-Token → 401 y la sala no cambia")
    void missingTokenIs401() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int version = presenterView(g).intAt("$.version");
        Resp r = soundRaw(g, "{\"enabled\":false}");
        expectProblem(r, 401, "UNAUTHORIZED");
        assertThat(r.contentType()).startsWith("application/problem+json");
        Resp after = presenterView(g);
        assertThat((Boolean) after.read("$.soundEnabled")).isTrue();
        assertThat(after.intAt("$.version")).isEqualTo(version);
    }

    @Test
    @DisplayName("AC-53 AC-26 POST /sound con token de otra sala → 403 y la sala no cambia")
    void otherRoomTokenIs403() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Room other = createRoom("{}");
        int version = presenterView(g).intAt("$.version");
        Resp r = soundRaw(g, "{\"enabled\":false}", presenter(other.presenterToken()));
        expectProblem(r, 403, "FORBIDDEN");
        Resp after = presenterView(g);
        assertThat((Boolean) after.read("$.soundEnabled")).isTrue();
        assertThat(after.intAt("$.version")).isEqualTo(version);
    }

    @Test
    @DisplayName("AC-53 AC-26 un playerToken no sirve para silenciar la TV → 401/403")
    void playerTokenCannotToggle() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Resp r = soundRaw(g, "{\"enabled\":false}", presenter(g.players().getFirst().token()));
        assertThat(r.status()).as("respuesta: %s", r).isIn(401, 403);
        assertThat((Boolean) presenterView(g).read("$.soundEnabled")).isTrue();
    }

    @Test
    @DisplayName("AC-53 POST /sound en una sala inexistente → 404 ROOM_NOT_FOUND")
    void unknownRoomIs404() {
        Room room = createRoom("{}");
        Resp r = post("/api/rooms/QZQZ/sound", "{\"enabled\":false}", presenter(room.presenterToken()));
        expectProblem(r, 404, "ROOM_NOT_FOUND");
    }

    @ParameterizedTest(name = "AC-53 cuerpo inválido → 400: {0}")
    @ValueSource(strings = {"{}", "{\"enabled\":null}", "{\"enabled\":\"quizá\"}", "no-es-json"})
    void invalidBodyIs400(String body) {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Resp r = soundRaw(g, body, presenter(g.token()));
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertThat((Boolean) presenterView(g).read("$.soundEnabled")).isTrue();
    }
}
