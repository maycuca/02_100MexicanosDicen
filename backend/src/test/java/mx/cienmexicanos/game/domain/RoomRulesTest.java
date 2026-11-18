package mx.cienmexicanos.game.domain;

import static mx.cienmexicanos.game.domain.Fixtures.currentAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RoomRulesTest {

    static void assertError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Nested
    class CreateRoom {

        @Test
        @DisplayName("AC-02 nombres de equipo, rondas y meta configurables")
        void settings() {
            Room room = new Room("ABCD", " Leones ", "Tigres", 8, 1000, Fixtures.T0);
            assertThat(room.team(TeamId.A).name()).isEqualTo("Leones");
            assertThat(room.team(TeamId.B).name()).isEqualTo("Tigres");
            assertThat(room.totalRounds()).isEqualTo(8);
            assertThat(room.targetScore()).isEqualTo(1000);
            assertThat(room.status()).isEqualTo(RoomStatus.LOBBY);
            assertThat(room.lastActivity()).isEqualTo(Fixtures.T0);
            assertThat(room.hostToken()).isNotEqualTo(room.claimToken());
        }

        @Test
        @DisplayName("AC-02 rondas 3–8, meta 50–1000 y nombres de 1–24 caracteres")
        void invalidSettings() {
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", "A", "B", 2, 300, Fixtures.T0));
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", "A", "B", 9, 300, Fixtures.T0));
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", "A", "B", 5, 49, Fixtures.T0));
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", "A", "B", 5, 1001, Fixtures.T0));
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", " ", "B", 5, 300, Fixtures.T0));
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", "A", null, 5, 300, Fixtures.T0));
            assertError(ErrorCode.VALIDATION_ERROR, () -> new Room("ABCD", "A", "x".repeat(25), 5, 300, Fixtures.T0));
        }

        @Test
        @DisplayName("AC-28 touch registra la última actividad")
        void touch() {
            Room room = Fixtures.room(5, 300);
            Instant later = Fixtures.T0.plusSeconds(60);
            room.touch(later);
            assertThat(room.lastActivity()).isEqualTo(later);
        }
    }

    @Nested
    class Join {

        @Test
        @DisplayName("AC-05 el jugador se une con nombre (recortado) y equipo")
        void join() {
            Room room = Fixtures.room(5, 300);
            Player p = room.addPlayer("  Ana  ", TeamId.B);
            assertThat(p.name()).isEqualTo("Ana");
            assertThat(p.team()).isEqualTo(TeamId.B);
            assertThat(p.connected()).isTrue();
            assertThat(room.team(TeamId.B).players()).containsExactly(p);
            assertThat(room.playerByToken(p.token().toString())).contains(p);
            assertThat(room.player(p.id())).contains(p);
            assertThat(room.drainEvents()).singleElement().satisfies(e -> {
                assertThat(e.type()).isEqualTo(GameEventType.PLAYER_JOINED);
                assertThat(e.playerId()).isEqualTo(p.id());
                assertThat(e.team()).isEqualTo(TeamId.B);
            });
            assertThat(room.drainEvents()).isEmpty();
        }

        @Test
        @DisplayName("AC-05 nombre de 2 a 20 caracteres y equipo obligatorio")
        void invalidName() {
            Room room = Fixtures.room(5, 300);
            assertError(ErrorCode.VALIDATION_ERROR, () -> room.addPlayer("A", TeamId.A));
            assertError(ErrorCode.VALIDATION_ERROR, () -> room.addPlayer("   A   ", TeamId.A));
            assertError(ErrorCode.VALIDATION_ERROR, () -> room.addPlayer("x".repeat(21), TeamId.A));
            assertError(ErrorCode.VALIDATION_ERROR, () -> room.addPlayer(null, TeamId.A));
            assertError(ErrorCode.VALIDATION_ERROR, () -> room.addPlayer("Ana", null));
            assertThat(room.addPlayer("x".repeat(20), TeamId.A).name()).hasSize(20);
        }

        @Test
        @DisplayName("AC-06 nombre repetido sin distinguir mayúsculas → NAME_TAKEN")
        void duplicateName() {
            Room room = Fixtures.room(5, 300);
            room.addPlayer("Ana", TeamId.A);
            assertError(ErrorCode.NAME_TAKEN, () -> room.addPlayer("ANA", TeamId.B));
            assertError(ErrorCode.NAME_TAKEN, () -> room.addPlayer(" ana ", TeamId.A));
        }

        @Test
        @DisplayName("AC-07 solo se puede unir en LOBBY → ROOM_NOT_IN_LOBBY")
        void notInLobby() {
            Room room = Fixtures.faceOff();
            assertError(ErrorCode.ROOM_NOT_IN_LOBBY, () -> room.addPlayer("Nuevo", TeamId.A));
        }

        @Test
        @DisplayName("token desconocido o null no identifica a nadie")
        void unknownToken() {
            Room room = Fixtures.lobby(5, 300);
            assertThat(room.playerByToken(null)).isEmpty();
            assertThat(room.playerByToken(UUID.randomUUID().toString())).isEmpty();
        }

        @Test
        @DisplayName("AC-09 conexión del jugador: PLAYER_LEFT al desconectarse, sin cambios si no varía")
        void connection() {
            Room room = Fixtures.lobby(5, 300);
            Player ana = room.team(TeamId.A).players().getFirst();
            long v = room.version();
            room.setPlayerConnected(ana.id(), true);
            assertThat(room.version()).isEqualTo(v);
            room.setPlayerConnected(ana.id(), false);
            assertThat(ana.connected()).isFalse();
            assertThat(room.drainEvents()).extracting(DomainEvent::type).containsExactly(GameEventType.PLAYER_LEFT);
            room.setPlayerConnected(ana.id(), true);
            assertThat(ana.connected()).isTrue();
            assertThat(room.drainEvents()).isEmpty();
            assertThat(room.version()).isEqualTo(v + 2);
            room.setPlayerConnected(UUID.randomUUID(), false);
            assertThat(room.version()).isEqualTo(v + 2);
        }
    }

    @Nested
    class Presenter {

        @Test
        @DisplayName("AC-30 el claim token se canjea una sola vez por un presenterToken; 2º intento → CLAIM_USED")
        void claimOnce() {
            Room room = Fixtures.room(5, 300);
            String claim = room.claimToken().toString();
            assertThat(room.presenterConnected()).isFalse();

            UUID token = room.claimPresenter(claim);

            assertThat(room.isPresenterToken(token.toString())).isTrue();
            assertThat(room.presenterConnected()).isTrue();
            assertThat(room.drainEvents()).extracting(DomainEvent::type).containsExactly(GameEventType.PRESENTER_CLAIMED);
            assertError(ErrorCode.CLAIM_USED, () -> room.claimPresenter(claim));
        }

        @Test
        @DisplayName("AC-30 claim token incorrecto → CLAIM_INVALID")
        void claimInvalid() {
            Room room = Fixtures.room(5, 300);
            assertError(ErrorCode.CLAIM_INVALID, () -> room.claimPresenter("nope"));
            assertError(ErrorCode.CLAIM_INVALID, () -> room.claimPresenter(null));
            assertError(ErrorCode.CLAIM_INVALID, () -> room.claimPresenter(room.hostToken().toString()));
        }

        @Test
        @DisplayName("AC-33 regenerar invalida el QR anterior, reabre el canje y conserva el token de la laptop")
        void regenerate() {
            Room room = Fixtures.room(5, 300);
            String first = room.claimToken().toString();
            UUID phone = room.claimPresenter(first);

            UUID second = room.regenerateClaim();

            assertThat(second.toString()).isNotEqualTo(first);
            assertThat(room.claimToken()).isEqualTo(second);
            assertThat(room.presenterConnected()).as("AC-32").isFalse();
            assertThat(room.isPresenterToken(room.hostToken().toString())).isTrue();
            assertThat(room.isPresenterToken(phone.toString())).as("el celular anterior pierde acceso").isFalse();
            assertError(ErrorCode.CLAIM_INVALID, () -> room.claimPresenter(first));
            UUID newPhone = room.claimPresenter(second.toString());
            assertThat(room.isPresenterToken(newPhone.toString())).isTrue();
            assertThat(room.presenterConnected()).isTrue();
        }

        @Test
        @DisplayName("AC-26 solo los tokens de presentador de la sala son válidos")
        void presenterToken() {
            Room room = Fixtures.lobby(5, 300);
            assertThat(room.isPresenterToken(room.hostToken().toString())).isTrue();
            assertThat(room.isPresenterToken(null)).isFalse();
            assertThat(room.isPresenterToken(room.claimToken().toString())).isFalse();
            String playerToken = room.allPlayers().getFirst().token().toString();
            assertThat(room.isPresenterToken(playerToken)).isFalse();
        }
    }

    @Nested
    class Start {

        @Test
        @DisplayName("AC-10 no se puede iniciar si un equipo está vacío → TEAM_EMPTY")
        void teamEmpty() {
            Room room = Fixtures.room(5, 300);
            room.addPlayer("Ana", TeamId.A);
            assertError(ErrorCode.TEAM_EMPTY, () -> room.start(Fixtures.questions(5)));
            assertThat(room.status()).isEqualTo(RoomStatus.LOBBY);
        }

        @Test
        @DisplayName("AC-11 se necesitan tantas preguntas distintas como rondas")
        void notEnoughQuestions() {
            Room room = Fixtures.lobby(5, 300);
            assertError(ErrorCode.NOT_ENOUGH_QUESTIONS, () -> room.start(Fixtures.questions(4)));
            assertError(ErrorCode.NOT_ENOUGH_QUESTIONS, () -> room.start(null));
            var q = Fixtures.question(1);
            assertError(ErrorCode.NOT_ENOUGH_QUESTIONS, () -> room.start(List.of(q, q, q, q, q)));
        }

        @Test
        @DisplayName("AC-11 cada ronda usa una pregunta distinta, en el orden elegido")
        void questionsPerRound() {
            Room room = Fixtures.lobby(3, 1000);
            room.start(Fixtures.questions(3));
            for (int r = 1; r <= 3; r++) {
                assertThat(room.currentRound().orElseThrow().question().id()).isEqualTo(r);
                room.assignControl(TeamId.A, Fixtures.T0);
                for (int i = 0; i < 4; i++) {
                    room.strike(Fixtures.T0);
                }
                room.nextRound();
            }
        }

        @Test
        @DisplayName("AC-12 un designado por equipo, rotando en orden de llegada")
        void faceOffRotation() {
            Room room = Fixtures.lobby(5, 1000);
            List<Player> a = room.team(TeamId.A).players();
            List<Player> b = room.team(TeamId.B).players();
            room.start(Fixtures.questions(5));
            UUID[][] expected = {
                    {a.get(0).id(), b.get(0).id()},
                    {a.get(1).id(), b.get(1).id()},
                    {a.get(0).id(), b.get(0).id()}};
            for (UUID[] pair : expected) {
                FaceOff fo = room.currentRound().orElseThrow().faceOff();
                assertThat(fo.playerA()).isEqualTo(pair[0]);
                assertThat(fo.playerB()).isEqualTo(pair[1]);
                room.assignControl(TeamId.A, Fixtures.T0);
                for (int i = 0; i < 4; i++) {
                    room.strike(Fixtures.T0);
                }
                room.nextRound();
            }
        }
    }

    @Nested
    class Buzz {

        @Test
        @DisplayName("AC-14 solo cuenta el primer buzz; los posteriores se ignoran")
        void firstBuzzWins() {
            Room room = Fixtures.faceOff();
            FaceOff fo = room.currentRound().orElseThrow().faceOff();
            room.drainEvents();

            assertThat(room.buzz(fo.playerB(), Fixtures.T0)).isTrue();
            long version = room.version();
            assertThat(room.buzz(fo.playerA(), Fixtures.T0)).isFalse();
            assertThat(room.buzz(fo.playerB(), Fixtures.T0)).isFalse();

            assertThat(fo.buzzWinner()).contains(fo.playerB());
            assertThat(room.version()).isEqualTo(version);
            var events = room.drainEvents();
            assertThat(events).extracting(DomainEvent::type)
                    .containsExactly(GameEventType.BUZZ_WON, GameEventType.TURN_CHANGED); // spec 002 AC-36
            assertThat(events.getFirst()).satisfies(e -> {
                assertThat(e.playerId()).isEqualTo(fo.playerB());
                assertThat(e.team()).isEqualTo(TeamId.B);
            });
        }

        @Test
        @DisplayName("AC-27 solo el jugador designado puede presionar → NOT_YOUR_TURN")
        void onlyDesignated() {
            Room room = Fixtures.faceOff();
            FaceOff fo = room.currentRound().orElseThrow().faceOff();
            Player notDesignated = room.allPlayers().stream()
                    .filter(p -> !p.id().equals(fo.playerA()) && !p.id().equals(fo.playerB()))
                    .findFirst().orElseThrow();
            assertError(ErrorCode.NOT_YOUR_TURN, () -> room.buzz(notDesignated.id(), Fixtures.T0));
            assertError(ErrorCode.NOT_YOUR_TURN, () -> room.buzz(UUID.randomUUID(), Fixtures.T0));
            assertThat(fo.buzzWinner()).isEmpty();
        }

        @Test
        @DisplayName("AC-27 fuera de FACE_OFF el buzz → INVALID_STATE")
        void onlyInFaceOff() {
            Room room = Fixtures.playing(TeamId.A);
            UUID a = room.currentRound().orElseThrow().faceOff().playerA();
            assertError(ErrorCode.INVALID_STATE, () -> room.buzz(a, Fixtures.T0));
        }
    }

    @Nested
    class Scoring {

        @Test
        @DisplayName("AC-15 en el cara a cara se puede revelar y marcar X; el bote acumula lo revelado")
        void faceOffRevealAndStrike() {
            Room room = Fixtures.faceOff();
            FaceOff fo = room.currentRound().orElseThrow().faceOff();
            room.buzz(fo.playerA(), Fixtures.T0);
            room.drainEvents();

            room.reveal(currentAnswer(room, 2), Fixtures.T0); // 30 → turno a B (spec 006 AC-93)
            room.strike(Fixtures.T0); // B falla → termina el cara a cara, control A (AC-97)

            Round round = room.currentRound().orElseThrow();
            assertThat(round.pot()).isEqualTo(30);
            assertThat(round.strikes()).isZero();
            assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
            assertThat(room.drainEvents()).extracting(DomainEvent::type, DomainEvent::team).containsExactly(
                    tuple(GameEventType.ANSWER_REVEALED, TeamId.A), tuple(GameEventType.TURN_CHANGED, TeamId.B),
                    tuple(GameEventType.FACE_OFF_MISS, TeamId.B), tuple(GameEventType.CONTROL_ASSIGNED, TeamId.A),
                    tuple(GameEventType.TURN_CHANGED, TeamId.A));
        }

        @Test
        @DisplayName("AC-17 cada respuesta revelada suma al bote, no al marcador")
        void potNotScore() {
            Room room = Fixtures.playing(TeamId.A);
            room.drainEvents();
            room.reveal(currentAnswer(room, 1), Fixtures.T0);
            room.reveal(currentAnswer(room, 3), Fixtures.T0);
            assertThat(room.currentRound().orElseThrow().pot()).isEqualTo(60);
            assertThat(room.team(TeamId.A).score()).isZero();
            assertThat(room.drainEvents()).first().satisfies(e -> {
                assertThat(e.type()).isEqualTo(GameEventType.ANSWER_REVEALED);
                assertThat(e.position()).isEqualTo(1);
                assertThat(e.points()).isEqualTo(40);
                assertThat(e.team()).isEqualTo(TeamId.A);
            });
        }

        @Test
        @DisplayName("AC-16 respuesta ajena a la ronda → ANSWER_NOT_FOUND; ya revelada → INVALID_STATE")
        void revealErrors() {
            Room room = Fixtures.playing(TeamId.A);
            assertError(ErrorCode.ANSWER_NOT_FOUND, () -> room.reveal(999_999, Fixtures.T0));
            room.reveal(currentAnswer(room, 1), Fixtures.T0);
            assertError(ErrorCode.INVALID_STATE, () -> room.reveal(currentAnswer(room, 1), Fixtures.T0));
        }

        @Test
        @DisplayName("AC-18 los strikes del equipo con control llegan a 3 y empieza el robo del rival")
        void strikesEvents() {
            Room room = Fixtures.playing(TeamId.B);
            room.drainEvents();
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            var events = room.drainEvents();
            assertThat(events).extracting(DomainEvent::type).containsExactly(
                    GameEventType.STRIKE, GameEventType.STRIKE, GameEventType.STRIKE, GameEventType.STEAL_STARTED,
                    GameEventType.TURN_CHANGED);
            assertThat(events).extracting(DomainEvent::strikes).containsExactly(1, 2, 3, 3, null);
            assertThat(events.get(3).team()).isEqualTo(TeamId.A);
            assertThat(events.getLast().team()).as("AC-35 en STEAL el turno es del rival").isEqualTo(TeamId.A);
        }

        @Test
        @DisplayName("AC-19 en el robo el acierto del rival no suma al bote (data-model) y se lo lleva")
        void stealRevealDoesNotAddToPot() {
            Room room = Fixtures.playing(TeamId.A);
            room.reveal(currentAnswer(room, 4), Fixtures.T0); // 10
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.drainEvents();
            room.reveal(currentAnswer(room, 1), Fixtures.T0);
            assertThat(room.currentRound().orElseThrow().pot()).isEqualTo(10);
            assertThat(room.team(TeamId.B).score()).isEqualTo(10);
            assertThat(room.drainEvents()).extracting(DomainEvent::type)
                    .containsExactly(GameEventType.ANSWER_REVEALED, GameEventType.ROUND_ENDED, GameEventType.TURN_CHANGED);
        }

        @Test
        @DisplayName("AC-20 al cerrar la ronda se revelan las restantes y se suma bote × multiplicador")
        void roundEndRevealsAll() {
            Room room = Fixtures.lobby(3, 1000);
            room.start(Fixtures.questions(3));
            room.assignControl(TeamId.A, Fixtures.T0);
            for (int i = 0; i < 4; i++) {
                room.strike(Fixtures.T0);
            }
            room.nextRound(); // ronda 2 de 3 → ×2
            room.buzz(room.currentRound().orElseThrow().faceOff().playerA(), Fixtures.T0);
            room.reveal(currentAnswer(room, 1), Fixtures.T0); // 40 en FACE_OFF: la #1 da el control a A (spec 006 AC-92)
            room.reveal(currentAnswer(room, 2), Fixtures.T0); // 30
            room.drainEvents();
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0);
            room.strike(Fixtures.T0); // robo fallido → bote a A

            Round round = room.currentRound().orElseThrow();
            assertThat(round.slots()).allMatch(Slot::revealed);
            assertThat(round.multiplier()).isEqualTo(2);
            assertThat(room.team(TeamId.A).score()).isEqualTo(140);
            assertThat(room.drainEvents()).filteredOn(e -> e.type() == GameEventType.ROUND_ENDED).singleElement()
                    .satisfies(e -> {
                assertThat(e.team()).isEqualTo(TeamId.A);
                assertThat(e.points()).isEqualTo(140);
            });
        }

        @Test
        @DisplayName("AC-21 la última ronda vale triple")
        void lastRoundTriple() {
            Room room = Fixtures.lobby(3, 1000);
            room.start(Fixtures.questions(3));
            for (int r = 1; r <= 2; r++) {
                room.assignControl(TeamId.A, Fixtures.T0);
                for (int i = 0; i < 4; i++) {
                    room.strike(Fixtures.T0);
                }
                room.nextRound();
            }
            assertThat(room.currentRound().orElseThrow().multiplier()).isEqualTo(3);
            room.assignControl(TeamId.B, Fixtures.T0);
            for (int pos = 1; pos <= 4; pos++) {
                room.reveal(currentAnswer(room, pos), Fixtures.T0);
            }
            assertThat(room.team(TeamId.B).score()).isEqualTo(300);
        }

        @Test
        @DisplayName("assignControl con todo revelado cierra la ronda; equipo null → VALIDATION_ERROR")
        void assignControlEdgeCases() {
            Room room = Fixtures.faceOff();
            assertError(ErrorCode.VALIDATION_ERROR, () -> room.assignControl(null, Fixtures.T0));
            room.buzz(room.currentRound().orElseThrow().faceOff().playerA(), Fixtures.T0);
            room.reveal(currentAnswer(room, 2), Fixtures.T0); // → B
            room.assignControl(TeamId.A, Fixtures.T0); // spec 006 C-28: corrección manual
            for (int pos : new int[] {1, 3, 4}) {
                room.reveal(currentAnswer(room, pos), Fixtures.T0);
            }
            assertThat(room.status()).isEqualTo(RoomStatus.ROUND_END);
            assertThat(room.team(TeamId.A).score()).isEqualTo(100);
        }
    }

    @Nested
    class GameOver {

        @Test
        @DisplayName("AC-22 la partida termina al alcanzar la meta al cierre de una ronda")
        void targetReached() {
            Room room = Fixtures.lobby(5, 100);
            room.start(Fixtures.questions(5));
            room.assignControl(TeamId.A, Fixtures.T0);
            for (int pos = 1; pos <= 4; pos++) {
                room.reveal(currentAnswer(room, pos), Fixtures.T0);
            }
            assertThat(room.status()).as("todavía se muestra el fin de ronda").isEqualTo(RoomStatus.ROUND_END);
            room.drainEvents();
            room.nextRound();
            assertThat(room.status()).isEqualTo(RoomStatus.GAME_OVER);
            assertThat(room.winner()).contains("A");
            assertThat(room.drainEvents()).singleElement().satisfies(e -> {
                assertThat(e.type()).isEqualTo(GameEventType.GAME_OVER);
                assertThat(e.team()).isEqualTo(TeamId.A);
            });
        }

        @Test
        @DisplayName("AC-22 empate al terminar todas las rondas → TIE sin equipo en el evento")
        void tie() {
            Room room = Fixtures.lobby(3, 1000);
            room.start(Fixtures.questions(3));
            for (int r = 1; r <= 2; r++) {
                room.assignControl(TeamId.A, Fixtures.T0);
                for (int i = 0; i < 4; i++) {
                    room.strike(Fixtures.T0);
                }
                room.nextRound();
            }
            room.assignControl(TeamId.A, Fixtures.T0);
            for (int i = 0; i < 4; i++) {
                room.strike(Fixtures.T0);
            }
            room.drainEvents();
            room.nextRound();
            assertThat(room.winner()).contains("TIE");
            assertThat(room.drainEvents()).singleElement().satisfies(e -> assertThat(e.team()).isNull());
        }

        @Test
        @DisplayName("versión creciente con cada comando")
        void versionIncreases() {
            Room room = Fixtures.room(5, 300);
            long v0 = room.version();
            room.addPlayer("Ana", TeamId.A);
            room.addPlayer("Beto", TeamId.B);
            room.start(Fixtures.questions(5));
            assertThat(room.version()).isEqualTo(v0 + 3);
        }
    }
}
