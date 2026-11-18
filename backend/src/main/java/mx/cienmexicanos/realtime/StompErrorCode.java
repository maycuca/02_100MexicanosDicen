package mx.cienmexicanos.realtime;

import mx.cienmexicanos.game.domain.ErrorCode;

/** Valores de StompError.code (openapi.yaml v0.3.0). */
public enum StompErrorCode {
    NOT_YOUR_TURN, INVALID_STATE, ROOM_NOT_FOUND, UNAUTHORIZED;

    static StompErrorCode from(ErrorCode code) {
        return switch (code) {
            case NOT_YOUR_TURN -> NOT_YOUR_TURN;
            case ROOM_NOT_FOUND -> ROOM_NOT_FOUND;
            case UNAUTHORIZED, FORBIDDEN -> UNAUTHORIZED;
            default -> INVALID_STATE;
        };
    }
}
