package mx.cienmexicanos.game.domain;

import static mx.cienmexicanos.game.domain.Fixtures.currentAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Tabla de transiciones de data-model.md: válidas e inválidas (→ INVALID_STATE). */
class RoomStateMachineTest {

    enum Command { START, BUZZ, REVEAL, STRIKE, ASSIGN_CONTROL, NEXT_ROUND, REMATCH }

    static final Map<RoomStatus, Supplier<Room>> SETUP = Map.of(
            RoomStatus.LOBBY, () -> Fixtures.lobby(5, 300),
            RoomStatus.FACE_OFF, Fixtures::faceOff,
            RoomStatus.PLAYING, () -> Fixtures.playing(TeamId.A),
            RoomStatus.STEAL, Fixtures::steal,
            RoomStatus.ROUND_END, Fixtures::roundEnd,
            RoomStatus.GAME_OVER, Fixtures::gameOver);

    static final Map<RoomStatus, Set<Command>> VALID = Map.of(
            RoomStatus.LOBBY, EnumSet.of(Command.START),
            RoomStatus.FACE_OFF, EnumSet.of(Command.BUZZ, Command.REVEAL, Command.STRIKE, Command.ASSIGN_CONTROL),
            RoomStatus.PLAYING, EnumSet.of(Command.REVEAL, Command.STRIKE),
            RoomStatus.STEAL, EnumSet.of(Command.REVEAL, Command.STRIKE),
            RoomStatus.ROUND_END, EnumSet.of(Command.NEXT_ROUND),
            RoomStatus.GAME_OVER, EnumSet.of(Command.REMATCH));

    static void run(Room room, Command command) {
        switch (command) {
            case START -> room.start(Fixtures.questions(5));
            case BUZZ -> room.buzz(room.currentRound().map(r -> r.faceOff().playerA()).orElse(UUID.randomUUID()), Fixtures.T0);
            case REVEAL -> {
                if (room.status() == RoomStatus.FACE_OFF) { // spec 006 AC-100: sin buzz no se revela
                    room.buzz(room.currentRound().orElseThrow().faceOff().playerA(), Fixtures.T0);
                }
                room.reveal(room.currentRound()
                    .flatMap(r -> r.slots().stream().filter(s -> !s.revealed()).findFirst())
                    .map(Slot::answerId).orElse(Fixtures.answerId(1, 1)), Fixtures.T0);
            }
            case STRIKE -> {
                if (room.status() == RoomStatus.FACE_OFF) { // AC-40: sin buzz no hay equipo en turno
                    room.buzz(room.currentRound().orElseThrow().faceOff().playerA(), Fixtures.T0);
                }
                room.strike(Fixtures.T0);
            }
            case ASSIGN_CONTROL -> room.assignControl(TeamId.A, Fixtures.T0);
            case NEXT_ROUND -> room.nextRound();
            case REMATCH -> room.rematch();
        }
    }

    static Stream<Arguments> invalidTransitions() {
        return SETUP.keySet().stream().flatMap(status -> EnumSet.allOf(Command.class).stream()
                .filter(c -> !VALID.get(status).contains(c))
                .map(c -> Arguments.of(status, c)));
    }

    static Stream<Arguments> validTransitions() {
        return SETUP.keySet().stream().flatMap(status -> VALID.get(status).stream().map(c -> Arguments.of(status, c)));
    }

    @ParameterizedTest(name = "{0} + {1} → INVALID_STATE")
    @MethodSource("invalidTransitions")
    @DisplayName("data-model: cualquier comando fuera de la tabla → INVALID_STATE")
    void invalidTransitionsAreRejected(RoomStatus status, Command command) {
        Room room = SETUP.get(status).get();
        assertThat(room.status()).isEqualTo(status);
        long version = room.version();

        assertThatThrownBy(() -> run(room, command))
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_STATE));
        assertThat(room.status()).isEqualTo(status);
        assertThat(room.version()).isEqualTo(version);
    }

    @ParameterizedTest(name = "{0} + {1} es válido")
    @MethodSource("validTransitions")
    @DisplayName("data-model: los comandos de la tabla se aceptan y suben la versión")
    void validTransitionsAreAccepted(RoomStatus status, Command command) {
        Room room = SETUP.get(status).get();
        long version = room.version();
        run(room, command);
        assertThat(room.version()).isGreaterThan(version);
    }

    @Nested
    class TableRows {

        @Test
        @DisplayName("AC-11 LOBBY --start--> FACE_OFF de la ronda 1")
        void lobbyStart() {
            Room room = Fixtures.lobby(5, 300);
            room.start(Fixtures.questions(5));
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
            assertThat(room.currentRound().orElseThrow().number()).isEqualTo(1);
            assertThat(room.drainEvents()).extracting(DomainEvent::type).containsExactly(GameEventType.GAME_STARTED);
        }

        @Test
        @DisplayName("FACE_OFF --buzz/reveal que no es la #1--> FACE_OFF (spec 006)")
        void faceOffStays() {
            Room room = Fixtures.faceOff();
            room.buzz(room.currentRound().orElseThrow().faceOff().playerB(), Fixtures.T0);
            room.reveal(currentAnswer(room, 2), Fixtures.T0); // spec 006 AC-93: no es la #1 → turno al rival
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
            assertThat(room.currentRound().orElseThrow().strikes()).as("las X del cara a cara no cuentan").isZero();
        }

        @Test
        @DisplayName("AC-15 FACE_OFF --assignControl--> PLAYING")
        void faceOffAssign() {
            Room room = Fixtures.faceOff();
            room.assignControl(TeamId.B, Fixtures.T0);
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(room.currentRound().orElseThrow().controllingTeam()).contains(TeamId.B);
        }

        @Test
        @DisplayName("AC-16 PLAYING --reveal (quedan ocultas) / strike (<3)--> PLAYING")
        void playingStays() {
            Room room = Fixtures.playing(TeamId.A);
            room.reveal(currentAnswer(room, 1), Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(room.currentRound().orElseThrow().strikes()).isEqualTo(2);
        }

        @Test
        @DisplayName("AC-18 PLAYING --reveal (todas)--> ROUND_END con bote para el equipo con control")
        void playingAllRevealed() {
            Room room = Fixtures.playing(TeamId.B);
            for (int pos = 1; pos <= 4; pos++) {
                room.reveal(currentAnswer(room, pos), Fixtures.T0);
            }
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(room.currentRound().orElseThrow().winner()).contains(TeamId.B);
            assertThat(room.team(TeamId.B).score()).isEqualTo(100); // ronda 1 de 5 → ×1
        }

        @Test
        @DisplayName("AC-18 PLAYING --3 strikes--> STEAL")
        void playingToSteal() {
            Room room = Fixtures.steal();
            assertThat(room.status()).isEqualTo(RoomStatus.STEAL);
            assertThat(room.drainEvents()).extracting(DomainEvent::type).endsWith(GameEventType.STEAL_STARTED, GameEventType.TURN_CHANGED);
        }

        @Test
        @DisplayName("AC-19 STEAL --reveal--> ROUND_END, bote al rival")
        void stealReveal() {
            Room room = Fixtures.playing(TeamId.A);
            room.reveal(currentAnswer(room, 1), Fixtures.T0); // 40 al bote
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.reveal(currentAnswer(room, 2), Fixtures.T0); // el rival acierta
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(room.currentRound().orElseThrow().winner()).contains(TeamId.B);
            assertThat(room.team(TeamId.B).score()).isEqualTo(40);
            assertThat(room.team(TeamId.A).score()).isZero();
        }

        @Test
        @DisplayName("AC-19 STEAL --strike--> ROUND_END, bote al equipo con control")
        void stealStrike() {
            Room room = Fixtures.playing(TeamId.A);
            room.reveal(currentAnswer(room, 3), Fixtures.T0); // 20
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(room.team(TeamId.A).score()).isEqualTo(20);
        }

        @Test
        @DisplayName("ROUND_END --nextRound--> FACE_OFF de la siguiente ronda")
        void nextRound() {
            Room room = Fixtures.roundEnd();
            room.nextRound();
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
            assertThat(room.currentRound().orElseThrow().number()).isEqualTo(2);
            assertThat(room.currentRound().orElseThrow().strikes()).isZero();
            assertThat(room.currentRound().orElseThrow().pot()).isZero();
        }

        @Test
        @DisplayName("AC-22 ROUND_END --nextRound (última ronda)--> GAME_OVER")
        void lastRound() {
            Room room = Fixtures.gameOver();
            assertThat(room.status()).isEqualTo(RoomStatus.GAME_OVER);
            assertThat(room.winner()).contains("TIE");
        }

        @Test
        @DisplayName("AC-23 GAME_OVER --rematch--> LOBBY con los mismos jugadores y marcador en cero")
        void rematch() {
            Room room = Fixtures.gameOver();
            room.rematch();
            assertThat(room.status()).isEqualTo(RoomStatus.LOBBY);
            assertThat(room.allPlayers()).hasSize(4);
            assertThat(room.teams()).allSatisfy(t -> assertThat(t.score()).isZero());
            assertThat(room.currentRound()).isEmpty();
            assertThat(room.winner()).isEmpty();
            room.start(Fixtures.questions(5));
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
        }
    }
}
