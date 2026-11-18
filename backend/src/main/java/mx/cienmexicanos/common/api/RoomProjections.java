package mx.cienmexicanos.common.api;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mx.cienmexicanos.game.domain.DomainEvent;
import mx.cienmexicanos.game.domain.Player;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.RoomStatus;
import mx.cienmexicanos.game.domain.Round;
import mx.cienmexicanos.game.domain.Slot;
import mx.cienmexicanos.game.domain.TeamId;

/** Proyecciones del agregado {@link Room} a los esquemas del contrato (plan D4). */
public final class RoomProjections {

    private RoomProjections() {}

    public static RoomState publicState(Room room) {
        return new RoomState(room.code(), room.status(), room.presenterConnected(), room.turnSeconds(),
                room.autoStrikeOnTimeout(), room.soundEnabled(), room.totalRounds(),
                room.targetScore(), teams(room), room.currentRound().map(r -> round(r, room.status())).orElse(null),
                room.winner().orElse(null), room.version());
    }

    public static PresenterRoomState presenterState(Room room) {
        List<PresenterBoardSlot> board = room.currentRound()
                .map(r -> r.slots().stream()
                        .map(s -> new PresenterBoardSlot(s.answerId(), s.position(), s.revealed(), s.text(), s.points()))
                        .toList())
                .orElse(List.of());
        return new PresenterRoomState(room.code(), room.status(), room.presenterConnected(), room.turnSeconds(),
                room.autoStrikeOnTimeout(), room.soundEnabled(), room.totalRounds(),
                room.targetScore(), teams(room), room.currentRound().map(r -> round(r, room.status())).orElse(null),
                room.winner().orElse(null), room.version(), board);
    }

    public static PlayerDto player(Player p) {
        return new PlayerDto(p.id(), p.name(), p.team(), p.connected());
    }

    public static GameEventMessage event(DomainEvent e, long version) {
        return new GameEventMessage(e.type(), version, e.playerId(), e.team(), e.position(), e.points(), e.strikes(),
                e.enabled());
    }

    private static List<TeamDto> teams(Room room) {
        return List.of(TeamId.A, TeamId.B).stream()
                .map(room::team)
                .map(t -> new TeamDto(t.id(), t.name(), t.captain().map(Player::id).orElse(null), t.score(),
                        t.players().stream().map(RoomProjections::player).toList()))
                .toList();
    }

    private static RoundState round(Round r, RoomStatus status) {
        var fo = r.faceOff();
        TeamId potWinner = status == RoomStatus.ROUND_END ? r.winner().orElse(null) : null;
        Map<TeamId, Integer> strikesByTeam = new EnumMap<>(TeamId.class);
        strikesByTeam.put(TeamId.A, r.strikesOf(TeamId.A));
        strikesByTeam.put(TeamId.B, r.strikesOf(TeamId.B));
        return new RoundState(r.number(), r.multiplier(), r.question().text(), r.strikes(), r.pot(),
                r.activeTeam().orElse(null), strikesByTeam, r.turnDeadline().map(RoomProjections::isoUtc).orElse(null),
                r.timerRunning(), r.timerRemainingMs(),
                r.controllingTeam().orElse(null), potWinner,
                new FaceOffState(fo.playerA(), fo.playerB(), fo.buzzWinner().orElse(null), fo.firstTeam().orElse(null),
                        fo.firstAnswerPosition().orElse(null)),
                r.slots().stream().map(RoomProjections::publicSlot).toList());
    }

    /** AC-47: ISO-8601 UTC con precisión de milisegundos (p. ej. 2026-11-17T10:00:20.250Z). */
    static String isoUtc(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.MILLIS));
    }

    /** AC-25: una casilla oculta solo expone posición y estado. */
    static BoardSlot publicSlot(Slot s) {
        return s.revealed()
                ? new BoardSlot(s.position(), true, s.text(), s.points())
                : new BoardSlot(s.position(), false, null, null);
    }
}
