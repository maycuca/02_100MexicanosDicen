package mx.cienmexicanos.realtime;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mx.cienmexicanos.game.domain.GameException;
import mx.cienmexicanos.room.RoomRegistry;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * T301 (plan D6, AC-26): autentica la sesión STOMP y protege los destinos.
 * <ul>
 *   <li>CONNECT: exige {@code X-Player-Token} o {@code X-Presenter-Token} válido; sin token o con token inválido
 *       se rechaza con un frame ERROR (spec C-8).</li>
 *   <li>SUBSCRIBE a {@code /topic/rooms/{code}/presenter}: solo con un presenter token vigente de esa sala.
 *       No se permiten patrones (el broker simple los interpretaría como comodines).</li>
 *   <li>SEND: solo a destinos de aplicación ({@code /app/**}); nadie publica directo en el broker.</li>
 * </ul>
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    public static final String PLAYER_HEADER = "X-Player-Token";
    public static final String PRESENTER_HEADER = "X-Presenter-Token";

    private static final Pattern PRESENTER_TOPIC = Pattern.compile("^/topic/rooms/([^/]+)/presenter$");

    private final RoomRegistry registry;

    public StompAuthInterceptor(RoomRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> { }
        }
        return message;
    }

    private StompPrincipal authenticate(StompHeaderAccessor accessor) {
        String presenterToken = accessor.getFirstNativeHeader(PRESENTER_HEADER);
        String playerToken = accessor.getFirstNativeHeader(PLAYER_HEADER);
        String sessionId = accessor.getSessionId();
        if (presenterToken != null && !presenterToken.isBlank()) {
            return registry.findFirst(room -> room.isPresenterToken(presenterToken)
                            ? Optional.of(StompPrincipal.presenter(room.code(), presenterToken, sessionId))
                            : Optional.<StompPrincipal>empty())
                    .orElseThrow(() -> new MessageDeliveryException("FORBIDDEN: X-Presenter-Token inválido"));
        }
        if (playerToken != null && !playerToken.isBlank()) {
            return registry.findFirst(room -> room.playerByToken(playerToken)
                            .map(p -> StompPrincipal.player(room.code(), p.id(), playerToken)))
                    .orElseThrow(() -> new MessageDeliveryException("FORBIDDEN: X-Player-Token inválido"));
        }
        throw new MessageDeliveryException("UNAUTHORIZED: falta X-Player-Token o X-Presenter-Token");
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.contains("*") || destination.contains("?")
                || destination.contains("{")) {
            throw new MessageDeliveryException("FORBIDDEN: destino de suscripción no permitido");
        }
        if (!destination.startsWith("/topic/rooms/") && !destination.startsWith("/user/queue/")) {
            throw new MessageDeliveryException("FORBIDDEN: destino de suscripción no permitido");
        }
        Matcher m = PRESENTER_TOPIC.matcher(destination);
        if (m.matches() && !isPresenterOf(accessor, m.group(1))) {
            throw new MessageDeliveryException("FORBIDDEN: el canal del presentador requiere X-Presenter-Token");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/")) {
            throw new MessageDeliveryException("FORBIDDEN: solo se permite enviar a /app/**");
        }
    }

    private boolean isPresenterOf(StompHeaderAccessor accessor, String code) {
        if (!(accessor.getUser() instanceof StompPrincipal p) || p.role() != StompPrincipal.Role.PRESENTER
                || !p.roomCode().equals(RoomRegistry.normalize(code))) {
            return false;
        }
        try {
            return registry.withRoom(code, room -> room.isPresenterToken(p.token()));
        } catch (GameException e) {
            return false;
        }
    }
}
