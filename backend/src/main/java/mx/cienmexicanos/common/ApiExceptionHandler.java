package mx.cienmexicanos.common;

import mx.cienmexicanos.game.domain.ErrorCode;
import mx.cienmexicanos.game.domain.GameException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores como RFC 9457 con el campo {@code code} del esquema Problem (plan D7, T203).
 * Extiende {@link ResponseEntityExceptionHandler} para que los errores del framework (JSON mal formado,
 * validación, método no soportado…) también lleven {@code code}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GameException.class)
    ProblemDetail game(GameException ex) {
        return problem(statusOf(ex.code()), ex.code().name(), ex.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException ex) {
        return problem(ex.status(), ex.code().name(), ex.getMessage());
    }

    /** Mapeo de códigos de dominio a los HTTP status de openapi.yaml. */
    public static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case ROOM_NOT_FOUND, ANSWER_NOT_FOUND, PLAYER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CLAIM_INVALID, FORBIDDEN, NOT_YOUR_TURN, NOT_CAPTAIN -> HttpStatus.FORBIDDEN;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case CLAIM_USED, NAME_TAKEN, ROOM_NOT_IN_LOBBY, INVALID_STATE, TEAM_EMPTY, NOT_ENOUGH_QUESTIONS,
                 NO_ACTIVE_TEAM, TEAM_NAME_TAKEN, TIMER_DISABLED, NO_MORE_QUESTIONS ->
                    HttpStatus.CONFLICT;
        };
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd
                && (pd.getProperties() == null || !pd.getProperties().containsKey("code"))) {
            pd.setProperty("code", statusCode.value() == 400 ? ErrorCode.VALIDATION_ERROR.name()
                    : "HTTP_" + statusCode.value());
        }
        return response;
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        return pd;
    }
}
