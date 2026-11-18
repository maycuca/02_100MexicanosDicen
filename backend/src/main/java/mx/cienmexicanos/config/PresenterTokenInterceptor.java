package mx.cienmexicanos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import mx.cienmexicanos.room.RoomService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * AC-26 (T203, plan D6): exige un {@code X-Presenter-Token} válido para la sala en las acciones del presentador.
 * Sin header → 401 UNAUTHORIZED; sala inexistente → 404 ROOM_NOT_FOUND; token inválido o de otra sala →
 * 403 FORBIDDEN (spec C-5).
 */
@Component
public class PresenterTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Presenter-Token";

    static final String[] PATHS = {
            "/api/rooms/*/presenter-view",
            "/api/rooms/*/presenter/claim-token",
            "/api/rooms/*/start",
            "/api/rooms/*/rounds/**",
            "/api/rooms/*/next-round",
            "/api/rooms/*/rematch",
            "/api/rooms/*/sound"
    };

    private final RoomService rooms;

    public PresenterTokenInterceptor(RoomService rooms) {
        this.rooms = rooms;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        @SuppressWarnings("unchecked")
        var vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String code = vars == null ? null : vars.get("code");
        if (code == null) {
            return true;
        }
        rooms.requirePresenter(code, request.getHeader(HEADER));
        return true;
    }
}
