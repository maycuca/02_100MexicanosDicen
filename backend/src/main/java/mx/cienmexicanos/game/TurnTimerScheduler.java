package mx.cienmexicanos.game;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Spec 002, plan D13/D14: un único vencimiento de turno agendado por sala. {@code RoomCommandRunner} lo reagenda
 * o cancela tras cada comando según el {@code turnDeadline} resultante; al vencer ejecuta la acción recibida, que
 * aplica {@code Room.expireTurn(deadline, now)} bajo el lock de la sala (AC-48, AC-49).
 * <p>
 * Usa su propio {@link ThreadPoolTaskScheduler} para no competir con el {@code TaskScheduler} del broker STOMP ni
 * con el de {@code @Scheduled}.
 */
@Component
public class TurnTimerScheduler implements DisposableBean {

    private record Pending(Instant deadline, ScheduledFuture<?> future) {}

    private final TaskScheduler scheduler;
    private final ThreadPoolTaskScheduler owned;
    private final ConcurrentMap<String, Pending> pending = new ConcurrentHashMap<>();

    /** Constructor que usa Spring: crea su propio pool de un hilo. */
    public TurnTimerScheduler() {
        this(newPool(), true);
    }

    /** Para pruebas: un {@link TaskScheduler} controlado. */
    public TurnTimerScheduler(TaskScheduler scheduler) {
        this(scheduler, false);
    }

    private TurnTimerScheduler(TaskScheduler scheduler, boolean owned) {
        this.scheduler = scheduler;
        this.owned = owned ? (ThreadPoolTaskScheduler) scheduler : null;
    }

    private static ThreadPoolTaskScheduler newPool() {
        ThreadPoolTaskScheduler pool = new ThreadPoolTaskScheduler();
        pool.setPoolSize(1);
        pool.setThreadNamePrefix("turn-timer-");
        pool.setRemoveOnCancelPolicy(true);
        pool.initialize();
        return pool;
    }

    /**
     * Deja agendado exactamente {@code deadline} para la sala: si ya lo está no hace nada; si no, cancela el anterior
     * y agenda {@code onExpire}. Con {@code deadline} null solo cancela.
     */
    public void reschedule(String code, Instant deadline, Runnable onExpire) {
        pending.compute(code, (key, current) -> {
            if (current != null && Objects.equals(current.deadline(), deadline)) {
                return current;
            }
            if (current != null) {
                current.future().cancel(false);
            }
            return deadline == null ? null : new Pending(deadline, scheduler.schedule(onExpire, deadline));
        });
    }

    /** Cancela el vencimiento pendiente de la sala (sala cerrada o expirada). */
    public void cancel(String code) {
        Pending removed = pending.remove(code);
        if (removed != null) {
            removed.future().cancel(false);
        }
    }

    /** Vencimiento agendado para la sala, si hay (diagnóstico y pruebas). */
    public Instant scheduledDeadline(String code) {
        Pending p = pending.get(code);
        return p == null ? null : p.deadline();
    }

    @Override
    public void destroy() {
        pending.values().forEach(p -> p.future().cancel(false));
        pending.clear();
        if (owned != null) {
            owned.shutdown();
        }
    }
}
