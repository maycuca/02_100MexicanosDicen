package mx.cienmexicanos.room;

import java.time.Clock;
import java.time.Duration;
import mx.cienmexicanos.game.TurnTimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** AC-28: cierra las salas sin actividad durante {@code app.room-ttl} (2 h por defecto) y cancela sus temporizadores. */
@Component
public class RoomExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(RoomExpiryJob.class);

    private final RoomRegistry registry;
    private final Clock clock;
    private final Duration ttl;
    private final TurnTimerScheduler timers;

    public RoomExpiryJob(RoomRegistry registry, Clock clock, @Value("${app.room-ttl:PT2H}") Duration ttl,
                         TurnTimerScheduler timers) {
        this.registry = registry;
        this.timers = timers;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Scheduled(fixedDelayString = "${app.room-expiry-check:PT1M}")
    public void evictInactiveRooms() {
        var evicted = registry.evictInactiveSince(clock.instant().minus(ttl));
        evicted.forEach(timers::cancel);
        if (!evicted.isEmpty()) {
            log.info("Salas cerradas por inactividad: {}", evicted);
        }
    }
}
