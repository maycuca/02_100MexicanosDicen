package mx.cienmexicanos.common;

import mx.cienmexicanos.game.domain.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error de la capa web con un HTTP status explícito (cuando el contrato fija uno distinto al por defecto). */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public ErrorCode code() { return code; }
}
