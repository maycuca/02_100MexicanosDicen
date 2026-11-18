package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import mx.cienmexicanos.CienMexicanosApplication;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base de las pruebas de aceptación de caja negra (qa-tester, T206/T303).
 *
 * <p>Solo habla HTTP y STOMP contra el servidor real levantado en un puerto aleatorio, según
 * {@code specs/001-juego-base/contracts/openapi.yaml} y {@code asyncapi.yaml}. No usa clases internas
 * del backend: si una prueba falla, el defecto está en la implementación o en el contrato.
 *
 * <p>Se excluyen del build normal; se corren con {@code ./mvnw -B verify -Pacceptance}.
 */
@SpringBootTest(classes = CienMexicanosApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("acceptance")
abstract class AcceptanceTestBase {

    /** Tiempo máximo para cualquier espera por condición. */
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    static final String ROOM_CODE_PATTERN = "^[A-HJ-NP-Z]{4}$";
    static final String PRESENTER_HEADER = "X-Presenter-Token";
    static final String PLAYER_HEADER = "X-Player-Token";

    private static final AtomicInteger NAME_SEQ = new AtomicInteger();

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    // ------------------------------------------------------------------ HTTP

    /** Respuesta HTTP cruda; el cuerpo se consulta con JsonPath. */
    record Resp(int status, String body, String contentType) {

        <T> T read(String path) {
            return JsonPath.read(body, path);
        }

        /** Lee una lista (resultado de un path con comodines o filtros). */
        <T> List<T> list(String path) {
            return JsonPath.read(body, path);
        }

        /** Lee un path que puede no existir; devuelve null si falta. */
        <T> T readOrNull(String path) {
            try {
                return JsonPath.read(body, path);
            } catch (PathNotFoundException e) {
                return null;
            }
        }

        /** Campo {@code code} del Problem (RFC 9457). */
        String code() {
            return body == null || body.isBlank() ? null : readOrNull("$.code");
        }

        String roomStatus() {
            return readOrNull("$.status");
        }

        int intAt(String path) {
            return ((Number) read(path)).intValue();
        }

        @Override
        public String toString() {
            return "HTTP " + status + " " + body;
        }
    }

    String baseUrl() {
        return "http://localhost:" + port;
    }

    String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    Resp get(String path, String... headers) {
        return send("GET", path, null, headers);
    }

    Resp post(String path, String jsonBody, String... headers) {
        return send("POST", path, jsonBody, headers);
    }

    Resp send(String method, String path, String jsonBody, String... headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl() + path)).timeout(Duration.ofSeconds(10));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            if (headers[i + 1] != null) {
                b.header(headers[i], headers[i + 1]);
            }
        }
        if (jsonBody != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resp(r.statusCode(), r.body(), r.headers().firstValue("Content-Type").orElse(""));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static String[] presenter(String token) {
        return new String[] {PRESENTER_HEADER, token};
    }

    static String[] player(String token) {
        return new String[] {PLAYER_HEADER, token};
    }

    static Resp expectStatus(Resp r, int expected) {
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(expected);
        return r;
    }

    static void expectProblem(Resp r, int status, String code) {
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(status);
        assertThat(r.code()).as("Problem.code en %s", r).isEqualTo(code);
    }

    /** Nombre único y válido (2–20 caracteres) para evitar choques entre pruebas. */
    static String uniqueName(String prefix) {
        String n = prefix + NAME_SEQ.incrementAndGet();
        return n.length() > 20 ? n.substring(0, 20) : n;
    }

    // ------------------------------------------------------------------ Fixtures de juego

    record Room(String code, String presenterToken, String claimToken, Resp created) {}

    record Joined(String id, String token, String name, String team) {}

    /** Partida con sus jugadores; permite encontrar el token de un jugador por su id. */
    record Game(Room room, List<Joined> players) {
        Joined byId(String id) {
            return players.stream()
                    .filter(p -> p.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("jugador " + id + " no está en la partida"));
        }

        String code() {
            return room.code();
        }

        String token() {
            return room.presenterToken();
        }
    }

    record Slot(long answerId, int position, String text, int points, boolean revealed) {}

    Room createRoom(String jsonBody) {
        Resp r = expectStatus(post("/api/rooms", jsonBody), 201);
        return new Room(r.read("$.code"), r.read("$.presenterToken"), r.read("$.presenterClaimToken"), r);
    }

    /**
     * Sala para partidas de prueba <b>sin temporizador</b> ({@code turnSeconds=0}, spec 002 AC-46). T727: desde la spec
     * 002 el valor por defecto es 20 s con X automática; las pruebas que no son del temporizador no deben depender de
     * que una X automática no llegue a dispararse.
     */
    Room createRoom(int rounds, int targetScore) {
        return createRoom(rounds, targetScore, 0, true);
    }

    /** Sala con temporizador de turno configurado (spec 002, AC-46). */
    Room createRoom(int rounds, int targetScore, int turnSeconds, boolean autoStrikeOnTimeout) {
        return createRoom("{\"rounds\":" + rounds + ",\"targetScore\":" + targetScore + ",\"turnSeconds\":" + turnSeconds
                + ",\"autoStrikeOnTimeout\":" + autoStrikeOnTimeout + "}");
    }

    Resp joinRaw(String code, String name, String team) {
        String body = "{\"name\":" + (name == null ? "null" : quote(name)) + ",\"team\":"
                + (team == null ? "null" : quote(team)) + "}";
        return post("/api/rooms/" + code + "/players", body);
    }

    Joined join(String code, String name, String team) {
        Resp r = expectStatus(joinRaw(code, name, team), 201);
        return new Joined(r.read("$.player.id"), r.read("$.playerToken"), r.read("$.player.name"), r.read("$.player.team"));
    }

    /** Sala con un jugador por equipo, aún en LOBBY. */
    Game lobbyWithTwoPlayers(int rounds, int targetScore) {
        return lobbyWithTwoPlayers(createRoom(rounds, targetScore));
    }

    /** Sala con temporizador (spec 002) y un jugador por equipo, aún en LOBBY. */
    Game lobbyWithTwoPlayers(int rounds, int targetScore, int turnSeconds, boolean autoStrikeOnTimeout) {
        return lobbyWithTwoPlayers(createRoom(rounds, targetScore, turnSeconds, autoStrikeOnTimeout));
    }

    Game lobbyWithTwoPlayers(Room room) {
        List<Joined> players = new ArrayList<>();
        players.add(join(room.code(), uniqueName("Ana"), "A"));
        players.add(join(room.code(), uniqueName("Beto"), "B"));
        return new Game(room, players);
    }

    /** Sala con un jugador por equipo y la partida iniciada (FACE_OFF de la ronda 1). */
    Game startedGame(int rounds, int targetScore) {
        Game g = lobbyWithTwoPlayers(rounds, targetScore);
        Resp started = expectStatus(start(g), 200);
        assertThat(started.roomStatus()).isEqualTo("FACE_OFF");
        return g;
    }

    Resp getRoom(String code) {
        return get("/api/rooms/" + code);
    }

    Resp presenterView(Game g) {
        return expectStatus(get("/api/rooms/" + g.code() + "/presenter-view", presenter(g.token())), 200);
    }

    Resp start(Game g) {
        return post("/api/rooms/" + g.code() + "/start", null, presenter(g.token()));
    }

    Resp reveal(Game g, long answerId) {
        return post("/api/rooms/" + g.code() + "/rounds/current/answers/" + answerId + "/reveal", null, presenter(g.token()));
    }

    Resp strike(Game g) {
        return post("/api/rooms/" + g.code() + "/rounds/current/strike", null, presenter(g.token()));
    }

    Resp assignControl(Game g, String team) {
        return post("/api/rooms/" + g.code() + "/rounds/current/control", "{\"team\":\"" + team + "\"}", presenter(g.token()));
    }

    /** Spec 005, AC-81: START, PAUSE o RESET del temporizador. */
    Resp timer(Game g, String action) {
        return post("/api/rooms/" + g.code() + "/rounds/current/timer", "{\"action\":\"" + action + "\"}",
                presenter(g.token()));
    }

    /** Spec 008, AC-122: cambiar la pregunta de la ronda. */
    Resp changeQuestion(Game g) {
        return post("/api/rooms/" + g.code() + "/rounds/current/question", null, presenter(g.token()));
    }

    /** Spec 005, AC-91: cerrar la sala. */
    Resp closeRoom(Game g) {
        return send("DELETE", "/api/rooms/" + g.code(), null, presenter(g.token()));
    }

    Resp nextRound(Game g) {
        return post("/api/rooms/" + g.code() + "/next-round", null, presenter(g.token()));
    }

    Resp rematch(Game g) {
        return post("/api/rooms/" + g.code() + "/rematch", null, presenter(g.token()));
    }

    /** Tablero completo (con respuestas ocultas) ordenado por posición. */
    static List<Slot> presenterBoard(Resp presenterState) {
        List<Map<String, Object>> raw = presenterState.read("$.presenterBoard");
        return raw.stream()
                .map(m -> new Slot(
                        ((Number) m.get("answerId")).longValue(),
                        ((Number) m.get("position")).intValue(),
                        (String) m.get("text"),
                        ((Number) m.get("points")).intValue(),
                        Boolean.TRUE.equals(m.get("revealed"))))
                .sorted(Comparator.comparingInt(Slot::position))
                .toList();
    }

    List<Slot> board(Game g) {
        return presenterBoard(presenterView(g));
    }

    Slot slotAt(Game g, int position) {
        return board(g).stream()
                .filter(s -> s.position() == position)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hay casilla en la posición " + position));
    }

    int scoreOf(Resp state, String team) {
        List<Number> scores = state.read("$.teams[?(@.id == '" + team + "')].score");
        assertThat(scores).as("marcador del equipo %s", team).hasSize(1);
        return scores.getFirst().intValue();
    }

    String designated(Game g, String team) {
        return presenterView(g).read("$.round.faceOff.player" + team);
    }

    // ------------------------------------------------------------------ STOMP

    StompTestClient connectPlayer(Joined p) {
        return StompTestClient.connect(wsUrl(), Map.of(PLAYER_HEADER, p.token()));
    }

    StompTestClient connectPresenter(Game g) {
        return StompTestClient.connect(wsUrl(), Map.of(PRESENTER_HEADER, g.token()));
    }

    StompTestClient connectPresenter(Room r) {
        return StompTestClient.connect(wsUrl(), Map.of(PRESENTER_HEADER, r.presenterToken()));
    }

    static String buzzDestination(String code) {
        return "/app/rooms/" + code + "/buzz";
    }

    static String stateTopic(String code) {
        return "/topic/rooms/" + code + "/state";
    }

    static String eventsTopic(String code) {
        return "/topic/rooms/" + code + "/events";
    }

    static String presenterTopic(String code) {
        return "/topic/rooms/" + code + "/presenter";
    }

    /** El jugador designado del equipo {@code team} presiona el buzzer (STOMP) y espera a que el servidor lo registre. */
    void buzzBy(Game g, String team) {
        String playerId = designated(g, team);
        Joined p = g.byId(playerId);
        try (StompTestClient c = connectPlayer(p)) {
            c.send(buzzDestination(g.code()), "{}");
            await().atMost(TIMEOUT).until(() -> playerId.equals(presenterView(g).readOrNull("$.round.faceOff.buzzWinner")));
        }
    }

    // ------------------------------------------------------------------ Rondas completas

    /**
     * Camino canónico del cara a cara (spec 006, AC-92): buzz del designado de {@code team} y el presentador revela la
     * respuesta #1, que le da el control automáticamente. Devuelve el estado tras revelar.
     */
    Resp winFaceOff(Game g, String team) {
        buzzBy(g, team);
        Resp r = expectStatus(reveal(g, slotAt(g, 1).answerId()), 200);
        assertThat(r.roomStatus()).as("AC-92 la #1 termina el cara a cara").isEqualTo("PLAYING");
        assertThat((String) r.read("$.round.controllingTeam")).isEqualTo(team);
        return r;
    }

    /** Marca strikes hasta llegar a STEAL (máximo 3, AC-18). */
    Resp strikeUntilSteal(Game g) {
        Resp r = null;
        for (int i = 0; i < 3; i++) {
            r = expectStatus(strike(g), 200);
            if ("STEAL".equals(r.roomStatus())) {
                return r;
            }
        }
        throw new AssertionError("3 strikes no llevaron a STEAL: " + r);
    }

    /**
     * Juega la ronda actual de forma que {@code team} gana el cara a cara y se queda con el bote:
     * control → 3 strikes → robo fallido. Devuelve el bote de la ronda antes del cierre.
     */
    int playRoundKeepingPot(Game g, String team) {
        winFaceOff(g, team);
        Resp steal = strikeUntilSteal(g);
        int pot = steal.intAt("$.round.pot");
        Resp end = expectStatus(strike(g), 200);
        assertThat(end.roomStatus()).isEqualTo("ROUND_END");
        assertThat((String) end.read("$.round.potWinner")).as("AC-19 robo fallido: bote al equipo con control").isEqualTo(team);
        return pot;
    }

    /** Ronda con bote 0: X en el cara a cara, control a {@code team}, 3 strikes y robo fallido. */
    void playRoundWithEmptyPot(Game g, String team) {
        buzzBy(g, team);
        Resp faceOffStrike = expectStatus(strike(g), 200);
        assertThat(faceOffStrike.roomStatus()).isEqualTo("FACE_OFF");
        expectStatus(assignControl(g, team), 200);
        Resp steal = strikeUntilSteal(g);
        assertThat(steal.intAt("$.round.pot")).isZero();
        Resp end = expectStatus(strike(g), 200);
        assertThat(end.roomStatus()).isEqualTo("ROUND_END");
    }

    // ------------------------------------------------------------------ Spec 002 (contrato v0.4.0)

    Resp setSound(Game g, boolean enabled) {
        return post("/api/rooms/" + g.code() + "/sound", "{\"enabled\":" + enabled + "}", presenter(g.token()));
    }

    /** {@code round.activeTeam} (AC-35); null si no hay ronda, si el campo falta o si es null. */
    static String activeTeam(Resp state) {
        return state.readOrNull("$.round.activeTeam");
    }

    static String activeTeam(String json) {
        return jsonAt(json, "$.round.activeTeam");
    }

    /**
     * Contrato v0.4.0: en {@code RoundState} las claves {@code activeTeam}, {@code strikesByTeam} y {@code turnDeadline}
     * son requeridas (presentes siempre; null cuando no aplican).
     */
    static void assertRoundKeysPresent(Resp state) {
        Map<String, Object> round = state.readOrNull("$.round");
        if (round == null) {
            return;
        }
        assertThat(round).as("RoundState v0.4.0 en %s", state).containsKeys("activeTeam", "strikesByTeam", "turnDeadline");
    }

    /** {@code round.strikesByTeam[team]} (AC-41); falla si el campo no viene. */
    static int strikesOf(Resp state, String team) {
        Number n = state.readOrNull("$.round.strikesByTeam." + team);
        assertThat(n).as("round.strikesByTeam.%s en %s", team, state).isNotNull();
        return n.intValue();
    }

    /** AC-41: siempre {@code strikesByTeam[controllingTeam] == round.strikes} (cuando hay equipo con control). */
    static void assertStrikesInvariant(Resp state) {
        String controlling = state.readOrNull("$.round.controllingTeam");
        if (controlling != null) {
            assertThat(strikesOf(state, controlling))
                    .as("AC-41 strikesByTeam[%s] == round.strikes en %s", controlling, state)
                    .isEqualTo(state.intAt("$.round.strikes"));
        }
    }

    /** {@code round.turnDeadline} como instante (AC-47); null si no hay temporizador activo. Exige ISO-8601 UTC. */
    static java.time.Instant turnDeadline(Resp state) {
        String raw = state.readOrNull("$.round.turnDeadline");
        return raw == null ? null : parseUtc(raw);
    }

    static java.time.Instant parseUtc(String raw) {
        java.time.OffsetDateTime odt;
        try {
            odt = java.time.OffsetDateTime.parse(raw);
        } catch (java.time.format.DateTimeParseException e) {
            throw new AssertionError("turnDeadline no es ISO-8601 con zona: " + raw, e);
        }
        assertThat(odt.getOffset()).as("AC-47 turnDeadline en UTC: %s", raw).isEqualTo(java.time.ZoneOffset.UTC);
        return odt.toInstant();
    }

    static <T> T jsonAt(String json, String path) {
        try {
            return JsonPath.read(json, path);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    static boolean isEvent(String json, String type) {
        return type.equals(jsonAt(json, "$.type"));
    }

    /** Cliente STOMP de la pantalla suscrito a eventos y estado público de una sala (spec 002: TURN_CHANGED, STRIKE…). */
    record Watch(StompTestClient client, StompTestClient.Inbox events, StompTestClient.Inbox state) implements AutoCloseable {

        List<String> eventsOf(String type) {
            return events.payloads().stream().filter(json -> isEvent(json, type)).toList();
        }

        /** Eventos de {@code type} llegados a partir de la marca {@code from} (ver {@link #mark()}). */
        List<String> eventsOf(String type, int from) {
            return events.payloadsFrom(from).stream().filter(json -> isEvent(json, type)).toList();
        }

        /** Marca de los eventos recibidos hasta ahora. */
        int mark() {
            return events.size();
        }

        /** Espera un evento de {@code type} llegado después de la marca {@code from}. */
        String awaitEvent(int from, String type, Duration timeout) {
            return events.awaitFrom(from, json -> isEvent(json, type), timeout).payload();
        }

        /** Espera el snapshot de {@code /state} con {@code version >= minVersion}. */
        String awaitStateVersion(int minVersion, Duration timeout) {
            return state.await(json -> {
                Number v = jsonAt(json, "$.version");
                return v != null && v.intValue() >= minVersion;
            }, timeout).payload();
        }

        @Override
        public void close() {
            client.close();
        }
    }

    /**
     * Conecta la pantalla y se suscribe a {@code /events} y {@code /state}. La sala debe estar en LOBBY: como el simple
     * broker no confirma la suscripción, se une a un espectador (equipo A, después de los designados) hasta recibir su
     * {@code PLAYER_JOINED}; a partir de ahí no se pierde ningún evento.
     */
    Watch watch(Game g) {
        StompTestClient c = connectPresenter(g);
        StompTestClient.Inbox events = c.subscribe(eventsTopic(g.code()));
        StompTestClient.Inbox state = c.subscribe(stateTopic(g.code()));
        for (int i = 0; i < 5; i++) {
            Joined spectator = join(g.code(), uniqueName("Mira"), "A");
            if (events.tryAwait(json -> isEvent(json, "PLAYER_JOINED")
                    && spectator.id().equals(jsonAt(json, "$.playerId")), Duration.ofSeconds(1)).isPresent()) {
                return new Watch(c, events, state);
            }
        }
        c.close();
        throw new AssertionError("la suscripción a " + eventsTopic(g.code()) + " no recibió PLAYER_JOINED");
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
