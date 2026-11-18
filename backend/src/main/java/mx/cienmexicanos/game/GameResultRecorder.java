package mx.cienmexicanos.game;

import java.time.Clock;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.TeamId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Persiste el resultado de una partida al llegar a GAME_OVER. Un fallo de BD no rompe la partida. */
@Component
public class GameResultRecorder {

    private static final Logger log = LoggerFactory.getLogger(GameResultRecorder.class);

    private final GameResultRepository repository;
    private final Clock clock;

    public GameResultRecorder(GameResultRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void record(Room room) {
        try {
            var a = room.team(TeamId.A);
            var b = room.team(TeamId.B);
            repository.save(new GameResult(room.code(), a.name(), b.name(), a.score(), b.score(),
                    room.winner().orElse("TIE"), clock.instant().toString()));
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar el resultado de la sala {}", room.code(), e);
        }
    }
}
