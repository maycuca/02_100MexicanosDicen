package mx.cienmexicanos.game.domain;

/** Violación de una regla del juego o de la máquina de estados. */
public class GameException extends RuntimeException {

    private final ErrorCode code;

    public GameException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    static GameException invalidState(RoomStatus status, String command) {
        return new GameException(ErrorCode.INVALID_STATE,
                "No se puede ejecutar '" + command + "' en el estado " + status);
    }
}
