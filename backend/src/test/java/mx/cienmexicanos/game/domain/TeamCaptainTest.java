package mx.cienmexicanos.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 003, US-17: capitán y nombre de equipo (T801, T802). */
class TeamCaptainTest {

    static void assertError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    static String token(Room room, String playerName) {
        return room.allPlayers().stream().filter(p -> p.name().equals(playerName)).findFirst().orElseThrow()
                .token().toString();
    }

    @Test
    @DisplayName("AC-62 sin jugadores no hay capitán; el primero que se une a cada equipo es su capitán")
    void captainIsFirstPlayer() {
        Room room = Fixtures.room(5, 300);
        assertThat(room.team(TeamId.A).captain()).isEmpty();
        assertThat(room.team(TeamId.B).captain()).isEmpty();

        Player ana = room.addPlayer("Ana", TeamId.A);
        assertThat(room.team(TeamId.A).captain()).contains(ana);
        assertThat(room.team(TeamId.B).captain()).isEmpty();

        Player beto = room.addPlayer("Beto", TeamId.B);
        room.addPlayer("Carla", TeamId.A);
        assertThat(room.team(TeamId.A).captain()).contains(ana);
        assertThat(room.team(TeamId.B).captain()).contains(beto);
    }

    @Test
    @DisplayName("AC-62 el capitán no cambia si se desconecta")
    void captainSurvivesDisconnect() {
        Room room = Fixtures.lobby(5, 300);
        Player ana = room.team(TeamId.A).captain().orElseThrow();
        room.setPlayerConnected(ana.id(), false);
        assertThat(room.team(TeamId.A).captain()).contains(ana);
    }

    @Test
    @DisplayName("AC-63 AC-65 el capitán renombra su equipo (recortado), publica TEAM_RENAMED y sube la versión")
    void captainRenames() {
        Room room = Fixtures.lobby(5, 300);
        long before = room.version();
        room.renameTeam(token(room, "Ana"), TeamId.A, "  Los Leones  ");
        assertThat(room.team(TeamId.A).name()).isEqualTo("Los Leones");
        assertThat(room.version()).isEqualTo(before + 1);
        assertThat(room.drainEvents()).containsExactly(DomainEvent.forTeam(GameEventType.TEAM_RENAMED, TeamId.A));
    }

    @Test
    @DisplayName("AC-63 límites de longitud 2 y 20 tras recortar; mayúsculas del propio nombre sí cambian")
    void lengthBoundsAndOwnCase() {
        Room room = Fixtures.lobby(5, 300);
        String beto = token(room, "Beto");
        room.renameTeam(beto, TeamId.B, "Ok");
        assertThat(room.team(TeamId.B).name()).isEqualTo("Ok");
        room.renameTeam(beto, TeamId.B, "x".repeat(20));
        assertThat(room.team(TeamId.B).name()).hasSize(20);
        room.renameTeam(beto, TeamId.B, "Tigres");
        room.drainEvents();
        room.renameTeam(beto, TeamId.B, "TIGRES");
        assertThat(room.team(TeamId.B).name()).isEqualTo("TIGRES");
        assertThat(room.drainEvents()).hasSize(1);
    }

    @Test
    @DisplayName("AC-65 renombrar al mismo nombre exacto no publica evento ni sube la versión")
    void sameNameIsNoOp() {
        Room room = Fixtures.lobby(5, 300);
        long before = room.version();
        room.renameTeam(token(room, "Ana"), TeamId.A, " Equipo Rojo ");
        assertThat(room.version()).isEqualTo(before);
        assertThat(room.drainEvents()).isEmpty();
    }

    @Test
    @DisplayName("AC-64 token inválido, de otro equipo o de un jugador que no es capitán → NOT_CAPTAIN")
    void notCaptain() {
        Room room = Fixtures.lobby(5, 300);
        assertError(ErrorCode.NOT_CAPTAIN, () -> room.renameTeam("nope", TeamId.A, "Leones"));
        assertError(ErrorCode.NOT_CAPTAIN, () -> room.renameTeam(null, TeamId.A, "Leones"));
        assertError(ErrorCode.NOT_CAPTAIN, () -> room.renameTeam(token(room, "Carla"), TeamId.A, "Leones"));
        assertError(ErrorCode.NOT_CAPTAIN, () -> room.renameTeam(token(room, "Beto"), TeamId.A, "Leones"));
        assertError(ErrorCode.NOT_CAPTAIN, () -> room.renameTeam(token(room, "Ana"), TeamId.B, "Leones"));
        Room other = Fixtures.lobby(5, 300);
        assertError(ErrorCode.NOT_CAPTAIN, () -> other.renameTeam(token(room, "Ana"), TeamId.A, "Leones"));
        assertThat(room.team(TeamId.A).name()).isEqualTo("Equipo Rojo");
    }

    @Test
    @DisplayName("AC-64 longitud inválida tras recortar → VALIDATION_ERROR")
    void invalidLength() {
        Room room = Fixtures.lobby(5, 300);
        String ana = token(room, "Ana");
        assertError(ErrorCode.VALIDATION_ERROR, () -> room.renameTeam(ana, TeamId.A, "  L  "));
        assertError(ErrorCode.VALIDATION_ERROR, () -> room.renameTeam(ana, TeamId.A, "x".repeat(21)));
        assertError(ErrorCode.VALIDATION_ERROR, () -> room.renameTeam(ana, TeamId.A, null));
    }

    @Test
    @DisplayName("AC-64 nombre igual al del otro equipo sin distinguir mayúsculas → TEAM_NAME_TAKEN")
    void nameTaken() {
        Room room = Fixtures.lobby(5, 300);
        assertError(ErrorCode.TEAM_NAME_TAKEN, () -> room.renameTeam(token(room, "Ana"), TeamId.A, " equipo VERDE "));
        assertThat(room.drainEvents()).isEmpty();
    }

    @Test
    @DisplayName("AC-64 fuera de LOBBY → ROOM_NOT_IN_LOBBY")
    void notInLobby() {
        Room room = Fixtures.faceOff();
        assertError(ErrorCode.ROOM_NOT_IN_LOBBY, () -> room.renameTeam(token(room, "Ana"), TeamId.A, "Leones"));
        Room over = Fixtures.gameOver();
        assertError(ErrorCode.ROOM_NOT_IN_LOBBY, () -> over.renameTeam(token(over, "Ana"), TeamId.A, "Leones"));
    }

    @Test
    @DisplayName("AC-67 la revancha conserva nombres y capitanes; en el nuevo LOBBY el capitán puede renombrar")
    void rematchKeepsNamesAndCaptains() {
        Room room = Fixtures.lobby(3, 1000);
        room.renameTeam(token(room, "Ana"), TeamId.A, "Leones");
        room.renameTeam(token(room, "Beto"), TeamId.B, "Tigres");
        Player captainA = room.team(TeamId.A).captain().orElseThrow();
        Player captainB = room.team(TeamId.B).captain().orElseThrow();
        room.start(Fixtures.questions(3));
        for (int r = 1; r <= 3; r++) {
            room.assignControl(TeamId.A, Fixtures.T0);
            for (int x = 0; x < 4; x++) {
                room.strike(Fixtures.T0);
            }
            room.nextRound();
        }
        assertThat(room.status()).isEqualTo(RoomStatus.GAME_OVER);

        room.rematch();
        assertThat(room.team(TeamId.A).name()).isEqualTo("Leones");
        assertThat(room.team(TeamId.B).name()).isEqualTo("Tigres");
        assertThat(room.team(TeamId.A).captain()).contains(captainA);
        assertThat(room.team(TeamId.B).captain()).contains(captainB);
        room.renameTeam(token(room, "Ana"), TeamId.A, "Leonas");
        assertThat(room.team(TeamId.A).name()).isEqualTo("Leonas");
    }
}
