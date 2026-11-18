package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 003 US-17 — Equipos nombrados por los jugadores: {@code Team.captainId} (AC-62),
 * {@code PUT /api/rooms/{code}/teams/{team}/name} con {@code X-Player-Token} (AC-63, AC-64), el evento
 * {@code TEAM_RENAMED} (AC-65) y la revancha que conserva nombres y capitanes (AC-67). Contrato v0.5.0. T821.
 */
@Tag("acceptance")
@DisplayName("Aceptación 003 · Capitanes y nombres de equipo")
class TeamCaptainAcceptanceTest extends AcceptanceTestBase {

    private static final String DEFAULT_A = "Equipo Rojo";
    private static final String DEFAULT_B = "Equipo Verde";

    // ------------------------------------------------------------------ utilidades

    private Resp renameRaw(String code, String team, String body, String... headers) {
        return send("PUT", "/api/rooms/" + code + "/teams/" + team + "/name", body, headers);
    }

    private Resp rename(String code, String team, String name, String playerToken) {
        return renameRaw(code, team, "{\"name\":" + quote(name) + "}", player(playerToken));
    }

    private static String teamName(Resp state, String team) {
        List<String> names = state.list("$.teams[?(@.id == '" + team + "')].name");
        assertThat(names).as("nombre del equipo %s en %s", team, state).hasSize(1);
        return names.getFirst();
    }

    private static String teamName(String json, String team) {
        List<String> names = JsonPath.read(json, "$.teams[?(@.id == '" + team + "')].name");
        return names.isEmpty() ? null : names.getFirst();
    }

    /** {@code captainId} del equipo; exige que la clave exista (requerida en v0.5.0) aunque valga null. */
    private static String captainOf(Resp state, String team) {
        List<Map<String, Object>> teams = state.list("$.teams[?(@.id == '" + team + "')]");
        assertThat(teams).as("equipo %s en %s", team, state).hasSize(1);
        assertThat(teams.getFirst()).as("AC-62 Team.captainId es requerido (v0.5.0) en %s", state).containsKey("captainId");
        return (String) teams.getFirst().get("captainId");
    }

    /** Ambas proyecciones (pública y del presentador) para afirmar lo mismo en las dos. */
    private List<Resp> projections(Game g) {
        return List.of(expectStatus(getRoom(g.code()), 200), presenterView(g));
    }

    private Joined captainA(Game g) {
        return g.players().get(0);
    }

    private Joined captainB(Game g) {
        return g.players().get(1);
    }

    /** Juega 3 rondas sin alcanzar la meta hasta GAME_OVER. */
    private void playToGameOver(Game g) {
        expectStatus(start(g), 200);
        playRoundKeepingPot(g, "A");
        expectStatus(nextRound(g), 200);
        playRoundWithEmptyPot(g, "A");
        expectStatus(nextRound(g), 200);
        playRoundWithEmptyPot(g, "A");
        Resp over = expectStatus(nextRound(g), 200);
        assertThat(over.roomStatus()).isEqualTo("GAME_OVER");
    }

    // ------------------------------------------------------------------ AC-62 captainId

    @Test
    @DisplayName("AC-62 sin jugadores captainId es null y la clave está presente en ambas proyecciones y en createRoom")
    void captainNullWithoutPlayers() {
        Room room = createRoom("{}");
        Resp created = room.created();
        for (String team : List.of("A", "B")) {
            List<Map<String, Object>> t = created.list("$.state.teams[?(@.id == '" + team + "')]");
            assertThat(t.getFirst()).as("CreateRoomResponse.state").containsKey("captainId");
            assertThat(t.getFirst().get("captainId")).isNull();
        }
        Game g = new Game(room, List.of());
        for (Resp state : projections(g)) {
            assertThat(captainOf(state, "A")).isNull();
            assertThat(captainOf(state, "B")).isNull();
        }
    }

    @Test
    @DisplayName("AC-62 el primer jugador de cada equipo es su capitán; los siguientes no lo cambian")
    void firstPlayerIsCaptain() {
        Room room = createRoom(3, 1000);
        Joined a1 = join(room.code(), uniqueName("Ana"), "A");
        Game onlyA = new Game(room, List.of(a1));
        for (Resp state : projections(onlyA)) {
            assertThat(captainOf(state, "A")).isEqualTo(a1.id());
            assertThat(captainOf(state, "B")).as("B aún sin jugadores").isNull();
        }

        Joined a2 = join(room.code(), uniqueName("Abi"), "A");
        Joined b1 = join(room.code(), uniqueName("Beto"), "B");
        Joined b2 = join(room.code(), uniqueName("Bere"), "B");
        Game g = new Game(room, List.of(a1, b1, a2, b2));
        for (Resp state : projections(g)) {
            assertThat(captainOf(state, "A")).isEqualTo(a1.id());
            assertThat(captainOf(state, "B")).isEqualTo(b1.id());
        }
    }

    @Test
    @DisplayName("AC-62 captainId viaja en el snapshot de /topic/rooms/{code}/state")
    void captainInStompSnapshot() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            String snapshot = w.awaitStateVersion(presenterView(g).intAt("$.version"), TIMEOUT);
            List<String> captains = JsonPath.read(snapshot, "$.teams[*].captainId");
            assertThat(captains).containsExactlyInAnyOrder(captainA(g).id(), captainB(g).id());
        }
    }

    // ------------------------------------------------------------------ AC-63 renombrar

    @Test
    @DisplayName("AC-63 el capitán renombra su equipo: 200 con RoomState, sube la versión y ambas proyecciones lo muestran")
    void captainRenames() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v0 = presenterView(g).intAt("$.version");

        Resp r = expectStatus(rename(g.code(), "A", "Los Tlacuaches", captainA(g).token()), 200);
        assertThat(r.contentType()).startsWith("application/json");
        assertThat(teamName(r, "A")).isEqualTo("Los Tlacuaches");
        assertThat(teamName(r, "B")).isEqualTo(DEFAULT_B);
        assertThat(r.roomStatus()).isEqualTo("LOBBY");
        assertThat(r.intAt("$.version")).isGreaterThan(v0);
        assertThat((Object) r.readOrNull("$.presenterBoard")).as("RoomState público, sin presenterBoard").isNull();
        assertThat(captainOf(r, "A")).isEqualTo(captainA(g).id());

        Resp b = expectStatus(rename(g.code(), "B", "Las Ajolotas", captainB(g).token()), 200);
        assertThat(teamName(b, "B")).isEqualTo("Las Ajolotas");
        for (Resp state : projections(g)) {
            assertThat(teamName(state, "A")).isEqualTo("Los Tlacuaches");
            assertThat(teamName(state, "B")).isEqualTo("Las Ajolotas");
        }
    }

    @Test
    @DisplayName("AC-63 se recortan los espacios antes de validar y de guardar")
    void trimsSpaces() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Resp r = expectStatus(rename(g.code(), "A", "   Los Chapulines  ", captainA(g).token()), 200);
        assertThat(teamName(r, "A")).isEqualTo("Los Chapulines");
        assertThat(teamName(expectStatus(getRoom(g.code()), 200), "A")).isEqualTo("Los Chapulines");
    }

    @ParameterizedTest(name = "AC-63 longitud válida en los límites: \"{0}\"")
    @ValueSource(strings = {"Yo", "Veinte caracteres ok", "  Yo  "})
    void validLengthLimits(String name) {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Resp r = expectStatus(rename(g.code(), "B", name, captainB(g).token()), 200);
        assertThat(teamName(r, "B")).isEqualTo(name.strip());
    }

    @Test
    @DisplayName("AC-63 renombrar al mismo nombre exacto es no-op: 200, sin cambio de versión")
    void sameNameIsNoOp() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Resp first = expectStatus(rename(g.code(), "A", "Los Tlacuaches", captainA(g).token()), 200);
        Resp again = expectStatus(rename(g.code(), "A", "Los Tlacuaches", captainA(g).token()), 200);
        assertThat(teamName(again, "A")).isEqualTo("Los Tlacuaches");
        assertThat(again.intAt("$.version")).as("no-op: la versión no sube").isEqualTo(first.intAt("$.version"));
        assertThat(presenterView(g).intAt("$.version")).isEqualTo(first.intAt("$.version"));
    }

    @Test
    @DisplayName("AC-63 el propio nombre con otras mayúsculas no choca: no es el del otro equipo")
    void ownNameDifferentCaseAllowed() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Resp r = expectStatus(rename(g.code(), "A", "EQUIPO ROJO", captainA(g).token()), 200);
        assertThat(teamName(r, "A")).isEqualTo("EQUIPO ROJO");
    }

    @Test
    @DisplayName("AC-63 el capitán puede renombrar varias veces durante el LOBBY")
    void renameSeveralTimes() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        expectStatus(rename(g.code(), "A", "Primero", captainA(g).token()), 200);
        Resp r = expectStatus(rename(g.code(), "A", "Segundo", captainA(g).token()), 200);
        assertThat(teamName(r, "A")).isEqualTo("Segundo");
    }

    @Test
    @DisplayName("AC-66 al unirse, el jugador recibe los equipos con su nombre actual (GET público)")
    void joinSeesCurrentName() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        expectStatus(rename(g.code(), "B", "Las Ajolotas", captainB(g).token()), 200);
        Joined late = join(g.code(), uniqueName("Ceci"), "B");
        Resp pub = expectStatus(getRoom(g.code()), 200);
        assertThat(teamName(pub, "B")).isEqualTo("Las Ajolotas");
        assertThat(captainOf(pub, "B")).as("el recién llegado no es capitán").isEqualTo(captainB(g).id()).isNotEqualTo(late.id());
    }

    // ------------------------------------------------------------------ AC-64 errores

    private void assertUnchanged(Game g, int version) {
        Resp after = presenterView(g);
        assertThat(teamName(after, "A")).isEqualTo(DEFAULT_A);
        assertThat(teamName(after, "B")).isEqualTo(DEFAULT_B);
        assertThat(after.intAt("$.version")).as("la sala no cambia").isEqualTo(version);
    }

    @Test
    @DisplayName("AC-64 sin X-Player-Token → 401 UNAUTHORIZED (problem+json) y la sala no cambia")
    void missingTokenIs401() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = renameRaw(g.code(), "A", "{\"name\":\"Los Tlacuaches\"}");
        expectProblem(r, 401, "UNAUTHORIZED");
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 el X-Presenter-Token no sustituye al X-Player-Token → 401")
    void presenterTokenOnlyIs401() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = renameRaw(g.code(), "A", "{\"name\":\"Los Tlacuaches\"}", presenter(g.token()));
        expectProblem(r, 401, "UNAUTHORIZED");
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 jugador del mismo equipo que no es capitán → 403 NOT_CAPTAIN")
    void sameTeamNonCaptainIs403() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Joined a2 = join(g.code(), uniqueName("Abi"), "A");
        int v = presenterView(g).intAt("$.version");
        Resp r = rename(g.code(), "A", "Los Tlacuaches", a2.token());
        expectProblem(r, 403, "NOT_CAPTAIN");
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 el capitán del otro equipo no puede renombrar el ajeno → 403 NOT_CAPTAIN")
    void otherTeamCaptainIs403() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        expectProblem(rename(g.code(), "A", "Los Tlacuaches", captainB(g).token()), 403, "NOT_CAPTAIN");
        expectProblem(rename(g.code(), "B", "Las Ajolotas", captainA(g).token()), 403, "NOT_CAPTAIN");
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 token de capitán de otra sala → 403 NOT_CAPTAIN")
    void otherRoomTokenIs403() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Game other = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = rename(g.code(), "A", "Los Tlacuaches", captainA(other).token());
        expectProblem(r, 403, "NOT_CAPTAIN");
        assertUnchanged(g, v);
        assertThat(teamName(presenterView(other), "A")).as("tampoco cambia la otra sala").isEqualTo(DEFAULT_A);
    }

    @Test
    @DisplayName("AC-64 token desconocido → 401 o 403 y la sala no cambia")
    void unknownTokenRejected() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = rename(g.code(), "A", "Los Tlacuaches", "token-que-no-existe");
        assertThat(r.status()).as("respuesta: %s", r).isIn(401, 403);
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 sala inexistente → 404 ROOM_NOT_FOUND")
    void unknownRoomIs404() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Resp r = rename("QZQZ", "A", "Los Tlacuaches", captainA(g).token());
        expectProblem(r, 404, "ROOM_NOT_FOUND");
    }

    @Test
    @DisplayName("AC-64 equipo que no es A ni B → 400 VALIDATION_ERROR")
    void unknownTeamRejected() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        expectProblem(rename(g.code(), "C", "Los Tlacuaches", captainA(g).token()), 400, "VALIDATION_ERROR");
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 el presenterToken enviado como X-Player-Token → 403 NOT_CAPTAIN")
    void presenterTokenAsPlayerTokenIs403() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        expectProblem(rename(g.code(), "A", "Los Tlacuaches", g.token()), 403, "NOT_CAPTAIN");
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 orden de errores: un no capitán con nombre inválido recibe 403 (antes que 400/409)")
    void notCaptainCheckedBeforeValidation() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        expectProblem(rename(g.code(), "A", "X", captainB(g).token()), 403, "NOT_CAPTAIN");
        expectProblem(rename(g.code(), "A", "equipo verde", captainB(g).token()), 403, "NOT_CAPTAIN");
    }

    @Test
    @DisplayName("AC-64 fuera de LOBBY → 409 ROOM_NOT_IN_LOBBY (capitán legítimo)")
    void outsideLobbyIs409() {
        Game g = startedGame(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = rename(g.code(), "A", "Los Tlacuaches", captainA(g).token());
        expectProblem(r, 409, "ROOM_NOT_IN_LOBBY");
        assertThat(r.contentType()).startsWith("application/problem+json");
        Resp after = presenterView(g);
        assertThat(teamName(after, "A")).isEqualTo(DEFAULT_A);
        assertThat(after.intAt("$.version")).isEqualTo(v);
    }

    @ParameterizedTest(name = "AC-64 nombre del otro equipo sin distinguir mayúsculas → 409 TEAM_NAME_TAKEN: \"{0}\"")
    @ValueSource(strings = {"Equipo Verde", "equipo verde", "EQUIPO VERDE", "  eQuIpO vErDe  "})
    void defaultNameTakenIs409(String name) {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = rename(g.code(), "A", name, captainA(g).token());
        expectProblem(r, 409, "TEAM_NAME_TAKEN");
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertUnchanged(g, v);
    }

    @Test
    @DisplayName("AC-64 tras renombrar B, A no puede usar el nombre nuevo de B (case-insensitive) → 409 TEAM_NAME_TAKEN")
    void renamedNameTakenIs409() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        expectStatus(rename(g.code(), "B", "Los Tigres", captainB(g).token()), 200);
        int v = presenterView(g).intAt("$.version");
        expectProblem(rename(g.code(), "A", "LOS TIGRES", captainA(g).token()), 409, "TEAM_NAME_TAKEN");
        Resp after = presenterView(g);
        assertThat(teamName(after, "A")).isEqualTo(DEFAULT_A);
        assertThat(after.intAt("$.version")).isEqualTo(v);
        // El nombre anterior de B queda libre.
        Resp freed = expectStatus(rename(g.code(), "A", "Equipo Verde", captainA(g).token()), 200);
        assertThat(teamName(freed, "A")).isEqualTo("Equipo Verde");
    }

    @ParameterizedTest(name = "AC-64 longitud inválida → 400 VALIDATION_ERROR: \"{0}\"")
    @ValueSource(strings = {"Y", "Veintiún caracteres!!", "", "     ", "   Y   "})
    void invalidLengthIs400(String name) {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = rename(g.code(), "A", name, captainA(g).token());
        expectProblem(r, 400, "VALIDATION_ERROR");
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertUnchanged(g, v);
    }

    @ParameterizedTest(name = "AC-64 cuerpo inválido → 400: {0}")
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":[\"a\",\"b\"]}", "no-es-json"})
    void invalidBodyIs400(String body) {
        Game g = lobbyWithTwoPlayers(3, 1000);
        int v = presenterView(g).intAt("$.version");
        Resp r = renameRaw(g.code(), "A", body, player(captainA(g).token()));
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
        assertUnchanged(g, v);
    }

    // ------------------------------------------------------------------ AC-65 TEAM_RENAMED

    @Test
    @DisplayName("AC-65 el cambio publica TEAM_RENAMED (con team y version) y el snapshot nuevo en /state en ≤ 1 s")
    void teamRenamedEventWithinOneSecond() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            int mark = w.mark();
            int stateMark = w.state().size();
            long t0 = System.nanoTime();
            Resp r = expectStatus(rename(g.code(), "B", "Las Ajolotas", captainB(g).token()), 200);

            StompTestClient.Received event = w.events().awaitFrom(mark, json -> isEvent(json, "TEAM_RENAMED"), TIMEOUT);
            assertThat(Duration.ofNanos(event.atNanos() - t0)).as("AC-65 latencia de TEAM_RENAMED").isLessThanOrEqualTo(Duration.ofSeconds(1));
            assertThat((String) JsonPath.read(event.payload(), "$.team")).isEqualTo("B");
            assertThat((Object) JsonPath.read(event.payload(), "$.version")).isInstanceOf(Number.class);

            StompTestClient.Received snapshot = w.state().awaitFrom(stateMark,
                    json -> "Las Ajolotas".equals(teamName(json, "B")), TIMEOUT);
            assertThat(Duration.ofNanos(snapshot.atNanos() - t0)).as("AC-65 latencia del snapshot").isLessThanOrEqualTo(Duration.ofSeconds(1));
            assertThat(((Number) JsonPath.read(snapshot.payload(), "$.version")).intValue()).isGreaterThanOrEqualTo(r.intAt("$.version"));
            assertThat(teamName(snapshot.payload(), "A")).isEqualTo(DEFAULT_A);
        }
    }

    @Test
    @DisplayName("AC-65 un celular de jugador suscrito a /state recibe el nombre nuevo")
    void playerReceivesNewName() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        Joined other = captainB(g);
        try (StompTestClient phone = connectPlayer(other)) {
            StompTestClient.Inbox state = phone.subscribe(stateTopic(g.code()));
            // Calentamiento: el broker simple no confirma la suscripción.
            for (int i = 0; i < 5; i++) {
                Joined warm = join(g.code(), uniqueName("Mira"), "B");
                if (state.tryAwait(json -> JsonPath.<List<String>>read(json, "$.teams[*].players[*].id").contains(warm.id()),
                        Duration.ofSeconds(1)).isPresent()) {
                    break;
                }
            }
            expectStatus(rename(g.code(), "A", "Los Tlacuaches", captainA(g).token()), 200);
            state.await(json -> "Los Tlacuaches".equals(teamName(json, "A")), TIMEOUT);
        }
    }

    @Test
    @DisplayName("AC-65 un rechazo (409/403/400) no publica TEAM_RENAMED")
    void rejectedRenamePublishesNothing() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (Watch w = watch(g)) {
            int mark = w.mark();
            expectProblem(rename(g.code(), "A", "equipo verde", captainA(g).token()), 409, "TEAM_NAME_TAKEN");
            expectProblem(rename(g.code(), "A", "Otro Nombre", captainB(g).token()), 403, "NOT_CAPTAIN");
            expectProblem(rename(g.code(), "A", "X", captainA(g).token()), 400, "VALIDATION_ERROR");
            // Un cambio válido posterior sirve de barrera: cuando llega, los rechazos ya habrían publicado.
            expectStatus(rename(g.code(), "B", "Barrera", captainB(g).token()), 200);
            String barrier = w.awaitEvent(mark, "TEAM_RENAMED", TIMEOUT);
            assertThat((String) JsonPath.read(barrier, "$.team")).isEqualTo("B");
            assertThat(w.eventsOf("TEAM_RENAMED", mark)).as("solo el cambio válido publica").hasSize(1);
        }
    }

    // ------------------------------------------------------------------ AC-67 revancha

    @Test
    @DisplayName("AC-67 la revancha conserva los nombres y los capitanes; el capitán puede volver a renombrar")
    void rematchKeepsNamesAndCaptains() {
        Game two = lobbyWithTwoPlayers(3, 1000);
        Joined a2 = join(two.code(), uniqueName("Abi"), "A");
        // Se registra en la partida: puede ser el designado de un cara a cara (buzzBy lo busca por id).
        Game g = new Game(two.room(), List.of(two.players().get(0), two.players().get(1), a2));
        expectStatus(rename(g.code(), "A", "Los Tlacuaches", captainA(g).token()), 200);
        expectStatus(rename(g.code(), "B", "Las Ajolotas", captainB(g).token()), 200);

        playToGameOver(g);
        Resp lobby = expectStatus(rematch(g), 200);
        assertThat(lobby.roomStatus()).isEqualTo("LOBBY");
        assertThat(teamName(lobby, "A")).isEqualTo("Los Tlacuaches");
        assertThat(teamName(lobby, "B")).isEqualTo("Las Ajolotas");
        assertThat(captainOf(lobby, "A")).isEqualTo(captainA(g).id());
        assertThat(captainOf(lobby, "B")).isEqualTo(captainB(g).id());
        for (Resp state : projections(g)) {
            assertThat(teamName(state, "A")).isEqualTo("Los Tlacuaches");
            assertThat(teamName(state, "B")).isEqualTo("Las Ajolotas");
            assertThat(captainOf(state, "A")).isEqualTo(captainA(g).id());
            assertThat(captainOf(state, "B")).isEqualTo(captainB(g).id());
        }

        // De vuelta en LOBBY, el mismo capitán puede renombrar; el no capitán sigue sin poder.
        Resp again = expectStatus(rename(g.code(), "A", "Los Cacomixtles", captainA(g).token()), 200);
        assertThat(teamName(again, "A")).isEqualTo("Los Cacomixtles");
        expectProblem(rename(g.code(), "A", "Usurpadores", a2.token()), 403, "NOT_CAPTAIN");
    }

    @Test
    @DisplayName("AC-64 en GAME_OVER renombrar → 409 ROOM_NOT_IN_LOBBY")
    void gameOverIs409() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        playToGameOver(g);
        expectProblem(rename(g.code(), "A", "Los Tlacuaches", captainA(g).token()), 409, "ROOM_NOT_IN_LOBBY");
    }
}
