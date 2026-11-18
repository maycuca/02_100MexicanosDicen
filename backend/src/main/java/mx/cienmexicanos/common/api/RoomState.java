package mx.cienmexicanos.common.api;

import java.util.List;
import mx.cienmexicanos.game.domain.RoomStatus;

/** Esquema RoomState: proyección pública, nunca contiene respuestas ocultas (plan D4, AC-25). */
public record RoomState(String code, RoomStatus status, boolean presenterConnected, int turnSeconds,
                        boolean autoStrikeOnTimeout, boolean soundEnabled, int totalRounds, int targetScore,
                        List<TeamDto> teams, RoundState round, String winner, long version) {}
