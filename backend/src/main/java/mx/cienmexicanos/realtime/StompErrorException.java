package mx.cienmexicanos.realtime;

/** Error de un mensaje STOMP que se responde en {@code /user/queue/errors}. */
class StompErrorException extends RuntimeException {

    private final StompErrorCode code;

    StompErrorException(StompErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    StompErrorCode code() {
        return code;
    }
}
