package mx.cienmexicanos.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import mx.cienmexicanos.game.FakeTaskScheduler;
import mx.cienmexicanos.game.TurnTimerScheduler;
import mx.cienmexicanos.game.domain.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** C-7: AC-28 verificado con un Clock inyectable. */
class RoomExpiryJobTest {

    /** Reloj que se puede adelantar a mano. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) { this.now = start; }

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    @DisplayName("AC-28 una sala sin actividad durante 2 horas se cierra; con actividad reciente sigue abierta")
    void closesInactiveRooms() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));
        RoomRegistry registry = new RoomRegistry(RoomRegistryTest.scripted("IDLE", "BUSY"));
        RoomExpiryJob job = new RoomExpiryJob(registry, clock, Duration.ofHours(2), new TurnTimerScheduler(new FakeTaskScheduler()));
        registry.create(c -> new Room(c, "Rojo", "Verde", 3, 300, clock.instant()));
        Room busy = registry.create(c -> new Room(c, "Rojo", "Verde", 3, 300, clock.instant()));

        clock.advance(Duration.ofMinutes(119));
        job.evictInactiveRooms();
        assertThat(registry.exists("IDLE")).as("aún no cumple 2 h").isTrue();
        busy.touch(clock.instant());

        clock.advance(Duration.ofMinutes(2));
        job.evictInactiveRooms();
        assertThat(registry.exists("IDLE")).isFalse();
        assertThat(registry.exists("BUSY")).isTrue();

        clock.advance(Duration.ofHours(2));
        job.evictInactiveRooms();
        assertThat(registry.exists("BUSY")).isFalse();
    }
}
