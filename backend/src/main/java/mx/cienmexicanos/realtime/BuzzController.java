package mx.cienmexicanos.realtime;

import java.security.Principal;
import mx.cienmexicanos.common.api.StompError;
import mx.cienmexicanos.game.GameService;
import mx.cienmexicanos.game.domain.GameException;
import mx.cienmexicanos.room.RoomRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * T302: {@code SEND /app/rooms/{code}/buzz} — primer buzz gana (AC-14), solo el designado en FACE_OFF (AC-27).
 * Los errores van a {@code /user/queue/errors} con los códigos del esquema StompError.
 */
@Controller
public class BuzzController {

    private static final Logger log = LoggerFactory.getLogger(BuzzController.class);

    private final GameService game;
    private final RoomRegistry registry;

    public BuzzController(GameService game, RoomRegistry registry) {
        this.game = game;
        this.registry = registry;
    }

    @MessageMapping("/rooms/{code}/buzz")
    public void buzz(@DestinationVariable String code, Principal principal) {
        if (!(principal instanceof StompPrincipal p) || p.role() != StompPrincipal.Role.PLAYER) {
            throw new StompErrorException(StompErrorCode.UNAUTHORIZED,
                    "Conéctate con tu X-Player-Token para presionar el buzzer");
        }
        if (!registry.exists(code)) {
            throw new StompErrorException(StompErrorCode.ROOM_NOT_FOUND, "No existe la sala " + code);
        }
        if (!p.roomCode().equals(RoomRegistry.normalize(code))) {
            throw new StompErrorException(StompErrorCode.NOT_YOUR_TURN, "No eres jugador de esta sala");
        }
        game.buzz(code, p.playerId());
    }

    @MessageExceptionHandler(StompErrorException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public StompError onStompError(StompErrorException e) {
        return new StompError(e.code().name(), e.getMessage());
    }

    @MessageExceptionHandler(GameException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public StompError onGameError(GameException e) {
        return new StompError(StompErrorCode.from(e.code()).name(), e.getMessage());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public StompError onUnexpected(Exception e) {
        log.warn("Error procesando mensaje STOMP", e);
        return new StompError(StompErrorCode.INVALID_STATE.name(), "No se pudo procesar el mensaje");
    }
}
