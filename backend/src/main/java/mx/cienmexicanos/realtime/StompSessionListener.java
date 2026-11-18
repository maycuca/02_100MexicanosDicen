package mx.cienmexicanos.realtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mx.cienmexicanos.game.GameService;
import mx.cienmexicanos.game.domain.GameException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** AC-09: mantiene {@code Player.connected} según las sesiones STOMP abiertas de cada jugador. */
@Component
public class StompSessionListener {

    private final GameService game;
    private final Map<String, StompPrincipal> playerSessions = new ConcurrentHashMap<>();

    public StompSessionListener(GameService game) {
        this.game = game;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        if (event.getUser() instanceof StompPrincipal p && p.role() == StompPrincipal.Role.PLAYER) {
            String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
            if (sessionId != null) {
                playerSessions.put(sessionId, p);
            }
            update(p, true);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompPrincipal p = playerSessions.remove(event.getSessionId());
        if (p == null) {
            return;
        }
        boolean stillConnected = playerSessions.values().stream().anyMatch(o -> o.playerId().equals(p.playerId()));
        if (!stillConnected) {
            update(p, false);
        }
    }

    private void update(StompPrincipal p, boolean connected) {
        try {
            game.setPlayerConnected(p.roomCode(), p.playerId(), connected);
        } catch (GameException e) {
            // la sala ya expiró: nada que actualizar
        }
    }
}
