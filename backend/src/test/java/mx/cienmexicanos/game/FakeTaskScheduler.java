package mx.cienmexicanos.game;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

/**
 * {@link TaskScheduler} simulado para probar el temporizador de turno de forma determinista: nada se ejecuta solo;
 * la prueba decide cuándo corren las tareas vencidas con {@link #runDue(Instant)}.
 */
public final class FakeTaskScheduler implements TaskScheduler {

    /** Tarea agendada; {@code cancel} solo la marca, como un {@code ScheduledFuture} real. */
    public static final class Task implements ScheduledFuture<Object> {
        private final Instant at;
        private final Runnable runnable;
        private boolean cancelled;
        private boolean done;

        Task(Instant at, Runnable runnable) {
            this.at = at;
            this.runnable = runnable;
        }

        public Instant at() { return at; }

        /** Ejecuta la tarea aunque esté cancelada: simula un disparo que ya estaba en curso (carrera). */
        public void runNow() {
            done = true;
            runnable.run();
        }

        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed o) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            return true;
        }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return done || cancelled; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
    }

    private final List<Task> tasks = new ArrayList<>();

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        Task t = new Task(startTime, task);
        tasks.add(t);
        return t;
    }

    /** Tareas aún no ejecutadas ni canceladas. */
    public List<Task> pending() {
        return tasks.stream().filter(t -> !t.isDone()).toList();
    }

    public Optional<Task> next() {
        return pending().stream().min(Comparator.comparing(Task::at));
    }

    /** Ejecuta en orden las tareas vencidas a {@code now}, incluidas las que agenden ellas mismas. */
    public int runDue(Instant now) {
        int ran = 0;
        Optional<Task> due;
        while ((due = next().filter(t -> !t.at().isAfter(now))).isPresent()) {
            due.get().runNow();
            ran++;
        }
        return ran;
    }

    @Override public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) { throw unsupported(); }
    @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) { throw unsupported(); }
    @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) { throw unsupported(); }
    @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) { throw unsupported(); }
    @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) { throw unsupported(); }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("FakeTaskScheduler solo admite schedule(Runnable, Instant)");
    }
}
