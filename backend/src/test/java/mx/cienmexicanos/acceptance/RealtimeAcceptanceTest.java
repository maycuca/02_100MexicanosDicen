package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tiempo real por STOMP (asyncapi.yaml): broadcast del lobby (AC-08), presentador conectado (AC-32),
 * primer buzz gana con concurrencia (AC-14) y buzz no permitido → /user/queue/errors (AC-27).
 *
 * <p>El simple broker no confirma suscripciones (sin RECEIPT); por eso cada prueba espera por condición sobre
 * snapshots completos, y cuando mide latencia lo hace sobre un mensaje posterior a uno de "calentamiento".
 */
@Tag("acceptance")
@DisplayName("Aceptación · Tiempo real (STOMP)")
class RealtimeAcceptanceTest extends AcceptanceTestBase {

    private static <T> T jsonOrNull(String json, String path) {
        try {
            return JsonPath.read(json, path);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private static boolean hasPlayerNamed(String json, String name) {
        List<String> names = JsonPath.read(json, "$.teams[*].players[*].name");
        return names.contains(name);
    }

    private static boolean isType(String json, String type) {
        return type.equals(jsonOrNull(json, "$.type"));
    }

    // ------------------------------------------------------------------ AC-08

    @Test
    @DisplayName("AC-08 al unirse un jugador, /topic/rooms/{code}/state lo difunde agrupado por equipo en ≤ 1 s")
    void joinIsBroadcastWithinOneSecond() {
        Room room = createRoom("{}");
        try (StompTestClient screen = connectPresenter(room)) {
            StompTestClient.Inbox state = screen.subscribe(stateTopic(room.code()));

            String warm = uniqueName("Ana");
            join(room.code(), warm, "A");
            state.tryAwait(json -> hasPlayerNamed(json, warm), TIMEOUT); // calentamiento de la suscripción

            String name = uniqueName("Beto");
            long t0 = System.nanoTime();
            join(room.code(), name, "B");
            StompTestClient.Received msg = state.await(json -> hasPlayerNamed(json, name), TIMEOUT);

            assertThat(Duration.ofNanos(msg.atNanos() - t0)).as("latencia del broadcast").isLessThanOrEqualTo(Duration.ofSeconds(1));
            List<String> teamB = JsonPath.read(msg.payload(), "$.teams[?(@.id == 'B')].players[*].name");
            List<String> teamA = JsonPath.read(msg.payload(), "$.teams[?(@.id == 'A')].players[*].name");
            assertThat(teamB).containsExactly(name);
            assertThat(teamA).containsExactly(warm);
            assertThat((String) JsonPath.read(msg.payload(), "$.code")).isEqualTo(room.code());
            assertThat((Object) JsonPath.read(msg.payload(), "$.version")).isInstanceOf(Number.class);
        }
    }

    @Test
    @DisplayName("AC-08 los snapshots difundidos tienen version creciente")
    void broadcastVersionsIncrease() {
        Room room = createRoom("{}");
        try (StompTestClient screen = connectPresenter(room)) {
            StompTestClient.Inbox state = screen.subscribe(stateTopic(room.code()));
            List<String> names = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String n = uniqueName("Jug");
                names.add(n);
                join(room.code(), n, i % 2 == 0 ? "A" : "B");
            }
            state.await(json -> names.stream().allMatch(n -> hasPlayerNamed(json, n)), TIMEOUT);
            List<Integer> versions = state.payloads().stream()
                    .map(json -> ((Number) JsonPath.read(json, "$.version")).intValue())
                    .toList();
            assertThat(versions).doesNotHaveDuplicates();
        }
    }

    // ------------------------------------------------------------------ AC-32

    @Test
    @DisplayName("AC-32 AC-33 la pantalla recibe presenterConnected por STOMP: true al canjear, false al regenerar el QR")
    void presenterConnectedIsBroadcast() {
        Room room = createRoom("{}");
        try (StompTestClient screen = connectPresenter(room)) {
            StompTestClient.Inbox state = screen.subscribe(stateTopic(room.code()));
            StompTestClient.Inbox events = screen.subscribe(eventsTopic(room.code()));
            String warm = uniqueName("Ana");
            join(room.code(), warm, "A");
            state.tryAwait(json -> hasPlayerNamed(json, warm), TIMEOUT);

            expectStatus(post("/api/rooms/" + room.code() + "/presenter/claim",
                    "{\"claimToken\":" + quote(room.claimToken()) + "}"), 200);

            StompTestClient.Received connected =
                    state.await(json -> Boolean.TRUE.equals(jsonOrNull(json, "$.presenterConnected")), TIMEOUT);
            events.await(json -> isType(json, "PRESENTER_CLAIMED"), TIMEOUT);

            // AC-33: regenerar el QR vuelve a poner presenterConnected = false en la pantalla.
            int v = ((Number) JsonPath.read(connected.payload(), "$.version")).intValue();
            expectStatus(post("/api/rooms/" + room.code() + "/presenter/claim-token", null, presenter(room.presenterToken())), 200);
            state.await(json -> Boolean.FALSE.equals(jsonOrNull(json, "$.presenterConnected"))
                    && ((Number) JsonPath.read(json, "$.version")).intValue() > v, TIMEOUT);
        }
    }

    // ------------------------------------------------------------------ AC-14

    @Test
    @DisplayName("AC-14 con 20 buzz simultáneos solo el primero gana y todos lo ven en ≤ 500 ms")
    void onlyFirstBuzzWinsUnderConcurrency() throws Exception {
        int perTeam = 10;
        Room room = createRoom(3, 1000);
        List<Joined> players = new ArrayList<>();
        List<StompTestClient> clients = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2 * perTeam);
        try (StompTestClient screen = connectPresenter(room)) {
            StompTestClient.Inbox screenState = screen.subscribe(stateTopic(room.code()));
            StompTestClient.Inbox screenEvents = screen.subscribe(eventsTopic(room.code()));

            for (int i = 0; i < perTeam; i++) {
                players.add(join(room.code(), uniqueName("A"), "A"));
                players.add(join(room.code(), uniqueName("B"), "B"));
            }
            Game g = new Game(room, players);
            // AC-08 de paso: la pantalla ve a los 20 jugadores.
            screenState.await(json -> players.stream().allMatch(p -> hasPlayerNamed(json, p.name())), TIMEOUT);

            List<StompTestClient.Inbox> playerStates = new ArrayList<>();
            for (Joined p : players) {
                StompTestClient c = connectPlayer(p);
                clients.add(c);
                playerStates.add(c.subscribe(stateTopic(room.code())));
            }

            expectStatus(start(g), 200);
            screenState.await(json -> "FACE_OFF".equals(jsonOrNull(json, "$.status")), TIMEOUT);
            Set<String> designated = Set.of(designated(g, "A"), designated(g, "B"));

            // Todos (designados y no designados) presionan a la vez; los designados, dos veces cada uno.
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> sends = new ArrayList<>();
            for (int i = 0; i < clients.size(); i++) {
                StompTestClient c = clients.get(i);
                int times = designated.contains(players.get(i).id()) ? 2 : 1;
                sends.add(pool.submit(() -> {
                    go.await();
                    for (int k = 0; k < times; k++) {
                        c.send(buzzDestination(room.code()), "{}");
                    }
                    return null;
                }));
            }
            long released = System.nanoTime();
            go.countDown();
            for (Future<?> f : sends) {
                f.get(5, TimeUnit.SECONDS);
            }

            StompTestClient.Received won = screenState.await(json -> jsonOrNull(json, "$.round.faceOff.buzzWinner") != null, TIMEOUT);
            String winner = JsonPath.read(won.payload(), "$.round.faceOff.buzzWinner");
            assertThat(designated).as("solo un designado puede ganar (AC-27)").contains(winner);

            // Todos los clientes ven al ganador en ≤ 500 ms (LAN = loopback aquí).
            for (StompTestClient.Inbox inbox : playerStates) {
                StompTestClient.Received seen = inbox.await(json -> winner.equals(jsonOrNull(json, "$.round.faceOff.buzzWinner")), TIMEOUT);
                assertThat(Duration.ofNanos(seen.atNanos() - released)).isLessThanOrEqualTo(Duration.ofMillis(500));
            }

            // Barrera: una acción posterior del presentador; cuando su snapshot llega, ya se procesaron los buzz.
            expectStatus(reveal(g, slotAt(g, 1).answerId()), 200);
            screenState.await(json -> {
                List<Boolean> revealed = jsonOrNull(json, "$.round.board[*].revealed");
                return revealed != null && revealed.contains(true);
            }, TIMEOUT);

            Set<String> winnersSeen = new HashSet<>();
            for (String json : screenState.payloads()) {
                Optional.ofNullable(RealtimeAcceptanceTest.<String>jsonOrNull(json, "$.round.faceOff.buzzWinner")).ifPresent(winnersSeen::add);
            }
            assertThat(winnersSeen).as("el ganador nunca cambia").containsExactly(winner);
            assertThat((String) presenterView(g).read("$.round.faceOff.buzzWinner")).isEqualTo(winner);
            long buzzWonEvents = screenEvents.payloads().stream().filter(json -> isType(json, "BUZZ_WON")).count();
            assertThat(buzzWonEvents).as("exactamente un evento BUZZ_WON").isEqualTo(1);
            screenEvents.payloads().stream().filter(json -> isType(json, "BUZZ_WON")).findFirst()
                    .ifPresent(json -> assertThat((String) JsonPath.read(json, "$.playerId")).isEqualTo(winner));
        } finally {
            pool.shutdownNow();
            clients.forEach(StompTestClient::close);
        }
    }

    // ------------------------------------------------------------------ AC-27

    /** Envía buzz cada 200 ms hasta que llegue un error (tolera que la suscripción aún no esté registrada). */
    private String buzzUntilError(StompTestClient c, StompTestClient.Inbox errors, String code) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            c.send(buzzDestination(code), "{}");
            Optional<StompTestClient.Received> e = errors.tryAwait(json -> true, Duration.ofMillis(200));
            if (e.isPresent()) {
                return e.get().payload();
            }
        }
        throw new AssertionError("no llegó ningún mensaje a /user/queue/errors en " + TIMEOUT);
    }

    @Test
    @DisplayName("AC-27 buzz de un jugador no designado en FACE_OFF → error en /user/queue/errors y no gana")
    void nonDesignatedBuzzIsRejected() {
        Room room = createRoom(3, 1000);
        Joined a1 = join(room.code(), uniqueName("Ana"), "A");
        Joined b1 = join(room.code(), uniqueName("Beto"), "B");
        Joined a2 = join(room.code(), uniqueName("Alma"), "A"); // no designado en la ronda 1 (AC-12)
        Game g = new Game(room, List.of(a1, b1, a2));
        expectStatus(start(g), 200);
        assertThat(designated(g, "A")).isEqualTo(a1.id());

        try (StompTestClient c = connectPlayer(a2)) {
            StompTestClient.Inbox errors = c.subscribe("/user/queue/errors");
            String error = buzzUntilError(c, errors, room.code());

            assertThat((String) JsonPath.read(error, "$.code")).isEqualTo("NOT_YOUR_TURN");
            assertThat((String) JsonPath.read(error, "$.message")).isNotBlank();
            assertThat((Object) presenterView(g).readOrNull("$.round.faceOff.buzzWinner")).isNull();
        }
        // El designado sí puede ganar después.
        buzzBy(g, "A");
    }

    @Test
    @DisplayName("AC-27 buzz del designado fuera de FACE_OFF → error INVALID_STATE en /user/queue/errors")
    void designatedBuzzOutsideFaceOffIsRejected() {
        Game g = startedGame(3, 1000);
        winFaceOff(g, "A"); // PLAYING
        Joined designatedB = g.byId(designated(g, "B"));
        try (StompTestClient c = connectPlayer(designatedB)) {
            // Se lee la versión con la sesión ya abierta: abrir/cerrar la sesión cambia `connected`
            // (y por lo tanto `version`); lo que se verifica es que el buzz rechazado no la cambie.
            int version = presenterView(g).intAt("$.version");
            StompTestClient.Inbox errors = c.subscribe("/user/queue/errors");
            String error = buzzUntilError(c, errors, g.code());
            assertThat((String) JsonPath.read(error, "$.code")).isEqualTo("INVALID_STATE");

            Resp after = presenterView(g);
            assertThat(after.roomStatus()).isEqualTo("PLAYING");
            assertThat(after.intAt("$.version")).isEqualTo(version);
        }
    }

    @Test
    @DisplayName("AC-27 buzz en LOBBY → error INVALID_STATE en /user/queue/errors")
    void buzzInLobbyIsRejected() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        try (StompTestClient c = connectPlayer(g.players().getFirst())) {
            StompTestClient.Inbox errors = c.subscribe("/user/queue/errors");
            String error = buzzUntilError(c, errors, g.code());
            assertThat((String) JsonPath.read(error, "$.code")).isEqualTo("INVALID_STATE");
        }
        assertThat((String) getRoom(g.code()).read("$.status")).isEqualTo("LOBBY");
    }

    @Test
    @DisplayName("AC-27 los errores de buzz llegan solo al emisor, no a otros jugadores")
    void errorsArePrivate() {
        Room room = createRoom(3, 1000);
        Joined a1 = join(room.code(), uniqueName("Ana"), "A");
        Joined b1 = join(room.code(), uniqueName("Beto"), "B");
        Joined a2 = join(room.code(), uniqueName("Alma"), "A");
        Game g = new Game(room, List.of(a1, b1, a2));
        expectStatus(start(g), 200);

        try (StompTestClient offender = connectPlayer(a2); StompTestClient bystander = connectPlayer(b1)) {
            StompTestClient.Inbox bystanderErrors = bystander.subscribe("/user/queue/errors");
            StompTestClient.Inbox errors = offender.subscribe("/user/queue/errors");
            buzzUntilError(offender, errors, room.code());
            // El designado B hace buzz válido como barrera: su snapshot llega después de los errores del infractor.
            bystander.send(buzzDestination(room.code()), "{}");
            await().atMost(TIMEOUT).until(() -> b1.id().equals(presenterView(g).readOrNull("$.round.faceOff.buzzWinner")));
            assertThat(bystanderErrors.payloads()).isEmpty();
        }
    }

    @Test
    @DisplayName("AC-26 CONNECT por STOMP con token inválido no permite recibir el estado del presentador")
    void stompPresenterTopicRequiresPresenterToken() {
        Game g = lobbyWithTwoPlayers(3, 1000);
        StompTestClient bogus;
        try {
            bogus = StompTestClient.connect(wsUrl(), Map.of(PRESENTER_HEADER, "00000000-0000-0000-0000-000000000000"));
        } catch (AssertionError rejectedAtConnect) {
            return; // rechazo en CONNECT: cumple
        }
        try (StompTestClient c = bogus; StompTestClient screen = connectPresenter(g)) {
            StompTestClient.Inbox spy = c.subscribe(presenterTopic(g.code()));
            StompTestClient.Inbox legit = screen.subscribe(presenterTopic(g.code()));
            expectStatus(start(g), 200);
            // Barrera: cuando el presentador legítimo recibe el snapshot de FACE_OFF, el espía ya lo habría recibido.
            legit.await(json -> "FACE_OFF".equals(jsonOrNull(json, "$.status"))
                    && jsonOrNull(json, "$.presenterBoard") != null, TIMEOUT);
            assertThat(spy.payloads()).as("sin token válido no llega el tablero del presentador").isEmpty();
        }
    }
}
