package mx.cienmexicanos.player;

import jakarta.validation.Valid;
import mx.cienmexicanos.common.api.JoinRoomRequest;
import mx.cienmexicanos.common.api.JoinRoomResponse;
import mx.cienmexicanos.common.api.PlayerDto;
import mx.cienmexicanos.common.api.RenameTeamRequest;
import mx.cienmexicanos.common.api.RoomState;
import mx.cienmexicanos.game.domain.TeamId;
import mx.cienmexicanos.room.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** operationIds joinRoom, getMe (T202, AC-05…AC-09) y renameTeam (spec 003, T802, AC-62…AC-65). */
@RestController
@RequestMapping("/api/rooms/{code}")
public class PlayerController {

    private final RoomService rooms;

    public PlayerController(RoomService rooms) {
        this.rooms = rooms;
    }

    @PostMapping("/players")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinRoomResponse joinRoom(@PathVariable String code, @Valid @RequestBody JoinRoomRequest request) {
        return rooms.join(code, request);
    }

    @GetMapping("/players/me")
    public PlayerDto getMe(@PathVariable String code,
                           @RequestHeader(value = "X-Player-Token", required = false) String token) {
        return rooms.me(code, token);
    }

    @PutMapping("/teams/{team}/name")
    public RoomState renameTeam(@PathVariable String code, @PathVariable TeamId team,
                                @RequestHeader(value = "X-Player-Token", required = false) String token,
                                @RequestBody RenameTeamRequest request) {
        return rooms.renameTeam(code, team, token, request);
    }
}
