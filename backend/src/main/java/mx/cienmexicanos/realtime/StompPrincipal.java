package mx.cienmexicanos.realtime;

import java.security.Principal;
import java.util.UUID;

/**
 * Identidad de una sesión STOMP, fijada en CONNECT por {@link StompAuthInterceptor}; permite que
 * {@code /user/queue/errors} llegue a la sesión que envió el mensaje.
 */
public record StompPrincipal(String name, Role role, String roomCode, UUID playerId, String token)
        implements Principal {

    public enum Role { PLAYER, PRESENTER }

    @Override
    public String getName() {
        return name;
    }

    static StompPrincipal player(String roomCode, UUID playerId, String token) {
        return new StompPrincipal("player-" + playerId, Role.PLAYER, roomCode, playerId, token);
    }

    static StompPrincipal presenter(String roomCode, String token, String sessionId) {
        return new StompPrincipal("presenter-" + roomCode + "-" + sessionId, Role.PRESENTER, roomCode, null, token);
    }
}
