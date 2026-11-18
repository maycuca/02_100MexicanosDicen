package mx.cienmexicanos.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import mx.cienmexicanos.game.domain.AnswerSnapshot;
import mx.cienmexicanos.game.domain.ErrorCode;
import mx.cienmexicanos.game.domain.GameException;
import mx.cienmexicanos.game.domain.Player;
import mx.cienmexicanos.game.domain.QuestionSnapshot;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoomRegistryTest {

    static final Instant T0 = Instant.parse("2026-11-17T10:00:00Z");

    /** Generador que devuelve los códigos indicados en orden. */
    static RoomCodeGenerator scripted(String... codes) {
        Deque<String> queue = new ArrayDeque<>(List.of(codes));
        return new RoomCodeGenerator(RandomGenerator.getDefault()) {
            @Override
            public String next() {
                return queue.poll();
            }
        };
    }

    static Room room(String code, Instant at) {
        return new Room(code, "Rojo", "Verde", 3, 300, at);
    }

    @Test
    @DisplayName("AC-01 el código es único entre las salas activas")
    void uniqueCodes() {
        RoomRegistry registry = new RoomRegistry(scripted("AAAA", "AAAA", "BBBB"));
        Room first = registry.create(c -> room(c, T0));
        Room second = registry.create(c -> room(c, T0));
        assertThat(first.code()).isEqualTo("AAAA");
        assertThat(second.code()).isEqualTo("BBBB");
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("sala inexistente → ROOM_NOT_FOUND; el código no distingue mayúsculas")
    void lookup() {
        RoomRegistry registry = new RoomRegistry(scripted("KDMZ"));
        registry.create(c -> room(c, T0));
        assertThat(registry.exists("kdmz")).isTrue();
        assertThat(registry.withRoom(" kdmz ", Room::code)).isEqualTo("KDMZ");
        assertThatThrownBy(() -> registry.withRoom("ZZZZ", Room::code))
                .isInstanceOfSatisfying(GameException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.ROOM_NOT_FOUND));
        assertThat(RoomRegistry.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("AC-28 las salas sin actividad durante 2 horas se cierran")
    void expiration() {
        RoomRegistry registry = new RoomRegistry(scripted("OLDA", "NEWB"));
        registry.create(c -> room(c, T0));
        Room fresh = registry.create(c -> room(c, T0));
        fresh.touch(T0.plus(Duration.ofMinutes(90)));

        Instant now = T0.plus(Duration.ofHours(2)).plusSeconds(1);
        assertThat(registry.evictInactiveSince(now.minus(Duration.ofHours(2)))).containsExactly("OLDA");
        assertThat(registry.exists("OLDA")).isFalse();
        assertThat(registry.exists("NEWB")).isTrue();
        assertThat(registry.evictInactiveSince(now.minus(Duration.ofHours(2)))).isEmpty();
    }

    @Test
    @DisplayName("findFirst y anyMatch recorren las salas activas")
    void search() {
        RoomRegistry registry = new RoomRegistry(scripted("AAAA", "BBBB"));
        registry.create(c -> room(c, T0));
        Room b = registry.create(c -> room(c, T0));
        String token = b.hostToken().toString();
        assertThat(registry.anyMatch(r -> r.isPresenterToken(token))).isTrue();
        assertThat(registry.anyMatch(r -> r.isPresenterToken("x"))).isFalse();
        assertThat(registry.findFirst(r -> r.isPresenterToken(token)
                ? java.util.Optional.of(r.code()) : java.util.Optional.<String>empty())).contains("BBBB");
    }

    @Test
    @DisplayName("AC-14 con el lock por sala, de 20 buzz simultáneos solo uno gana (plan D2)")
    void concurrentBuzz() throws Exception {
        RoomRegistry registry = new RoomRegistry(scripted("KDMZ"));
        Room room = registry.create(c -> room(c, T0));
        Player a = room.addPlayer("Ana", TeamId.A);
        Player b = room.addPlayer("Beto", TeamId.B);
        room.start(IntStream.rangeClosed(1, 3).mapToObj(q -> new QuestionSnapshot(q, "P" + q,
                List.of(new AnswerSnapshot(q * 10, "x", 10, 1), new AnswerSnapshot(q * 10 + 1, "y", 5, 2),
                        new AnswerSnapshot(q * 10 + 2, "z", 1, 3)))).toList());

        AtomicInteger winners = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 20; i++) {
                Player p = i % 2 == 0 ? a : b;
                pool.submit(() -> {
                    go.await();
                    if (registry.withRoom("KDMZ", r -> r.buzz(p.id(), java.time.Instant.EPOCH))) {
                        winners.incrementAndGet();
                    }
                    return null;
                });
            }
            go.countDown();
        }
        assertThat(winners).hasValue(1);
    }
}
