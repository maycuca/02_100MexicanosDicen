package mx.cienmexicanos.common.api;

import java.util.List;
import mx.cienmexicanos.game.domain.RoomStatus;

/** Esquema PresenterRoomState = RoomState + presenterBoard (con respuestas ocultas). */
public record PresenterRoomState(String code, RoomStatus status, boolean presenterConnected, int turnSeconds,
                                 boolean autoStrikeOnTimeout, boolean soundEnabled, int totalRounds,
                                 int targetScore, List<TeamDto> teams, RoundState round, String winner, long version,
                                 List<PresenterBoardSlot> presenterBoard) {}
