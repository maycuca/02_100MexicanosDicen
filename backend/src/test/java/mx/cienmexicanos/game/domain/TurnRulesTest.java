package mx.cienmexicanos.game.domain;

import static mx.cienmexicanos.game.domain.Fixtures.T0;
import static mx.cienmexicanos.game.domain.Fixtures.currentAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec 002: equipo en turno, X por equipo, temporizador de turno y sonido (T701…T706); spec 005 (T1001, T1002). */
class TurnRulesTest {

    static final Instant T1 = T0.plusSeconds(5);
    static final Instant T2 = T0.plusSeconds(9);

    static Room lobby(int turnSeconds, boolean autoStrike) {
        Room room = new Room("KDMZ", "Equipo Rojo", "Equipo Verde", 5, 300, turnSeconds, autoStrike, T0);
        room.addPlayer("Ana", TeamId.A);
        room.addPlayer("Beto", TeamId.B);
        room.drainEvents();
        return room;
    }

    static Room faceOff(int turnSeconds, boolean autoStrike) {
        Room room = lobby(turnSeconds, autoStrike);
        room.start(Fixtures.questions(5));
        room.drainEvents();
        return room;
    }

    static Room playing(TeamId control, int turnSeconds, boolean autoStrike) {
        Room room = faceOff(turnSeconds, autoStrike);
        room.assignControl(control, T0);
        room.drainEvents();
        return room;
    }

    static Round round(Room room) {
        return room.currentRound().orElseThrow();
    }

    static UUID designated(Room room, TeamId team) {
        FaceOff fo = round(room).faceOff();
        return team == TeamId.A ? fo.playerA() : fo.playerB();
    }

    static TeamId activeTeam(Room room) {
        return round(room).activeTeam().orElse(null);
    }

    static void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    /** Spec 005, AC-80: el presentador inicia el temporizador; devuelve el vencimiento. */
    static Instant startTimer(Room room, Instant now) {
        room.controlTimer(TimerAction.START, now);
        room.drainEvents();
        return room.turnDeadline().orElseThrow();
    }

    static List<DomainEvent> ofType(List<DomainEvent> events, GameEventType type) {
        return events.stream().filter(e -> e.type() == type).toList();
    }

    @Nested
    class ActiveTeam {

        @Test
        @DisplayName("AC-35 LOBBY sin ronda; FACE_OFF sin buzz → activeTeam null")
        void lobbyAndFaceOffWithoutBuzz() {
            Room room = lobby(20, true);
            assertThat(room.currentRound()).isEmpty();
            room.start(Fixtures.questions(5));
            assertThat(activeTeam(room)).isNull();
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(ofType(room.drainEvents(), GameEventType.TURN_CHANGED)).as("null → null no es cambio").isEmpty();
        }

        @Test
        @DisplayName("AC-35 AC-36 FACE_OFF: el buzz deja en turno al equipo del ganador y publica TURN_CHANGED")
        void buzzSetsTurn() {
            Room room = faceOff(20, true);
            long version = room.version();
            room.buzz(designated(room, TeamId.B), T0);
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(room.version()).isGreaterThan(version);
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.BUZZ_WON, TeamId.B), tuple(GameEventType.TURN_CHANGED, TeamId.B));
        }

        @Test
        @DisplayName("spec 006 AC-93 AC-96 FACE_OFF: el primero abre una que no es la #1 → turno al rival; el rival abre una más baja → control al primero")
        void faceOffAlternates() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.drainEvents();

            room.reveal(currentAnswer(room, 2), T0);
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
            assertThat(round(room).faceOff().firstAnswerPosition()).contains(2);
            room.reveal(currentAnswer(room, 3), T0);
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(round(room).controllingTeam()).contains(TeamId.A);
            assertThat(activeTeam(room)).isEqualTo(TeamId.A);
            assertThat(round(room).pot()).isEqualTo(50);

            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.ANSWER_REVEALED, TeamId.A), tuple(GameEventType.TURN_CHANGED, TeamId.B),
                    tuple(GameEventType.ANSWER_REVEALED, TeamId.B), tuple(GameEventType.CONTROL_ASSIGNED, TeamId.A),
                    tuple(GameEventType.TURN_CHANGED, TeamId.A));
        }

        @Test
        @DisplayName("spec 006 AC-100 FACE_OFF sin buzz: revelar → NO_ACTIVE_TEAM sin cambiar nada")
        void revealWithoutBuzzKeepsNull() {
            Room room = faceOff(20, true);
            long version = room.version();
            assertError(ErrorCode.NO_ACTIVE_TEAM, () -> room.reveal(currentAnswer(room, 1), T0));
            assertThat(round(room).slots()).noneMatch(Slot::revealed);
            assertThat(round(room).pot()).isZero();
            assertThat(room.version()).isEqualTo(version);
            assertThat(room.drainEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-35 AC-36 PLAYING → controllingTeam; STEAL → el rival; ROUND_END → null")
        void playingStealRoundEnd() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.B), T0); // turno B
            room.drainEvents();

            room.assignControl(TeamId.A, T0);
            assertThat(activeTeam(room)).isEqualTo(TeamId.A);
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.CONTROL_ASSIGNED, TeamId.A), tuple(GameEventType.TURN_CHANGED, TeamId.A));

            room.strike(T0);
            room.strike(T0);
            assertThat(ofType(room.drainEvents(), GameEventType.TURN_CHANGED)).as("sigue A").isEmpty();
            room.strike(T0);
            assertThat(room.status()).isEqualTo(RoomStatus.STEAL);
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(ofType(room.drainEvents(), GameEventType.TURN_CHANGED)).extracting(DomainEvent::team)
                    .containsExactly(TeamId.B);

            room.strike(T0);
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(activeTeam(room)).isNull();
            assertThat(room.turnDeadline()).isEmpty();
            List<DomainEvent> events = room.drainEvents();
            assertThat(events).extracting(DomainEvent::type).endsWith(GameEventType.ROUND_ENDED,
                    GameEventType.TURN_CHANGED);
            assertThat(events.getLast().team()).isNull();
        }

        @Test
        @DisplayName("AC-35 asignar control al equipo que ya está en turno no publica TURN_CHANGED")
        void assignSameTeam() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.drainEvents();
            room.assignControl(TeamId.A, T1);
            assertThat(room.drainEvents()).extracting(DomainEvent::type)
                    .containsExactly(GameEventType.CONTROL_ASSIGNED);
            assertThat(round(room).turnDeadline()).as("spec 005 AC-79: el control no arranca el temporizador").isEmpty();
        }

        @Test
        @DisplayName("AC-35 al revelar todo en PLAYING la ronda termina y activeTeam vuelve a null")
        void allRevealedEndsTurn() {
            Room room = playing(TeamId.B, 20, true);
            for (int pos = 1; pos <= 4; pos++) {
                room.reveal(currentAnswer(room, pos), T0);
            }
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(activeTeam(room)).isNull();
            assertThat(room.drainEvents()).extracting(DomainEvent::type).endsWith(GameEventType.ROUND_ENDED,
                    GameEventType.TURN_CHANGED);
        }

        @Test
        @DisplayName("AC-35 GAME_OVER y la nueva ronda arrancan sin equipo en turno")
        void nextRoundAndGameOver() {
            Room room = playing(TeamId.A, 20, true);
            for (int i = 0; i < 4; i++) {
                room.strike(T0);
            }
            room.nextRound();
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
            assertThat(activeTeam(room)).isNull();
            assertThat(round(room).strikesOf(TeamId.A)).isZero();
            assertThat(round(room).strikesOf(TeamId.B)).isZero();

            Room over = Fixtures.gameOver();
            assertThat(over.currentRound().flatMap(Round::activeTeam)).isEmpty();
            assertThat(over.turnDeadline()).isEmpty();
        }
    }

    @Nested
    class Strikes {

        @Test
        @DisplayName("AC-40 X en FACE_OFF sin buzz → NO_ACTIVE_TEAM sin cambiar el estado (C-2)")
        void noActiveTeam() {
            Room room = faceOff(20, true);
            long version = room.version();
            assertError(ErrorCode.NO_ACTIVE_TEAM, () -> room.strike(T0));
            assertThat(room.version()).isEqualTo(version);
            assertThat(room.drainEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-40 C-6 X en LOBBY, ROUND_END o GAME_OVER sigue siendo INVALID_STATE")
        void invalidStateStillWins() {
            assertError(ErrorCode.INVALID_STATE, () -> lobby(20, true).strike(T0));
            assertError(ErrorCode.INVALID_STATE, () -> Fixtures.roundEnd().strike(T0));
            assertError(ErrorCode.INVALID_STATE, () -> Fixtures.gameOver().strike(T0));
        }

        @Test
        @DisplayName("spec 005 AC-85 fallo en FACE_OFF: no es X, FACE_OFF_MISS con team y el turno pasa al rival")
        void faceOffStrike() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.B), T0);
            room.drainEvents();
            room.strike(T0);

            Round r = round(room);
            assertThat(r.strikes()).isZero();
            assertThat(r.strikesOf(TeamId.A)).isZero();
            assertThat(r.strikesOf(TeamId.B)).isZero();
            assertThat(activeTeam(room)).isEqualTo(TeamId.A);
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team, DomainEvent::strikes)
                    .containsExactly(tuple(GameEventType.FACE_OFF_MISS, TeamId.B, null),
                            tuple(GameEventType.TURN_CHANGED, TeamId.A, null));
        }

        @Test
        @DisplayName("spec 006 AC-94 AC-97 si el rival también falla, termina el cara a cara y el control es del primero; sin STRIKE")
        void faceOffMissAlternates() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.drainEvents();
            room.strike(T0); // A falla → B
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
            room.strike(T0); // B falla → termina; control A
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(round(room).controllingTeam()).contains(TeamId.A);
            assertThat(activeTeam(room)).isEqualTo(TeamId.A);
            List<DomainEvent> events = room.drainEvents();
            assertThat(ofType(events, GameEventType.STRIKE)).isEmpty();
            assertThat(events).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.FACE_OFF_MISS, TeamId.A), tuple(GameEventType.TURN_CHANGED, TeamId.B),
                    tuple(GameEventType.FACE_OFF_MISS, TeamId.B), tuple(GameEventType.CONTROL_ASSIGNED, TeamId.A),
                    tuple(GameEventType.TURN_CHANGED, TeamId.A));
            assertThat(round(room).strikesOf(TeamId.A)).isZero();
            assertThat(round(room).strikesOf(TeamId.B)).isZero();
        }

        @Test
        @DisplayName("AC-41 AC-43 PLAYING: las X van al equipo con control y strikesByTeam[control] == strikes")
        void playingStrikes() {
            Room room = playing(TeamId.B, 20, true);
            for (int i = 1; i <= 3; i++) {
                room.strike(T0);
                Round r = round(room);
                assertThat(r.strikesOf(TeamId.B)).isEqualTo(i).isEqualTo(r.strikes());
                assertThat(r.strikesOf(TeamId.A)).isZero();
            }
            assertThat(ofType(room.drainEvents(), GameEventType.STRIKE))
                    .extracting(DomainEvent::team, DomainEvent::strikes)
                    .containsExactly(tuple(TeamId.B, 1), tuple(TeamId.B, 2), tuple(TeamId.B, 3));
        }

        @Test
        @DisplayName("AC-41 AC-43 STEAL: la X es del equipo que roba (0–1) y strikes del control se queda en 3")
        void stealStrike() {
            Room room = playing(TeamId.A, 20, true);
            room.reveal(currentAnswer(room, 2), T0); // 30 al bote
            room.strike(T0);
            room.strike(T0);
            room.strike(T0);
            room.drainEvents();
            room.strike(T0);

            Round r = round(room);
            assertThat(r.strikes()).isEqualTo(3);
            assertThat(r.strikesOf(TeamId.A)).isEqualTo(3).isEqualTo(r.strikes());
            assertThat(r.strikesOf(TeamId.B)).isEqualTo(1);
            assertThat(room.team(TeamId.A).score()).as("AC-19 el bote se queda con el control").isEqualTo(30);
            assertThat(ofType(room.drainEvents(), GameEventType.STRIKE)).singleElement()
                    .satisfies(e -> {
                        assertThat(e.team()).isEqualTo(TeamId.B);
                        assertThat(e.strikes()).isEqualTo(1);
                    });
        }

        @Test
        @DisplayName("AC-41 un robo acertado no suma X: strikesByTeam del que roba queda en 0")
        void stealRevealNoStrike() {
            Room room = playing(TeamId.A, 20, true);
            room.strike(T0);
            room.strike(T0);
            room.strike(T0);
            room.reveal(currentAnswer(room, 1), T0);
            assertThat(round(room).strikesOf(TeamId.B)).isZero();
            assertThat(round(room).strikesOf(TeamId.A)).isEqualTo(3);
        }

        @Test
        @DisplayName("AC-41 los fallos del cara a cara no cuentan en strikesByTeam al pasar a PLAYING")
        void faceOffStrikesDoNotCarry() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.strike(T0); // A falla
            room.strike(T0); // B falla → control A (spec 006 AC-97)
            room.strike(T0);
            assertThat(round(room).strikesOf(TeamId.A)).isEqualTo(1).isEqualTo(round(room).strikes());
            assertThat(round(room).strikesOf(TeamId.B)).isZero();
        }

    }

    @Nested
    class FaceOffRules {

        @Test
        @DisplayName("spec 006 AC-92 AC-99 el primero abre la #1 → se queda con el turno y empieza PLAYING")
        void topAnswerWins() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.B), T0);
            room.drainEvents();
            room.reveal(currentAnswer(room, 1), T0);
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(round(room).controllingTeam()).contains(TeamId.B);
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(round(room).pot()).isEqualTo(40);
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.ANSWER_REVEALED, TeamId.B), tuple(GameEventType.CONTROL_ASSIGNED, TeamId.B));
        }

        @Test
        @DisplayName("spec 006 AC-95 el rival abre una más alta que la del primero → se queda con el turno")
        void secondBeatsFirst() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.reveal(currentAnswer(room, 3), T0); // A: #3 → B
            room.reveal(currentAnswer(room, 2), T0); // B: #2 > #3
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(round(room).controllingTeam()).contains(TeamId.B);
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
        }

        @Test
        @DisplayName("spec 006 AC-94 AC-95 el primero falla y el rival abre cualquier respuesta → el rival se queda con el turno")
        void secondAnswersAfterMiss() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.strike(T0); // A falla → B
            assertThat(round(room).faceOff().firstAnswerPosition()).isEmpty();
            room.reveal(currentAnswer(room, 4), T0);
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(round(room).controllingTeam()).contains(TeamId.B);
        }

        @Test
        @DisplayName("spec 006 AC-97 el primero abrió una respuesta y al rival se le acaba el tiempo → control al primero")
        void secondTimesOut() {
            Room room = faceOff(20, false); // AC-98: aunque "X automática" esté apagada
            room.buzz(designated(room, TeamId.A), T0);
            room.reveal(currentAnswer(room, 3), T0); // → B
            Instant deadline = startTimer(room, T0);
            assertThat(room.expireTurn(deadline, deadline)).isTrue();
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(round(room).controllingTeam()).contains(TeamId.A);
            assertThat(room.drainEvents()).extracting(DomainEvent::type).containsExactly(GameEventType.TIMER_EXPIRED,
                    GameEventType.FACE_OFF_MISS, GameEventType.CONTROL_ASSIGNED, GameEventType.TURN_CHANGED);
        }

        @Test
        @DisplayName("spec 006 AC-98 en FACE_OFF el vencimiento es fallo aunque \"X automática\" esté apagada")
        void expiryAlwaysMissesInFaceOff() {
            Room room = faceOff(20, false);
            room.buzz(designated(room, TeamId.A), T0);
            Instant deadline = startTimer(room, T0);
            assertThat(room.expireTurn(deadline, deadline)).isTrue();
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
        }

        @Test
        @DisplayName("spec 006 AC-101 FaceOff expone firstTeam y firstAnswerPosition")
        void exposesFirstAttempt() {
            Room room = faceOff(20, true);
            assertThat(round(room).faceOff().firstTeam()).isEmpty();
            room.buzz(designated(room, TeamId.B), T0);
            assertThat(round(room).faceOff().firstTeam()).contains(TeamId.B);
            assertThat(round(room).faceOff().attempt()).isEqualTo(1);
            room.reveal(currentAnswer(room, 2), T0);
            assertThat(round(room).faceOff().attempt()).isEqualTo(2);
            assertThat(round(room).faceOff().firstAnswerPosition()).contains(2);
        }

        @Test
        @DisplayName("spec 006 C-28 assignControl sigue como corrección manual en el segundo intento")
        void manualCorrection() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.reveal(currentAnswer(room, 2), T0);
            room.assignControl(TeamId.A, T0);
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(round(room).controllingTeam()).contains(TeamId.A);
        }
    }

    @Nested
    class Configuration {

        @Test
        @DisplayName("AC-46 por defecto: 20 s, X automática y sonido activado")
        void defaults() {
            Room room = Fixtures.room(5, 300);
            assertThat(room.turnSeconds()).isEqualTo(20);
            assertThat(room.autoStrikeOnTimeout()).isTrue();
            assertThat(room.soundEnabled()).isTrue();
        }

        @ParameterizedTest(name = "turnSeconds={0} es válido")
        @ValueSource(ints = {0, 10, 20, 60})
        @DisplayName("AC-46 turnSeconds válido: 0 o 10–60")
        void validTurnSeconds(int seconds) {
            Room room = new Room("ABCD", "A", "B", 5, 300, seconds, false, T0);
            assertThat(room.turnSeconds()).isEqualTo(seconds);
            assertThat(room.autoStrikeOnTimeout()).isFalse();
        }

        @ParameterizedTest(name = "turnSeconds={0} → VALIDATION_ERROR")
        @ValueSource(ints = {-1, 1, 9, 61, 120})
        @DisplayName("AC-46 turnSeconds fuera de rango → VALIDATION_ERROR")
        void invalidTurnSeconds(int seconds) {
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", "A", "B", 5, 300, seconds, true, T0));
        }

        @Test
        @DisplayName("AC-46 AC-53 la revancha conserva turnSeconds, autoStrikeOnTimeout y soundEnabled")
        void rematchKeepsConfig() {
            Room room = new Room("ABCD", "A", "B", 3, 1000, 45, false, T0);
            room.addPlayer("Ana", TeamId.A);
            room.addPlayer("Beto", TeamId.B);
            room.setSound(false);
            room.start(Fixtures.questions(3));
            for (int r = 1; r <= 3; r++) {
                room.assignControl(TeamId.A, T0);
                for (int i = 0; i < 4; i++) {
                    room.strike(T0);
                }
                room.nextRound();
            }
            room.rematch();
            assertThat(room.turnSeconds()).isEqualTo(45);
            assertThat(room.autoStrikeOnTimeout()).isFalse();
            assertThat(room.soundEnabled()).isFalse();
        }
    }

    @Nested
    class ManualTimer {

        @Test
        @DisplayName("spec 005 AC-79 la ronda empieza detenida y completa; buzz, revelar, X y control no lo arrancan")
        void neverStartsByItself() {
            Room room = faceOff(30, true);
            assertThat(round(room).timerRunning()).isFalse();
            assertThat(round(room).timerRemainingMs()).isEqualTo(30_000);
            room.buzz(designated(room, TeamId.A), T0);
            room.reveal(currentAnswer(room, 4), T1);
            room.assignControl(TeamId.B, T2);
            room.strike(T2);
            room.reveal(currentAnswer(room, 1), T2);
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(round(room).timerRemainingMs()).isEqualTo(30_000);
            assertThat(ofType(room.drainEvents(), GameEventType.TIMER_CHANGED)).isEmpty();
        }

        @Test
        @DisplayName("spec 005 AC-80 AC-81 Iniciar, Parar, Reanudar y Restablecer publican TIMER_CHANGED")
        void startPauseResumeReset() {
            Room room = playing(TeamId.A, 20, true);
            long version = room.version();

            assertThat(room.controlTimer(TimerAction.START, T0)).isTrue();
            assertThat(room.turnDeadline()).contains(T0.plusSeconds(20));
            assertThat(round(room).timerRunning()).isTrue();

            assertThat(room.controlTimer(TimerAction.PAUSE, T0.plusSeconds(7))).isTrue();
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(round(room).timerRemainingMs()).isEqualTo(13_000);

            Instant resume = T0.plusSeconds(60);
            assertThat(room.controlTimer(TimerAction.START, resume)).isTrue();
            assertThat(room.turnDeadline()).as("Reanudar corre desde lo que queda").contains(resume.plusSeconds(13));

            assertThat(room.controlTimer(TimerAction.RESET, resume.plusSeconds(2))).isTrue();
            assertThat(round(room).timerRunning()).isFalse();
            assertThat(round(room).timerRemainingMs()).isEqualTo(20_000);

            assertThat(room.version()).isEqualTo(version + 4);
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.TIMER_CHANGED, TeamId.A), tuple(GameEventType.TIMER_CHANGED, TeamId.A),
                    tuple(GameEventType.TIMER_CHANGED, TeamId.A), tuple(GameEventType.TIMER_CHANGED, TeamId.A));
        }

        @Test
        @DisplayName("spec 005 AC-81 sin cambio no hay evento ni versión: START corriendo, PAUSE detenido, RESET completo")
        void noOps() {
            Room room = playing(TeamId.A, 20, true);
            long version = room.version();
            assertThat(room.controlTimer(TimerAction.PAUSE, T0)).isFalse();
            assertThat(room.controlTimer(TimerAction.RESET, T0)).isFalse();
            room.controlTimer(TimerAction.START, T0);
            room.drainEvents();
            assertThat(room.controlTimer(TimerAction.START, T1)).isFalse();
            assertThat(room.turnDeadline()).contains(T0.plusSeconds(20));
            assertThat(room.version()).isEqualTo(version + 1);
            assertThat(room.drainEvents()).isEmpty();
        }

        @Test
        @DisplayName("spec 005 AC-81 C-26 errores: INVALID_STATE fuera de juego, TIMER_DISABLED, VALIDATION_ERROR; START sin turno sí")
        void errors() {
            assertError(ErrorCode.INVALID_STATE, () -> lobby(20, true).controlTimer(TimerAction.START, T0));
            assertError(ErrorCode.INVALID_STATE, () -> Fixtures.roundEnd().controlTimer(TimerAction.START, T0));
            assertError(ErrorCode.TIMER_DISABLED, () -> faceOff(0, true).controlTimer(TimerAction.START, T0));
            assertError(ErrorCode.VALIDATION_ERROR, () -> faceOff(20, true).controlTimer(null, T0));

            Room room = faceOff(20, true);
            assertThat(room.controlTimer(TimerAction.START, T0)).as("C-26 cara a cara antes del buzzer").isTrue();
        }

        @Test
        @DisplayName("spec 005 C-25 cambiar de turno no toca el temporizador que corre")
        void turnChangeKeepsTimer() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            Instant deadline = startTimer(room, T0);
            room.strike(T1); // A falla → B
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(room.turnDeadline()).contains(deadline);
        }

        @Test
        @DisplayName("spec 005 AC-84 al terminar la ronda y en la siguiente el temporizador queda detenido y completo")
        void roundEndResets() {
            Room room = playing(TeamId.A, 20, true);
            startTimer(room, T0);
            for (int i = 0; i < 4; i++) {
                room.strike(T1);
            }
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(round(room).timerRemainingMs()).isEqualTo(20_000);
            room.nextRound();
            assertThat(round(room).timerRunning()).isFalse();
            assertThat(round(room).timerRemainingMs()).isEqualTo(20_000);
        }

        @Test
        @DisplayName("spec 005 AC-80 Iniciar con 0 restantes corre desde la duración completa")
        void startAfterExpiry() {
            Room room = playing(TeamId.A, 20, false);
            Instant deadline = startTimer(room, T0);
            room.expireTurn(deadline, deadline);
            assertThat(round(room).timerRemainingMs()).isZero();
            room.controlTimer(TimerAction.START, T2);
            assertThat(room.turnDeadline()).contains(T2.plusSeconds(20));
        }

        @Test
        @DisplayName("spec 005 AC-91 cerrar la sala publica ROOM_CLOSED y detiene el temporizador")
        void close() {
            Room room = playing(TeamId.A, 20, true);
            startTimer(room, T0);
            long version = room.version();
            room.close();
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(room.version()).isEqualTo(version + 1);
            assertThat(room.drainEvents()).extracting(DomainEvent::type).containsExactly(GameEventType.ROOM_CLOSED);
        }
    }

    @Nested
    class Expiry {

        @Test
        @DisplayName("AC-48 AC-83 vencimiento en PLAYING con auto-X: TIMER_EXPIRED + STRIKE y se queda en 0")
        void autoStrikePlaying() {
            Room room = playing(TeamId.A, 20, true);
            Instant deadline = startTimer(room, T0);
            long version = room.version();
            Instant fired = deadline.plusMillis(3);

            assertThat(room.expireTurn(deadline, fired)).isTrue();
            assertThat(room.version()).isGreaterThan(version);
            assertThat(round(room).strikes()).isEqualTo(1);
            assertThat(round(room).strikesOf(TeamId.A)).isEqualTo(1);
            assertThat(room.turnDeadline()).as("spec 005 AC-83 no se reinicia").isEmpty();
            assertThat(round(room).timerRemainingMs()).isZero();
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team, DomainEvent::strikes)
                    .containsExactly(tuple(GameEventType.TIMER_EXPIRED, TeamId.A, null),
                            tuple(GameEventType.STRIKE, TeamId.A, 1));
        }

        @Test
        @DisplayName("AC-48 la X automática sigue las reglas: la tercera abre el robo")
        void autoStrikeOpensSteal() {
            Room room = playing(TeamId.A, 20, true);
            room.strike(T0);
            room.strike(T0);
            room.drainEvents();
            assertThat(room.expireTurn(startTimer(room, T0), T1)).isTrue();
            assertThat(room.status()).isEqualTo(RoomStatus.STEAL);
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(room.drainEvents()).extracting(DomainEvent::type).containsExactly(
                    GameEventType.TIMER_EXPIRED, GameEventType.STRIKE, GameEventType.STEAL_STARTED,
                    GameEventType.TURN_CHANGED);
        }

        @Test
        @DisplayName("AC-48 la X automática en STEAL cierra la ronda con el bote para el control")
        void autoStrikeClosesRound() {
            Room room = playing(TeamId.B, 20, true);
            room.reveal(currentAnswer(room, 1), T0);
            room.strike(T0);
            room.strike(T0);
            room.strike(T0);
            room.drainEvents();
            assertThat(room.expireTurn(startTimer(room, T0), T1)).isTrue();
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(room.team(TeamId.B).score()).isEqualTo(40);
            assertThat(round(room).strikesOf(TeamId.A)).isEqualTo(1);
            assertThat(room.turnDeadline()).isEmpty();
            List<DomainEvent> events = room.drainEvents();
            assertThat(events).extracting(DomainEvent::type).containsExactly(GameEventType.TIMER_EXPIRED,
                    GameEventType.STRIKE, GameEventType.ROUND_ENDED, GameEventType.TURN_CHANGED);
            assertThat(events.getFirst().team()).isEqualTo(TeamId.A);
        }

        @Test
        @DisplayName("spec 005 AC-83 AC-85 vencimiento en FACE_OFF con auto-X: FACE_OFF_MISS y el turno pasa al rival")
        void autoStrikeFaceOff() {
            Room room = faceOff(20, true);
            room.buzz(designated(room, TeamId.A), T0);
            room.drainEvents();
            Instant deadline = startTimer(room, T0);
            assertThat(room.expireTurn(deadline, deadline)).isTrue();
            assertThat(round(room).strikes()).isZero();
            assertThat(activeTeam(room)).isEqualTo(TeamId.B);
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.TIMER_EXPIRED, TeamId.A), tuple(GameEventType.FACE_OFF_MISS, TeamId.A),
                    tuple(GameEventType.TURN_CHANGED, TeamId.B));
        }

        @Test
        @DisplayName("spec 005 C-26 vencimiento sin equipo en turno: solo TIMER_EXPIRED sin team")
        void expiryWithoutActiveTeam() {
            Room room = faceOff(20, true);
            Instant deadline = startTimer(room, T0);
            assertThat(room.expireTurn(deadline, deadline)).isTrue();
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team)
                    .containsExactly(tuple(GameEventType.TIMER_EXPIRED, null));
        }

        @Test
        @DisplayName("AC-48 sin auto-X: solo TIMER_EXPIRED, el turno se queda y una X no reinicia el temporizador")
        void withoutAutoStrike() {
            Room room = playing(TeamId.A, 20, false);
            Instant deadline = startTimer(room, T0);
            long version = room.version();
            assertThat(room.expireTurn(deadline, T1)).isTrue();
            assertThat(room.version()).isGreaterThan(version);
            assertThat(round(room).strikes()).isZero();
            assertThat(activeTeam(room)).isEqualTo(TeamId.A);
            assertThat(room.turnDeadline()).isEmpty();
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team)
                    .containsExactly(tuple(GameEventType.TIMER_EXPIRED, TeamId.A));

            room.strike(T2);
            assertThat(room.turnDeadline()).as("spec 005 AC-79").isEmpty();
        }

        @Test
        @DisplayName("AC-49 nunca dos X por el mismo vencimiento: el segundo disparo es un no-op")
        void sameDeadlineOnlyOnce() {
            Room room = playing(TeamId.A, 20, true);
            Instant deadline = startTimer(room, T0);
            assertThat(room.expireTurn(deadline, T1)).isTrue();
            room.drainEvents();
            long version = room.version();

            assertThat(room.expireTurn(deadline, T1)).isFalse();
            assertThat(round(room).strikes()).isEqualTo(1);
            assertThat(room.version()).isEqualTo(version);
            assertThat(room.drainEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-49 spec 005 Parar o Restablecer antes del vencimiento invalida el vencimiento agendado")
        void manualActionWins() {
            Room room = playing(TeamId.A, 20, true);
            Instant scheduled = startTimer(room, T0);
            room.controlTimer(TimerAction.PAUSE, T1);
            room.drainEvents();
            long version = room.version();
            assertThat(room.expireTurn(scheduled, scheduled)).isFalse();
            assertThat(round(room).strikes()).isZero();
            assertThat(room.version()).isEqualTo(version);

            Instant second = startTimer(room, T2);
            room.controlTimer(TimerAction.RESET, T2);
            assertThat(room.expireTurn(second, second)).isFalse();
            assertThat(round(room).strikes()).isZero();
        }

        @Test
        @DisplayName("AC-49 sin vencimiento vigente (sin turno, ROUND_END o null) el vencimiento no hace nada")
        void noDeadlineNoOp() {
            Room room = faceOff(20, true);
            assertThat(room.expireTurn(T0.plusSeconds(20), T1)).isFalse();
            assertThat(room.expireTurn(null, T1)).isFalse();
            assertThat(lobby(20, true).expireTurn(T0, T1)).isFalse();
            Room ended = Fixtures.roundEnd();
            assertThat(ended.expireTurn(T0.plusSeconds(20), T1)).isFalse();
            assertThat(room.drainEvents()).isEmpty();
        }
    }

    @Nested
    class Sound {

        @Test
        @DisplayName("AC-53 silenciar y reactivar publica SOUND_TOGGLED con enabled y sube la versión")
        void toggle() {
            Room room = lobby(20, true);
            long version = room.version();
            room.setSound(false);
            assertThat(room.soundEnabled()).isFalse();
            assertThat(room.version()).isEqualTo(version + 1);
            room.setSound(true);
            assertThat(room.soundEnabled()).isTrue();
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::enabled).containsExactly(
                    tuple(GameEventType.SOUND_TOGGLED, false), tuple(GameEventType.SOUND_TOGGLED, true));
        }

        @Test
        @DisplayName("AC-53 pedir el mismo estado no cambia nada (sin evento ni versión nueva)")
        void sameValueIsNoOp() {
            Room room = lobby(20, true);
            long version = room.version();
            room.setSound(true);
            assertThat(room.version()).isEqualTo(version);
            assertThat(room.drainEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-53 el sonido se puede cambiar en cualquier estado de la partida")
        void anyState() {
            Room room = Fixtures.steal();
            room.setSound(false);
            assertThat(room.soundEnabled()).isFalse();
            assertThat(room.status()).isEqualTo(RoomStatus.STEAL);
        }
    }

    @Test
    @DisplayName("AC-46 AC-47 GameRules: rango de turnSeconds y cálculo del vencimiento")
    void gameRules() {
        assertThat(GameRules.isValidTurnSeconds(0)).isTrue();
        assertThat(GameRules.isValidTurnSeconds(9)).isFalse();
        assertThat(GameRules.turnDeadline(TeamId.A, 15, T0)).isEqualTo(T0.plusSeconds(15));
        assertThat(GameRules.turnDeadline(null, 15, T0)).isNull();
        assertThat(GameRules.turnDeadline(TeamId.B, 0, T0)).isNull();
    }
}
