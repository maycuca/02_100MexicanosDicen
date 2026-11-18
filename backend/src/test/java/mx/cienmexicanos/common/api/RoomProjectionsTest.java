package mx.cienmexicanos.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import mx.cienmexicanos.game.domain.AnswerSnapshot;
import mx.cienmexicanos.game.domain.QuestionSnapshot;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.RoomStatus;
import mx.cienmexicanos.game.domain.TeamId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RoomProjectionsTest {

    static Room playingRoom() {
        Room room = new Room("KDMZ", "Rojo", "Verde", 3, 300, Instant.EPOCH);
        room.addPlayer("Ana", TeamId.A);
        room.addPlayer("Beto", TeamId.B);
        room.start(IntStream.rangeClosed(1, 3).mapToObj(q -> new QuestionSnapshot(q, "Pregunta " + q, List.of(
                new AnswerSnapshot(q * 10 + 1, "SecretoUno", 50, 1),
                new AnswerSnapshot(q * 10 + 2, "SecretoDos", 30, 2),
                new AnswerSnapshot(q * 10 + 3, "SecretoTres", 20, 3)))).toList());
        room.assignControl(TeamId.A, Instant.EPOCH);
        room.reveal(12, Instant.EPOCH);
        return room;
    }

    @Test
    @DisplayName("AC-25 la proyección pública no contiene texto ni puntos de respuestas no reveladas")
    void publicStateHidesUnrevealed() throws Exception {
        RoomState state = RoomProjections.publicState(playingRoom());

        assertThat(state.round().board()).extracting(BoardSlot::position).containsExactly(1, 2, 3);
        assertThat(state.round().board()).filteredOn(s -> !s.revealed())
                .allSatisfy(s -> {
                    assertThat(s.text()).isNull();
                    assertThat(s.points()).isNull();
                });
        assertThat(state.round().board().get(1)).isEqualTo(new BoardSlot(2, true, "SecretoDos", 30));

        String json = JsonMapper.builder().build().writeValueAsString(state);
        assertThat(json).doesNotContain("SecretoUno", "SecretoTres", "answerId", "presenterBoard")
                .contains("SecretoDos");
    }

    @Test
    @DisplayName("AC-25 el presentador sí ve todas las respuestas con su answerId")
    void presenterSeesEverything() {
        PresenterRoomState state = RoomProjections.presenterState(playingRoom());
        assertThat(state.presenterBoard()).extracting(PresenterBoardSlot::text)
                .containsExactly("SecretoUno", "SecretoDos", "SecretoTres");
        assertThat(state.presenterBoard()).extracting(PresenterBoardSlot::answerId).containsExactly(11L, 12L, 13L);
        assertThat(state.presenterBoard()).extracting(PresenterBoardSlot::revealed).containsExactly(false, true, false);
    }

    @Test
    @DisplayName("AC-24 snapshot con equipos, marcador, ronda, bote y versión")
    void snapshotFields() {
        Room room = playingRoom();
        RoomState state = RoomProjections.publicState(room);
        assertThat(state.code()).isEqualTo("KDMZ");
        assertThat(state.status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(state.teams()).extracting(TeamDto::id).containsExactly(TeamId.A, TeamId.B);
        assertThat(state.teams().getFirst().players()).extracting(PlayerDto::name).containsExactly("Ana");
        assertThat(state.round().pot()).isEqualTo(30);
        assertThat(state.round().multiplier()).isEqualTo(1);
        assertThat(state.round().controllingTeam()).isEqualTo(TeamId.A);
        assertThat(state.round().question()).isEqualTo("Pregunta 1");
        assertThat(state.round().faceOff().playerA()).isEqualTo(room.team(TeamId.A).players().getFirst().id());
        assertThat(state.version()).isEqualTo(room.version());
        assertThat(state.winner()).isNull();
        assertThat(state.presenterConnected()).isFalse();
    }

    @Test
    @DisplayName("AC-19/AC-20 potWinner solo en ROUND_END")
    void potWinner() {
        Room room = playingRoom();
        assertThat(RoomProjections.publicState(room).round().potWinner()).isNull();
        room.strike(Instant.EPOCH);
        room.strike(Instant.EPOCH);
        room.strike(Instant.EPOCH);
        room.reveal(11, Instant.EPOCH); // el rival roba
        RoomState end = RoomProjections.publicState(room);
        assertThat(end.status()).isEqualTo(RoomStatus.ROUND_END);
        assertThat(end.round().potWinner()).isEqualTo(TeamId.B);
        assertThat(end.round().board()).allMatch(BoardSlot::revealed);
        room.nextRound();
        assertThat(RoomProjections.publicState(room).round().potWinner()).isNull();
    }

    @Test
    @DisplayName("en LOBBY no hay ronda ni tablero del presentador")
    void lobby() {
        Room room = new Room("KDMZ", "Rojo", "Verde", 3, 300, Instant.EPOCH);
        assertThat(RoomProjections.publicState(room).round()).isNull();
        assertThat(RoomProjections.presenterState(room).presenterBoard()).isEmpty();
    }

    @Test
    @DisplayName("spec 002 AC-35 AC-41 AC-46 AC-47 AC-53 campos nuevos en ambas proyecciones")
    void turnFields() {
        Room room = playingRoom(); // control A, 20 s por defecto, assignControl en EPOCH
        room.strike(Instant.EPOCH.plusMillis(1500));
        room.controlTimer(mx.cienmexicanos.game.domain.TimerAction.START, Instant.EPOCH.plusMillis(1500)); // spec 005
        for (var round : List.of(RoomProjections.publicState(room).round(),
                RoomProjections.presenterState(room).round())) {
            assertThat(round.activeTeam()).isEqualTo(TeamId.A);
            assertThat(round.strikesByTeam()).containsEntry(TeamId.A, 1).containsEntry(TeamId.B, 0);
            assertThat(round.turnDeadline()).isEqualTo("1970-01-01T00:00:21.500Z");
            assertThat(round.timerRunning()).isTrue();
            assertThat(round.timerRemainingMs()).isEqualTo(20_000);
        }
        room.setSound(false);
        RoomState pub = RoomProjections.publicState(room);
        PresenterRoomState pres = RoomProjections.presenterState(room);
        assertThat(pub.turnSeconds()).isEqualTo(20);
        assertThat(pub.autoStrikeOnTimeout()).isTrue();
        assertThat(pub.soundEnabled()).isFalse();
        assertThat(pres.turnSeconds()).isEqualTo(20);
        assertThat(pres.autoStrikeOnTimeout()).isTrue();
        assertThat(pres.soundEnabled()).isFalse();
    }

    @Test
    @DisplayName("contrato v0.4.0 activeTeam, strikesByTeam y turnDeadline siempre presentes en el JSON (null si no aplican)")
    void requiredNullsAreSerialized() throws Exception {
        Room room = new Room("KDMZ", "Rojo", "Verde", 3, 300, Instant.EPOCH);
        room.addPlayer("Ana", TeamId.A);
        room.addPlayer("Beto", TeamId.B);
        room.start(IntStream.rangeClosed(1, 3).mapToObj(q -> new QuestionSnapshot(q, "P" + q, List.of(
                new AnswerSnapshot(q * 10 + 1, "Uno", 50, 1)))).toList());
        JsonMapper mapper = JsonMapper.builder().build();
        for (Object state : List.of(RoomProjections.publicState(room), RoomProjections.presenterState(room))) {
            var round = mapper.readTree(mapper.writeValueAsString(state)).get("round");
            assertThat(round.has("activeTeam")).isTrue();
            assertThat(round.get("activeTeam").isNull()).isTrue();
            assertThat(round.has("turnDeadline")).isTrue();
            assertThat(round.get("turnDeadline").isNull()).isTrue();
            assertThat(round.get("strikesByTeam").get("A").asInt()).isZero();
            assertThat(round.get("strikesByTeam").get("B").asInt()).isZero();
        }
    }
}
