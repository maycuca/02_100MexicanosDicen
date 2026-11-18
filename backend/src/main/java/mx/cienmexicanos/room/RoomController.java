package mx.cienmexicanos.room;

import jakarta.validation.Valid;
import java.net.URI;
import mx.cienmexicanos.common.api.ClaimPresenterRequest;
import mx.cienmexicanos.common.api.ClaimPresenterResponse;
import mx.cienmexicanos.common.api.CreateRoomRequest;
import mx.cienmexicanos.common.api.CreateRoomResponse;
import mx.cienmexicanos.common.api.PresenterClaim;
import mx.cienmexicanos.common.api.PresenterRoomState;
import mx.cienmexicanos.common.api.RoomState;
import org.springframework.http.ResponseEntity;
import mx.cienmexicanos.config.PresenterTokenInterceptor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * operationIds createRoom, getRoom, getPresenterView, claimPresenter, regeneratePresenterClaim (T201, T205b).
 * El X-Presenter-Token lo valida {@link mx.cienmexicanos.config.PresenterTokenInterceptor} (T203).
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping
    public ResponseEntity<CreateRoomResponse> createRoom(@Valid @RequestBody(required = false) CreateRoomRequest request) {
        CreateRoomResponse created = rooms.create(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + created.code())).body(created);
    }

    @GetMapping("/{code}")
    public RoomState getRoom(@PathVariable String code) {
        return rooms.publicState(code);
    }

    /** Spec 005, AC-91 (operationId closeRoom): 401 sin token → 404 sala inexistente → 403 token inválido. */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> closeRoom(@PathVariable String code,
            @RequestHeader(value = PresenterTokenInterceptor.HEADER, required = false) String presenterToken) {
        rooms.close(code, presenterToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/presenter-view")
    public PresenterRoomState getPresenterView(@PathVariable String code) {
        return rooms.presenterState(code);
    }

    /** AC-30: canje de un solo uso del QR de presentador. */
    @PostMapping("/{code}/presenter/claim")
    public ClaimPresenterResponse claimPresenter(@PathVariable String code, @RequestBody ClaimPresenterRequest request) {
        return rooms.claimPresenter(code, request.claimToken());
    }

    /** AC-33: nuevo QR de presentador; invalida el anterior. */
    @PostMapping("/{code}/presenter/claim-token")
    public PresenterClaim regeneratePresenterClaim(@PathVariable String code) {
        return rooms.regenerateClaim(code);
    }
}
