package mx.cienmexicanos.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 002 US-13 — Temporizador de turno: configuración {@code turnSeconds}/{@code autoStrikeOnTimeout} (AC-46),
 * {@code round.turnDeadline} (AC-47), vencimiento con {@code TIMER_EXPIRED} y X automática (AC-48) y sin X dobles
 * (AC-49). T723.
 *
 * <p>Las pruebas de vencimiento usan el mínimo permitido ({@code turnSeconds=10}) y esperan por condición (evento o
 * snapshot) con un margen de 5 s; nunca con {@code sleep}. Cuando hay que actuar "justo antes" del vencimiento se espera
 * a que el reloj llegue a {@code turnDeadline − 750 ms} (mismo reloj que el servidor: localhost).
 */
@Tag("acceptance")
@DisplayName("Aceptación 002 · Temporizador de turno")
class TurnTimerAcceptanceTest extends AcceptanceTestBase {

    private static final int T = 10;
    /** Margen para esperar un vencimiento: turnSeconds + 5 s. */
    private static final Duration EXPIRY_WAIT = Duration.ofSeconds(T + 5);
    /** Tolerancia de reloj/serialización al comparar turnDeadline con el reloj local. */
    private static final Duration SLACK = Duration.ofMillis(250);

    private static void awaitClock(Instant instant) {
        await().atMost(Duration.ofSeconds(T + 5)).pollInterval(Duration.ofMillis(20)).until(() -> !Instant.now().isBefore(instant));
    }

    private Resp awaitPresenterView(Game g, Predicate<Resp> condition) {
        Resp[] last = new Resp[1];
        await().atMost(TIMEOUT).until(() -> condition.test(last[0] = presenterView(g)));
        return last[0];
    }

    /** Ejecuta un comando y verifica AC-47: {@code turnDeadline ≈ ahora + turnSeconds} (UTC) en la respuesta. */
    private Resp expectFreshDeadline(Supplier<Resp> command, int turnSeconds, String when) {
        Instant t0 = Instant.now();
        Resp r = command.get();
        Instant t1 = Instant.now();
        Instant deadline = turnDeadline(r);
        assertThat(deadline).as("AC-47 turnDeadline presente %s: %s", when, r).isNotNull();
        assertThat(deadline).as("AC-47 turnDeadline = ahora + %d s %s", turnSeconds, when)
                .isBetween(t0.plusSeconds(turnSeconds).minus(SLACK), t1.plusSeconds(turnSeconds).plus(SLACK));
        return r;
    }

    // ------------------------------------------------------------------ AC-46

    @Test
    @DisplayName("AC-46 AC-53 por defecto turnSeconds = 20, autoStrikeOnTimeout = true y soundEnabled = true")
    void defaults() {
        Room room = createRoom("{}");
        Resp created = room.created();
        assertThat(created.intAt("$.state.turnSeconds")).isEqualTo(20);
        assertThat((Boolean) created.read("$.state.autoStrikeOnTimeout")).isTrue();
        assertThat((Boolean) created.read("$.state.soundEnabled")).isTrue();

        Resp pub = expectStatus(getRoom(room.code()), 200);
        assertThat(pub.intAt("$.turnSeconds")).isEqualTo(20);
        assertThat((Boolean) pub.read("$.autoStrikeOnTimeout")).isTrue();
        assertThat((Boolean) pub.read("$.soundEnabled")).isTrue();

        Resp noBody = expectStatus(post("/api/rooms", null), 201);
        assertThat(noBody.intAt("$.state.turnSeconds")).isEqualTo(20);
        assertThat((Boolean) noBody.read("$.state.autoStrikeOnTimeout")).isTrue();
    }

    @ParameterizedTest(name = "AC-46 turnSeconds válido: {0}")
    @ValueSource(ints = {0, 10, 35, 60})
    void validTurnSeconds(int seconds) {
        Room room = createRoom("{\"turnSeconds\":" + seconds + "}");
        assertThat(room.created().intAt("$.state.turnSeconds")).isEqualTo(seconds);
        assertThat(expectStatus(getRoom(room.code()), 200).intAt("$.turnSeconds")).isEqualTo(seconds);
    }

    @ParameterizedTest(name = "AC-46 cuerpo inválido → 400: {0}")
    @ValueSource(strings = {
        "{\"turnSeconds\":-1}",
        "{\"turnSeconds\":1}",
        "{\"turnSeconds\":9}",
        "{\"turnSeconds\":61}",
        "{\"turnSeconds\":120}",
        "{\"turnSeconds\":\"veinte\"}",
        "{\"autoStrikeOnTimeout\":\"quizá\"}"
    })
    void invalidTurnSecondsRejected(String body) {
        Resp r = post("/api/rooms", body);
        assertThat(r.status()).as("respuesta: %s", r).isEqualTo(400);
        assertThat(r.contentType()).startsWith("application/problem+json");
    }

    @Test
    @DisplayName("AC-46 autoStrikeOnTimeout = false se guarda en la sala")
    void autoStrikeCanBeDisabled() {
        Room room = createRoom("{\"turnSeconds\":15,\"autoStrikeOnTimeout\":false}");
        assertThat(room.created().intAt("$.state.turnSeconds")).isEqualTo(15);
        assertThat((Boolean) room.created().read("$.state.autoStrikeOnTimeout")).isFalse();
        assertThat((Boolean) expectStatus(getRoom(room.code()), 200).read("$.autoStrikeOnTimeout")).isFalse();
    }

    // ------------------------------------------------------------------ AC-47 + spec 005 AC-79…AC-82

    @Test
    @DisplayName("spec 005 AC-79 AC-82 el temporizador nunca arranca solo: buzz, revelar, control y X lo dejan detenido y completo")
    void deadlineFollowsActiveTeam() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        assertThat(turnDeadline(presenterView(g))).as("LOBBY").isNull();
        Resp started = expectStatus(start(g), 200);
        assertThat(turnDeadline(started)).as("FACE_OFF sin buzz").isNull();
        assertThat((Boolean) started.read("$.round.timerRunning")).isFalse();
        assertThat(started.intAt("$.round.timerRemainingMs")).isEqualTo(T * 1000);
        Slot top = slotAt(g, 1);
        Slot second = slotAt(g, 2);

        buzzBy(g, "A");
        assertThat(turnDeadline(presenterView(g))).as("tras el buzz").isNull();
        assertThat(turnDeadline(expectStatus(reveal(g, top.answerId()), 200))).as("tras revelar la #1 (control, spec 006)").isNull();
        assertThat(turnDeadline(expectStatus(reveal(g, second.answerId()), 200))).isNull();
        Resp x = expectStatus(strike(g), 200);
        assertThat(turnDeadline(x)).as("tras una X").isNull();
        assertThat(x.intAt("$.round.timerRemainingMs")).isEqualTo(T * 1000);
    }

    @Test
    @DisplayName("spec 005 AC-80 AC-81 Iniciar fija turnDeadline = ahora + turnSeconds; Parar congela; Reanudar sigue; Restablecer completa; TIMER_CHANGED")
    void manualTimerControls() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            winFaceOff(g, "A");
            int mark = w.mark();

            Resp started = expectFreshDeadline(() -> expectStatus(timer(g, "START"), 200), T, "al iniciar");
            assertThat((Boolean) started.read("$.round.timerRunning")).isTrue();
            assertThat(turnDeadline(expectStatus(getRoom(g.code()), 200))).as("la TV ve el mismo vencimiento")
                    .isEqualTo(turnDeadline(started));
            w.awaitEvent(mark, "TIMER_CHANGED", TIMEOUT);

            awaitClock(Instant.now().plusMillis(1200));
            Resp paused = expectStatus(timer(g, "PAUSE"), 200);
            assertThat(turnDeadline(paused)).isNull();
            assertThat((Boolean) paused.read("$.round.timerRunning")).isFalse();
            int left = paused.intAt("$.round.timerRemainingMs");
            assertThat(left).isBetween(T * 1000 - 3000, T * 1000 - 1000);

            Instant before = Instant.now();
            Resp resumed = expectStatus(timer(g, "START"), 200);
            assertThat(turnDeadline(resumed)).as("Reanudar corre desde lo que quedaba")
                    .isBetween(before.plusMillis(left).minus(SLACK), Instant.now().plusMillis(left).plus(SLACK));

            Resp reset = expectStatus(timer(g, "RESET"), 200);
            assertThat(turnDeadline(reset)).isNull();
            assertThat(reset.intAt("$.round.timerRemainingMs")).isEqualTo(T * 1000);
            assertThat(w.eventsOf("TIMER_CHANGED", mark)).hasSizeGreaterThanOrEqualTo(4);
        }
    }

    @Test
    @DisplayName("spec 005 AC-81 errores del temporizador: 401, 403, 409 INVALID_STATE, 409 TIMER_DISABLED, 400 VALIDATION_ERROR")
    void timerErrors() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        expectProblem(timer(g, "START"), 409, "INVALID_STATE");
        expectStatus(start(g), 200);
        expectProblem(post("/api/rooms/" + g.code() + "/rounds/current/timer", "{\"action\":\"START\"}"), 401, "UNAUTHORIZED");
        expectProblem(post("/api/rooms/" + g.code() + "/rounds/current/timer", "{\"action\":\"START\"}",
                presenter("otro")), 403, "FORBIDDEN");
        expectProblem(timer(g, "LATER"), 400, "VALIDATION_ERROR");

        Game noTimer = startedGame(3, 1000); // turnSeconds = 0
        expectProblem(timer(noTimer, "START"), 409, "TIMER_DISABLED");
    }

    @Test
    @DisplayName("AC-47 con turnSeconds = 0 turnDeadline siempre es null aunque haya equipo en turno")
    void noTimerMeansNoDeadline() {
        Game g = startedGame(3, 1000); // turnSeconds = 0
        assertThat(presenterView(g).intAt("$.turnSeconds")).isZero();
        buzzBy(g, "A");
        Resp pv = presenterView(g);
        assertThat(activeTeam(pv)).isEqualTo("A");
        assertThat(turnDeadline(pv)).isNull();
        Resp control = expectStatus(assignControl(g, "A"), 200);
        assertThat(turnDeadline(control)).isNull();
        assertThat(turnDeadline(expectStatus(strike(g), 200))).isNull();
    }

    // ------------------------------------------------------------------ AC-48 + spec 005 AC-83

    @Test
    @DisplayName("spec 005 AC-83 AC-85 vence en FACE_OFF: TIMER_EXPIRED + FACE_OFF_MISS, pasa el turno y el temporizador queda en 0")
    void expiryInFaceOffStrikesAndPassesTurn() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            buzzBy(g, "A");
            int mark = w.mark();
            Instant deadline = turnDeadline(expectStatus(timer(g, "START"), 200));
            assertThat(deadline).isNotNull();

            String expired = w.awaitEvent(mark, "TIMER_EXPIRED", EXPIRY_WAIT);
            assertThat(Instant.now()).as("AC-48 no vence antes de turnDeadline").isAfterOrEqualTo(deadline.minus(SLACK));
            assertThat((String) JsonPath.read(expired, "$.team")).isEqualTo("A");
            String miss = w.awaitEvent(mark, "FACE_OFF_MISS", TIMEOUT);
            assertThat((String) JsonPath.read(miss, "$.team")).isEqualTo("A");

            Resp after = awaitPresenterView(g, r -> "B".equals(activeTeam(r)));
            assertThat(after.roomStatus()).isEqualTo("FACE_OFF");
            assertThat(after.intAt("$.round.strikes")).isZero();
            assertThat(strikesOf(after, "A")).isZero();
            assertThat(turnDeadline(after)).as("AC-83 no se reinicia").isNull();
            assertThat(after.intAt("$.round.timerRemainingMs")).isZero();
            assertThat(w.eventsOf("STRIKE", mark)).isEmpty();
            assertThat(w.eventsOf("TIMER_EXPIRED", mark)).as("un vencimiento → un TIMER_EXPIRED").hasSize(1);
        }
    }

    @Test
    @DisplayName("AC-48 vence en STEAL: X automática al que roba → ROUND_END con el bote para el equipo con control")
    void expiryInStealEndsRound() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            winFaceOff(g, "A");
            Resp steal = strikeUntilSteal(g);
            int pot = steal.intAt("$.round.pot");
            int mark = w.mark();
            Instant deadline = turnDeadline(expectStatus(timer(g, "START"), 200));
            assertThat(deadline).isNotNull();

            String expired = w.awaitEvent(mark, "TIMER_EXPIRED", EXPIRY_WAIT);
            assertThat(Instant.now()).isAfterOrEqualTo(deadline.minus(SLACK));
            assertThat((String) JsonPath.read(expired, "$.team")).isEqualTo("B");
            String strike = w.awaitEvent(mark, "STRIKE", TIMEOUT);
            assertThat((String) JsonPath.read(strike, "$.team")).isEqualTo("B");
            assertThat(((Number) JsonPath.read(strike, "$.strikes")).intValue()).isEqualTo(1);
            w.awaitEvent(mark, "ROUND_ENDED", TIMEOUT);

            Resp end = awaitPresenterView(g, r -> "ROUND_END".equals(r.roomStatus()));
            assertThat((String) end.read("$.round.potWinner")).as("AC-19 robo fallido").isEqualTo("A");
            assertThat(scoreOf(end, "A")).isEqualTo(pot * end.intAt("$.round.multiplier"));
            assertThat(strikesOf(end, "B")).isEqualTo(1);
            assertStrikesInvariant(end);
            assertThat(activeTeam(end)).isNull();
            assertThat(turnDeadline(end)).isNull();
            assertThat(end.intAt("$.round.timerRemainingMs")).as("AC-84 completo al terminar la ronda").isEqualTo(T * 1000);
        }
    }

    @Test
    @DisplayName("AC-48 con autoStrikeOnTimeout = false: TIMER_EXPIRED sin X; el temporizador queda en 0 y nada lo reinicia")
    void expiryWithoutAutoStrike() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, false);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            Slot second = slotAt(g, 2);
            winFaceOff(g, "A");
            int mark = w.mark();
            Instant deadline = turnDeadline(expectStatus(timer(g, "START"), 200));

            String expired = w.awaitEvent(mark, "TIMER_EXPIRED", EXPIRY_WAIT);
            assertThat(Instant.now()).isAfterOrEqualTo(deadline.minus(SLACK));
            assertThat((String) JsonPath.read(expired, "$.team")).isEqualTo("A");

            Resp after = awaitPresenterView(g, r -> turnDeadline(r) == null);
            assertThat(after.roomStatus()).isEqualTo("PLAYING");
            assertThat(after.intAt("$.round.strikes")).as("sin X automática").isZero();
            assertThat(activeTeam(after)).isEqualTo("A");
            assertThat(w.eventsOf("STRIKE", mark)).isEmpty();

            assertThat(turnDeadline(expectStatus(reveal(g, second.answerId()), 200)))
                    .as("spec 005 AC-79 revelar no lo reinicia").isNull();
            expectFreshDeadline(() -> expectStatus(timer(g, "START"), 200), T, "Iniciar tras vencer usa la duración completa");
        }
    }

    // ------------------------------------------------------------------ AC-49

    @Test
    @DisplayName("AC-49 Parar justo antes de vencer cancela el vencimiento: sin TIMER_EXPIRED ni X")
    void manualActionBeforeExpiryPreventsDoubleStrike() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            winFaceOff(g, "A");
            int mark = w.mark();
            Instant first = turnDeadline(expectStatus(timer(g, "START"), 200));

            awaitClock(first.minusMillis(750));
            Resp paused = expectStatus(timer(g, "PAUSE"), 200);
            assertThat(paused.intAt("$.round.timerRemainingMs")).isPositive();

            awaitClock(first.plusMillis(1500));
            assertThat(presenterView(g).intAt("$.round.strikes")).as("AC-49 sin X").isZero();
            assertThat(w.eventsOf("TIMER_EXPIRED", mark)).isEmpty();
            assertThat(w.eventsOf("STRIKE", mark)).isEmpty();
        }
    }

    @Test
    @DisplayName("spec 005 C-25 una X manual con el temporizador corriendo no lo reinicia: vence a la hora original una sola vez")
    void revealBeforeExpiryCancelsTimer() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            winFaceOff(g, "B");
            int mark = w.mark();
            Instant first = turnDeadline(expectStatus(timer(g, "START"), 200));
            Resp manual = expectStatus(strike(g), 200);
            assertThat(turnDeadline(manual)).as("C-25 el vencimiento no cambia").isEqualTo(first);

            w.awaitEvent(mark, "TIMER_EXPIRED", EXPIRY_WAIT);
            Resp after = awaitPresenterView(g, r -> r.intAt("$.round.strikes") == 2);
            assertThat(strikesOf(after, "B")).isEqualTo(2);
            awaitClock(Instant.now().plusMillis(1500));
            assertThat(presenterView(g).intAt("$.round.strikes")).as("un vencimiento → una sola X").isEqualTo(2);
            assertThat(w.eventsOf("TIMER_EXPIRED", mark)).hasSize(1);
        }
    }

    // ------------------------------------------------------------------ spec 005 AC-91

    @Test
    @DisplayName("spec 005 AC-91 cerrar la sala: ROOM_CLOSED por STOMP, 204 y después 404; sin token 401, token ajeno 403")
    void closeRoomBroadcasts() {
        Game g = lobbyWithTwoPlayers(3, 1000, T, true);
        Game other = lobbyWithTwoPlayers(3, 1000, T, true);
        try (Watch w = watch(g)) {
            expectStatus(start(g), 200);
            expectStatus(timer(g, "START"), 200);
            int mark = w.mark();
            expectProblem(send("DELETE", "/api/rooms/" + g.code(), null), 401, "UNAUTHORIZED");
            expectProblem(send("DELETE", "/api/rooms/" + g.code(), null, presenter(other.token())), 403, "FORBIDDEN");
            assertThat(closeRoom(g).status()).isEqualTo(204);
            w.awaitEvent(mark, "ROOM_CLOSED", TIMEOUT);
            expectProblem(getRoom(g.code()), 404, "ROOM_NOT_FOUND");
            expectProblem(closeRoom(g), 404, "ROOM_NOT_FOUND");
        }
        expectStatus(getRoom(other.code()), 200);
    }
}
