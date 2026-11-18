package mx.cienmexicanos.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Plan D13/D14: un único vencimiento agendado por sala. */
class TurnTimerSchedulerTest {

    static final Instant T0 = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    @DisplayName("AC-49 reagendar reemplaza el vencimiento anterior: solo queda uno por sala")
    void rescheduleReplaces() {
        FakeTaskScheduler fake = new FakeTaskScheduler();
        TurnTimerScheduler timers = new TurnTimerScheduler(fake);
        List<String> fired = new ArrayList<>();

        timers.reschedule("KDMZ", T0.plusSeconds(20), () -> fired.add("first"));
        timers.reschedule("KDMZ", T0.plusSeconds(25), () -> fired.add("second"));
        timers.reschedule("ABCD", T0.plusSeconds(20), () -> fired.add("other room"));

        assertThat(fake.pending()).hasSize(2);
        assertThat(timers.scheduledDeadline("KDMZ")).isEqualTo(T0.plusSeconds(25));
        fake.runDue(T0.plusSeconds(60));
        assertThat(fired).containsExactly("other room", "second");
    }

    @Test
    @DisplayName("AC-49 reagendar el mismo vencimiento no duplica la tarea")
    void sameDeadlineKeepsTask() {
        FakeTaskScheduler fake = new FakeTaskScheduler();
        TurnTimerScheduler timers = new TurnTimerScheduler(fake);
        List<String> fired = new ArrayList<>();

        timers.reschedule("KDMZ", T0.plusSeconds(20), () -> fired.add("first"));
        timers.reschedule("KDMZ", T0.plusSeconds(20), () -> fired.add("duplicate"));
        assertThat(fake.pending()).hasSize(1);
        fake.runDue(T0.plusSeconds(20));
        assertThat(fired).containsExactly("first");
    }

    @Test
    @DisplayName("AC-49 un vencimiento null o cancel() cancelan la tarea pendiente")
    void cancel() {
        FakeTaskScheduler fake = new FakeTaskScheduler();
        TurnTimerScheduler timers = new TurnTimerScheduler(fake);
        List<String> fired = new ArrayList<>();

        timers.reschedule("KDMZ", T0.plusSeconds(20), () -> fired.add("a"));
        timers.reschedule("KDMZ", null, () -> fired.add("never"));
        assertThat(timers.scheduledDeadline("KDMZ")).isNull();

        timers.reschedule("ABCD", T0.plusSeconds(20), () -> fired.add("b"));
        timers.cancel("ABCD");
        timers.cancel("NONE");

        assertThat(fake.pending()).isEmpty();
        assertThat(fake.runDue(T0.plusSeconds(60))).isZero();
        assertThat(fired).isEmpty();
    }

    @Test
    @DisplayName("plan D13 con el pool real: la tarea se ejecuta al vencer y destroy() cancela lo pendiente")
    void realPool() throws Exception {
        TurnTimerScheduler timers = new TurnTimerScheduler();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            timers.reschedule("KDMZ", Instant.now().plusMillis(50), latch::countDown);
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            CountDownLatch never = new CountDownLatch(1);
            timers.reschedule("ABCD", Instant.now().plusSeconds(30), never::countDown);
            timers.destroy();
            assertThat(timers.scheduledDeadline("ABCD")).isNull();
            assertThat(never.getCount()).isEqualTo(1);
        } finally {
            timers.destroy();
        }
    }
}
