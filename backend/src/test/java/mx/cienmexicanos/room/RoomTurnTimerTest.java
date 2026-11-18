package mx.cienmexicanos.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import mx.cienmexicanos.common.api.GameEventMessage;
import mx.cienmexicanos.common.api.RoomState;
import mx.cienmexicanos.game.FakeTaskScheduler;
import mx.cienmexicanos.game.GameResultRecorder;
import mx.cienmexicanos.game.TurnTimerScheduler;
import mx.cienmexicanos.game.domain.AnswerSnapshot;
import mx.cienmexicanos.game.domain.GameEventType;
import mx.cienmexicanos.game.domain.QuestionSnapshot;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.RoomStatus;
import mx.cienmexicanos.game.domain.Round;
import mx.cienmexicanos.game.domain.TeamId;
import mx.cienmexicanos.game.domain.TimerAction;
import mx.cienmexicanos.realtime.RoomBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * T704/T706 (plan D13/D14): RoomCommandRunner + TurnTimerScheduler con un reloj controlado y un TaskScheduler
 * simulado; incluye la carrera acción manual vs. vencimiento (AC-48, AC-49). Spec 005 (T1021): el temporizador solo
 * corre cuando el presentador lo inicia y al vencer no se reinicia (AC-79…AC-83).
 */
class RoomTurnTimerTest {

    static final Instant T0 = Instant.parse("2026-11-17T10:00:00Z");

    final RoomExpiryJobTest.MutableClock clock = new RoomExpiryJobTest.MutableClock(T0);
    final FakeTaskScheduler fake = new FakeTaskScheduler();
    final TurnTimerScheduler timers = new TurnTimerScheduler(fake);
    final RoomRegistry registry = new RoomRegistry(RoomRegistryTest.scripted("KDMZ", "ABCD"));
    final List<Message<?>> sent = new CopyOnWriteArrayList<>();
    final RoomCommandRunner runner = new RoomCommandRunner(registry,
            new RoomBroadcaster(new SimpMessagingTemplate((message, timeout) -> sent.add(message))),
            new GameResultRecorder(null, clock), timers, clock);

    static List<QuestionSnapshot> questions() {
        return IntStream.rangeClosed(1, 5).mapToObj(q -> new QuestionSnapshot(q, "Pregunta " + q, List.of(
                new AnswerSnapshot(q * 10L + 1, "Uno", 40, 1),
                new AnswerSnapshot(q * 10L + 2, "Dos", 30, 2),
                new AnswerSnapshot(q * 10L + 3, "Tres", 20, 3)))).toList();
    }

    /** Sala en FACE_OFF con dos jugadores; aún sin buzz. */
    String faceOffRoom(int turnSeconds, boolean autoStrike) {
        Room created = registry.create(c -> new Room(c, "Rojo", "Verde", 5, 300, turnSeconds, autoStrike,
                clock.instant()));
        String code = created.code();
        runner.execute(code, room -> room.addPlayer("Ana", TeamId.A));
        runner.execute(code, room -> room.addPlayer("Beto", TeamId.B));
        runner.execute(code, room -> {
            room.start(questions());
            return null;
        });
        sent.clear();
        return code;
    }

    String playingRoom(TeamId control, int turnSeconds, boolean autoStrike) {
        String code = faceOffRoom(turnSeconds, autoStrike);
        runner.execute(code, room -> {
            room.assignControl(control, clock.instant());
            return null;
        });
        sent.clear();
        return code;
    }

    void strike(String code) {
        runner.execute(code, room -> {
            room.strike(clock.instant());
            return null;
        });
    }

    /** Spec 005, AC-80: acción del presentador sobre el temporizador. */
    void timer(String code, TimerAction action) {
        runner.execute(code, room -> room.controlTimer(action, clock.instant()));
    }

    Round round(String code) {
        return runner.read(code, room -> room.currentRound().orElseThrow());
    }

    List<GameEventMessage> events() {
        return sent.stream().map(Message::getPayload).filter(GameEventMessage.class::isInstance)
                .map(GameEventMessage.class::cast).toList();
    }

    List<GameEventType> eventTypes() {
        return events().stream().map(GameEventMessage::type).toList();
    }

    RoomState lastPublicState() {
        return sent.stream().map(Message::getPayload).filter(RoomState.class::isInstance)
                .map(RoomState.class::cast).reduce((a, b) -> b).orElseThrow();
    }

    @Test
    @DisplayName("spec 005 AC-79 ni el buzz ni el control agendan nada: solo Iniciar agenda el vencimiento")
    void onlyStartSchedules() {
        String code = faceOffRoom(20, true);
        runner.execute(code, room -> room.buzz(room.currentRound().orElseThrow().faceOff().playerA(),
                clock.instant()));
        runner.execute(code, room -> {
            room.assignControl(TeamId.A, clock.instant());
            return null;
        });
        assertThat(fake.pending()).isEmpty();
        assertThat(lastPublicState().round().turnDeadline()).isNull();
        assertThat(lastPublicState().round().timerRemainingMs()).isEqualTo(20_000);

        timer(code, TimerAction.START);
        assertThat(timers.scheduledDeadline(code)).isEqualTo(T0.plusSeconds(20));
        assertThat(lastPublicState().round().turnDeadline()).isEqualTo("2026-11-17T10:00:20Z");
        assertThat(eventTypes()).contains(GameEventType.TIMER_CHANGED);
    }

    @Test
    @DisplayName("spec 005 AC-83 AC-85 al vencer en el cara a cara: TIMER_EXPIRED, FACE_OFF_MISS, turno al rival y nada agendado")
    void expiresAndAutoStrikes() {
        String code = faceOffRoom(20, true);
        runner.execute(code, room -> room.buzz(room.currentRound().orElseThrow().faceOff().playerA(),
                clock.instant()));
        timer(code, TimerAction.START);
        sent.clear();

        clock.advance(Duration.ofSeconds(19));
        assertThat(fake.runDue(clock.instant())).as("aún no vence").isZero();
        clock.advance(Duration.ofSeconds(1));
        assertThat(fake.runDue(clock.instant())).isEqualTo(1);

        assertThat(eventTypes()).containsExactly(GameEventType.TIMER_EXPIRED, GameEventType.FACE_OFF_MISS,
                GameEventType.TURN_CHANGED);
        assertThat(events().getFirst().team()).isEqualTo(TeamId.A);
        RoomState state = lastPublicState();
        assertThat(state.round().activeTeam()).isEqualTo(TeamId.B);
        assertThat(state.round().turnDeadline()).as("AC-83 no se reinicia").isNull();
        assertThat(state.round().timerRemainingMs()).isZero();
        assertThat(timers.scheduledDeadline(code)).isNull();
        assertThat(fake.pending()).isEmpty();
    }

    @Test
    @DisplayName("AC-48 la X automática encadena las reglas si el presentador inicia cada turno: STEAL y luego ROUND_END")
    void autoStrikeChain() {
        String code = playingRoom(TeamId.A, 10, true);
        for (int i = 0; i < 3; i++) {
            timer(code, TimerAction.START);
            clock.advance(Duration.ofSeconds(10));
            fake.runDue(clock.instant());
        }
        assertThat(runner.read(code, Room::status)).isEqualTo(RoomStatus.STEAL);
        assertThat(round(code).activeTeam()).contains(TeamId.B);

        timer(code, TimerAction.START);
        clock.advance(Duration.ofSeconds(10));
        fake.runDue(clock.instant());
        assertThat(runner.read(code, Room::status)).isEqualTo(RoomStatus.ROUND_END);
        assertThat(round(code).strikesOf(TeamId.B)).isEqualTo(1);
        assertThat(timers.scheduledDeadline(code)).isNull();
        assertThat(fake.pending()).isEmpty();
        assertThat(eventTypes()).filteredOn(t -> t == GameEventType.TIMER_EXPIRED).hasSize(4);
    }

    @Test
    @DisplayName("AC-48 sin auto-X: TIMER_EXPIRED, turnDeadline null y no queda nada agendado")
    void withoutAutoStrike() {
        String code = playingRoom(TeamId.B, 15, false);
        timer(code, TimerAction.START);
        sent.clear();
        clock.advance(Duration.ofSeconds(15));
        fake.runDue(clock.instant());

        assertThat(eventTypes()).containsExactly(GameEventType.TIMER_EXPIRED);
        assertThat(round(code).strikes()).isZero();
        assertThat(lastPublicState().round().turnDeadline()).isNull();
        assertThat(lastPublicState().round().activeTeam()).isEqualTo(TeamId.B);
        assertThat(fake.pending()).isEmpty();
    }

    @Test
    @DisplayName("spec 005 AC-80 Parar cancela el vencimiento pendiente; Reanudar agenda lo que quedaba")
    void manualActionCancels() {
        String code = playingRoom(TeamId.A, 20, true);
        timer(code, TimerAction.START); // vence en T0+20
        clock.advance(Duration.ofSeconds(5));
        timer(code, TimerAction.PAUSE);
        assertThat(fake.pending()).isEmpty();

        clock.advance(Duration.ofSeconds(19)); // T0+24: pasó el vencimiento original
        assertThat(fake.runDue(clock.instant())).isZero();
        assertThat(eventTypes()).doesNotContain(GameEventType.TIMER_EXPIRED);

        timer(code, TimerAction.START);
        assertThat(timers.scheduledDeadline(code)).isEqualTo(T0.plusSeconds(24 + 15));
    }

    @Test
    @DisplayName("spec 005 C-25 una X manual no reinicia ni cancela el temporizador que corre")
    void strikeKeepsSchedule() {
        String code = playingRoom(TeamId.A, 20, true);
        timer(code, TimerAction.START);
        clock.advance(Duration.ofSeconds(5));
        strike(code);
        assertThat(fake.pending()).singleElement().satisfies(t -> assertThat(t.at()).isEqualTo(T0.plusSeconds(20)));
    }

    @Test
    @DisplayName("AC-49 carrera: el disparo ya en curso llega después de Parar y no aplica X")
    void staleFireIsIgnored() {
        String code = playingRoom(TeamId.A, 20, true);
        timer(code, TimerAction.START);
        FakeTaskScheduler.Task inFlight = fake.next().orElseThrow();

        clock.advance(Duration.ofSeconds(20));
        timer(code, TimerAction.PAUSE); // el presentador gana el lock justo al vencer
        sent.clear();
        inFlight.runNow(); // el disparo que ya había salido

        assertThat(round(code).strikes()).isZero();
        assertThat(sent).as("ni eventos ni snapshot nuevos").isEmpty();
        assertThat(timers.scheduledDeadline(code)).isNull();
    }

    @Test
    @DisplayName("AC-49 un mismo vencimiento disparado dos veces aplica una sola X")
    void doubleFire() {
        String code = playingRoom(TeamId.A, 20, true);
        timer(code, TimerAction.START);
        FakeTaskScheduler.Task task = fake.next().orElseThrow();
        clock.advance(Duration.ofSeconds(20));
        task.runNow();
        task.runNow();
        assertThat(round(code).strikes()).isEqualTo(1);
        assertThat(eventTypes()).filteredOn(t -> t == GameEventType.TIMER_EXPIRED).hasSize(1);
    }

    @Test
    @DisplayName("AC-49 carrera con hilos reales: Parar y vencimiento simultáneos nunca dan más de una X")
    void concurrentRace() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 50; i++) {
                RoomRegistry reg = new RoomRegistry(RoomRegistryTest.scripted("R" + (char) ('A' + i % 26) + "ZZ"));
                RoomExpiryJobTest.MutableClock clock = new RoomExpiryJobTest.MutableClock(T0);
                FakeTaskScheduler sched = new FakeTaskScheduler();
                List<Message<?>> out = new CopyOnWriteArrayList<>();
                RoomCommandRunner r = new RoomCommandRunner(reg,
                        new RoomBroadcaster(new SimpMessagingTemplate((m, t) -> out.add(m))),
                        new GameResultRecorder(null, clock), new TurnTimerScheduler(sched), clock);
                String code = reg.create(c -> new Room(c, "Rojo", "Verde", 5, 300, 20, true, clock.instant())).code();
                r.execute(code, room -> room.addPlayer("Ana", TeamId.A));
                r.execute(code, room -> room.addPlayer("Beto", TeamId.B));
                r.execute(code, room -> {
                    room.start(questions());
                    room.assignControl(TeamId.A, clock.instant());
                    room.controlTimer(TimerAction.START, clock.instant());
                    return null;
                });
                FakeTaskScheduler.Task task = sched.next().orElseThrow();
                clock.advance(Duration.ofSeconds(20)); // el vencimiento dispara a su hora, igual que Parar
                CountDownLatch go = new CountDownLatch(1);
                Future<?> fire = pool.submit(() -> {
                    go.await();
                    task.runNow();
                    return null;
                });
                Future<?> manual = pool.submit(() -> {
                    go.await();
                    r.execute(code, room -> room.controlTimer(TimerAction.PAUSE, clock.instant()));
                    return null;
                });
                go.countDown();
                fire.get();
                manual.get();
                task.runNow(); // un disparo repetido tampoco suma

                long expired = out.stream().map(Message::getPayload).filter(GameEventMessage.class::isInstance)
                        .map(GameEventMessage.class::cast).filter(e -> e.type() == GameEventType.TIMER_EXPIRED)
                        .count();
                int strikes = r.read(code, room -> room.currentRound().orElseThrow().strikes());
                assertThat(expired).isLessThanOrEqualTo(1);
                assertThat(strikes).as("a lo más 1 automática").isEqualTo((int) expired);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AC-28 el vencimiento no cuenta como actividad; al cerrar la sala se cancela su temporizador")
    void expiryAndEviction() {
        String code = faceOffRoom(20, true);
        runner.execute(code, room -> room.buzz(room.currentRound().orElseThrow().faceOff().playerB(),
                clock.instant()));
        timer(code, TimerAction.START);
        Instant lastActivity = runner.read(code, Room::lastActivity);

        clock.advance(Duration.ofSeconds(20));
        fake.runDue(clock.instant());
        assertThat(runner.read(code, Room::lastActivity)).isEqualTo(lastActivity);

        timer(code, TimerAction.START);
        assertThat(fake.pending()).hasSize(1);
        clock.advance(Duration.ofHours(2).plusSeconds(1));
        new RoomExpiryJob(registry, clock, Duration.ofHours(2), timers).evictInactiveRooms();
        assertThat(registry.exists(code)).isFalse();
        assertThat(fake.pending()).isEmpty();
        assertThat(timers.scheduledDeadline(code)).isNull();
    }

    @Test
    @DisplayName("AC-48 un vencimiento que dispara con la sala ya cerrada no falla y limpia el temporizador")
    void fireAfterRoomGone() {
        String code = playingRoom(TeamId.A, 20, true);
        timer(code, TimerAction.START);
        FakeTaskScheduler.Task task = fake.next().orElseThrow();
        clock.advance(Duration.ofHours(3));
        registry.evictInactiveSince(clock.instant()); // sin pasar por el job: el temporizador sigue agendado
        task.runNow();
        assertThat(timers.scheduledDeadline(code)).isNull();
    }

    @Test
    @DisplayName("spec 005 AC-91 cerrar la sala publica ROOM_CLOSED, la quita del registro y cancela su temporizador")
    void closeRoom() {
        String code = playingRoom(TeamId.A, 20, true);
        timer(code, TimerAction.START);
        sent.clear();
        new RoomService(registry, runner, clock).close(code,
                runner.read(code, room -> room.hostToken().toString()));
        assertThat(eventTypes()).containsExactly(GameEventType.ROOM_CLOSED);
        assertThat(registry.exists(code)).isFalse();
        assertThat(fake.pending()).isEmpty();
        assertThat(timers.scheduledDeadline(code)).isNull();
    }

 
    @Test
    @DisplayName("AC-47 con turnSeconds = 0 nunca se agenda nada")
    void noTimer() {
        String code = faceOffRoom(0, true);
        runner.execute(code, room -> room.buzz(room.currentRound().orElseThrow().faceOff().playerA(),
                clock.instant()));
        strike(code);
        assertThat(fake.pending()).isEmpty();
        assertThat(lastPublicState().round().turnDeadline()).isNull();
        assertThat(lastPublicState().turnSeconds()).isZero();
    }
}
