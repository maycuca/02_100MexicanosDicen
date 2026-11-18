package mx.cienmexicanos.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import mx.cienmexicanos.game.domain.ErrorCode;
import mx.cienmexicanos.game.domain.GameException;
import mx.cienmexicanos.game.domain.Room;
import org.springframework.stereotype.Component;

/**
 * Salas activas en memoria (plan D1). Cada sala se lee y se muta bajo su propio lock (plan D2),
 * lo que serializa los comandos y garantiza "primer buzz gana" (AC-14).
 */
@Component
public class RoomRegistry {

    private static final int MAX_CODE_ATTEMPTS = 1_000;

    private record Entry(Room room, ReentrantLock lock) {}

    private final ConcurrentMap<String, Entry> rooms = new ConcurrentHashMap<>();
    private final RoomCodeGenerator codes;

    public RoomRegistry(RoomCodeGenerator codes) {
        this.codes = codes;
    }

    /** AC-01: registra una sala con un código único entre las activas. */
    public Room create(Function<String, Room> factory) {
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String code = codes.next();
            if (rooms.containsKey(code)) {
                continue;
            }
            Room room = factory.apply(code);
            if (rooms.putIfAbsent(code, new Entry(room, new ReentrantLock())) == null) {
                return room;
            }
        }
        throw new IllegalStateException("No hay códigos de sala disponibles");
    }

    /** Ejecuta {@code action} con la sala bloqueada; 404 ROOM_NOT_FOUND si no existe. */
    public <T> T withRoom(String code, Function<Room, T> action) {
        Entry entry = entry(code).orElseThrow(() -> notFound(code));
        entry.lock().lock();
        try {
            if (rooms.get(entry.room().code()) != entry) {
                throw notFound(code); // expiró mientras esperaba el lock
            }
            return action.apply(entry.room());
        } finally {
            entry.lock().unlock();
        }
    }

    public boolean exists(String code) {
        return entry(code).isPresent();
    }

    /** ¿Alguna sala activa cumple {@code predicate}? (se evalúa bajo el lock de cada sala). */
    public boolean anyMatch(Predicate<Room> predicate) {
        return findFirst(room -> predicate.test(room) ? Optional.of(Boolean.TRUE) : Optional.empty()).isPresent();
    }

    /** Primer resultado no vacío de {@code finder} entre las salas activas (bajo el lock de cada una). */
    public <T> Optional<T> findFirst(Function<Room, Optional<T>> finder) {
        for (Entry entry : List.copyOf(rooms.values())) {
            entry.lock().lock();
            try {
                Optional<T> found = finder.apply(entry.room());
                if (found.isPresent()) {
                    return found;
                }
            } finally {
                entry.lock().unlock();
            }
        }
        return Optional.empty();
    }

    /** AC-28: elimina las salas sin actividad desde antes de {@code cutoff}. */
    public List<String> evictInactiveSince(Instant cutoff) {
        List<String> evicted = new ArrayList<>();
        for (Entry entry : List.copyOf(rooms.values())) {
            entry.lock().lock();
            try {
                if (entry.room().lastActivity().isBefore(cutoff) && rooms.remove(entry.room().code(), entry)) {
                    evicted.add(entry.room().code());
                }
            } finally {
                entry.lock().unlock();
            }
        }
        return evicted;
    }

    /** Spec 005, AC-91: quita la sala (se llama con su lock tomado, dentro de {@link #withRoom}). */
    public void remove(Room room) {
        rooms.computeIfPresent(room.code(), (code, entry) -> entry.room() == room ? null : entry);
    }

    public int size() {
        return rooms.size();
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private Optional<Entry> entry(String code) {
        return Optional.ofNullable(rooms.get(normalize(code)));
    }

    private static GameException notFound(String code) {
        return new GameException(ErrorCode.ROOM_NOT_FOUND, "No existe la sala " + code);
    }
}
