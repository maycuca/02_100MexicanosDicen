package mx.cienmexicanos.game;

import java.time.Clock;
import java.util.UUID;
import mx.cienmexicanos.common.api.PresenterRoomState;
import mx.cienmexicanos.common.api.RoomProjections;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.TeamId;
import mx.cienmexicanos.game.domain.TimerAction;
import mx.cienmexicanos.question.QuestionService;
import mx.cienmexicanos.room.RoomCommandRunner;
import org.springframework.stereotype.Service;

/** Orquesta los comandos de juego; las reglas viven en {@code game.domain} (T204, T302). */
@Service
public class GameService {

    private final RoomCommandRunner runner;
    private final QuestionService questions;
    private final Clock clock;

    public GameService(RoomCommandRunner runner, QuestionService questions, Clock clock) {
        this.runner = runner;
        this.questions = questions;
        this.clock = clock;
    }

    /** AC-10, AC-11. */
    public PresenterRoomState start(String code) {
        return runner.execute(code, room -> {
            room.start(questions.pickRandom(room.totalRounds()));
            return RoomProjections.presenterState(room);
        });
    }

    public PresenterRoomState reveal(String code, long answerId) {
        return runner.execute(code, room -> {
            room.reveal(answerId, clock.instant());
            return RoomProjections.presenterState(room);
        });
    }

    public PresenterRoomState strike(String code) {
        return runner.execute(code, room -> {
            room.strike(clock.instant());
            return RoomProjections.presenterState(room);
        });
    }

    /** Spec 008, AC-122: cambiar la pregunta de la ronda. */
    public PresenterRoomState changeQuestion(String code) {
        return runner.execute(code, room -> {
            room.changeQuestion(room.status() == mx.cienmexicanos.game.domain.RoomStatus.FACE_OFF
                    ? questions.pickOneExcluding(room.questionIdsToAvoid()) : null);
            return RoomProjections.presenterState(room);
        });
    }

    /** Spec 005, AC-80, AC-81: iniciar, parar o restablecer el temporizador. */
    public PresenterRoomState controlTimer(String code, TimerAction action) {
        return runner.execute(code, room -> {
            room.controlTimer(action, clock.instant());
            return RoomProjections.presenterState(room);
        });
    }

    public PresenterRoomState assignControl(String code, TeamId team) {
        return runner.execute(code, room -> {
            room.assignControl(team, clock.instant());
            return RoomProjections.presenterState(room);
        });
    }

    public PresenterRoomState nextRound(String code) {
        return runner.execute(code, room -> {
            room.nextRound();
            return RoomProjections.presenterState(room);
        });
    }

    public PresenterRoomState rematch(String code) {
        return runner.execute(code, room -> {
            room.rematch();
            return RoomProjections.presenterState(room);
        });
    }

    /** Spec 002, AC-53: sonido de la TV. */
    public PresenterRoomState setSound(String code, boolean enabled) {
        return runner.execute(code, room -> {
            room.setSound(enabled);
            return RoomProjections.presenterState(room);
        });
    }

    /** AC-14, AC-27: true si este buzz fue el primero. */
    public boolean buzz(String code, UUID playerId) {
        return runner.execute(code, room -> room.buzz(playerId, clock.instant()));
    }

    /** AC-09: estado de conexión del jugador según su sesión STOMP. */
    public void setPlayerConnected(String code, UUID playerId, boolean connected) {
        runner.execute(code, (Room room) -> {
            room.setPlayerConnected(playerId, connected);
            return null;
        });
    }
}
