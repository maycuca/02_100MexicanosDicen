package mx.cienmexicanos.realtime;

import java.util.List;
import mx.cienmexicanos.common.api.RoomProjections;
import mx.cienmexicanos.game.domain.DomainEvent;
import mx.cienmexicanos.game.domain.Room;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * T301 (plan D3/D4): tras cada cambio publica el snapshot público, el del presentador y los eventos, todos con la
 * {@code version} vigente. Se invoca bajo el lock de la sala, así que las versiones salen en orden.
 */
@Component
public class RoomBroadcaster {

    private final SimpMessagingTemplate messaging;

    public RoomBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public static String stateTopic(String code) { return "/topic/rooms/" + code + "/state"; }
    public static String presenterTopic(String code) { return "/topic/rooms/" + code + "/presenter"; }
    public static String eventsTopic(String code) { return "/topic/rooms/" + code + "/events"; }

    public void publish(Room room, List<DomainEvent> events) {
        String code = room.code();
        messaging.convertAndSend(stateTopic(code), RoomProjections.publicState(room));
        messaging.convertAndSend(presenterTopic(code), RoomProjections.presenterState(room));
        for (DomainEvent event : events) {
            messaging.convertAndSend(eventsTopic(code), RoomProjections.event(event, room.version()));
        }
    }
}
