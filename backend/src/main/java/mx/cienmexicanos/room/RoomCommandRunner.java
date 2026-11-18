package mx.cienmexicanos.room;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import mx.cienmexicanos.game.GameResultRecorder;
import mx.cienmexicanos.game.TurnTimerScheduler;
import mx.cienmexicanos.game.domain.DomainEvent;
import mx.cienmexicanos.game.domain.ErrorCode;
import mx.cienmexicanos.game.domain.GameException;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.RoomStatus;
import mx.cienmexicanos.realtime.RoomBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ejecuta un comando sobre una sala bajo su lock (plan D2) y, si cambió el estado, registra la actividad
 * (AC-28), persiste el resultado al llegar a GAME_OVER, sincroniza el temporizador de turno (spec 002, plan D14) y
 * difunde los snapshots y eventos (plan D3).
 */
@Component
public class RoomCommandRunner {

    private static final Logger log = LoggerFactory.getLogger(RoomCommandRunner.class);

    private final RoomRegistry registry;
    private final RoomBroadcaster broadcaster;
    private final GameResultRecorder recorder;
    private final TurnTimerScheduler timers;
    private final Clock clock;

    public RoomCommandRunner(RoomRegistry registry, RoomBroadcaster broadcaster, GameResultRecorder recorder,
                             TurnTimerScheduler timers, Clock clock) {
        this.registry = registry;
        this.broadcaster = broadcaster;
        this.recorder = recorder;
        this.timers = timers;
        this.clock = clock;
    }

    /** Comando de un cliente (presentador o jugador): cuenta como actividad de la sala (AC-28). */
    public <T> T execute(String code, Function<Room, T> command) {
        return execute(code, command, true);
    }

    private <T> T execute(String code, Function<Room, T> command, boolean clientActivity) {
        return registry.withRoom(code, room -> {
            long versionBefore = room.version();
            RoomStatus statusBefore = room.status();
            T result;
            try {
                result = command.apply(room);
            } catch (RuntimeException e) {
                room.drainEvents();
                throw e;
            }
            List<DomainEvent> events = room.drainEvents();
            if (room.version() != versionBefore) {
                if (clientActivity) {
                    room.touch(clock.instant());
                }
                if (statusBefore != RoomStatus.GAME_OVER && room.status() == RoomStatus.GAME_OVER) {
                    recorder.record(room);
                }
                syncTimer(room);
                broadcaster.publish(room, events);
            }
            return result;
        });
    }

    /** Lectura consistente bajo el lock, sin difundir nada. */
    public <T> T read(String code, Function<Room, T> query) {
        return registry.withRoom(code, query);
    }

    /** Plan D14: deja agendado el {@code turnDeadline} vigente de la sala (o ninguno). */
    private void syncTimer(Room room) {
        String code = room.code();
        Instant deadline = room.turnDeadline().orElse(null);
        timers.reschedule(code, deadline, () -> onTurnExpired(code, deadline));
    }

    /**
     * AC-48, AC-49 (plan D13): el vencimiento se aplica bajo el lock y solo si {@code deadline} sigue vigente. No
     * cuenta como actividad: una sala abandonada sigue expirando a las 2 h (AC-28).
     */
    void onTurnExpired(String code, Instant deadline) {
        try {
            execute(code, room -> room.expireTurn(deadline, clock.instant()), false);
        } catch (GameException e) {
            if (e.code() == ErrorCode.ROOM_NOT_FOUND) {
                timers.cancel(code);
            } else {
                log.warn("No se pudo aplicar el vencimiento de turno en la sala {}", code, e);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo aplicar el vencimiento de turno en la sala {}", code, e);
        }
    }
}
