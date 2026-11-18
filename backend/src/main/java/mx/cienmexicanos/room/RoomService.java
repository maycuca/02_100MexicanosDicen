package mx.cienmexicanos.room;

import java.time.Clock;
import mx.cienmexicanos.common.ApiException;
import mx.cienmexicanos.common.api.ClaimPresenterResponse;
import mx.cienmexicanos.common.api.CreateRoomRequest;
import mx.cienmexicanos.common.api.CreateRoomResponse;
import mx.cienmexicanos.common.api.JoinRoomRequest;
import mx.cienmexicanos.common.api.JoinRoomResponse;
import mx.cienmexicanos.common.api.PlayerDto;
import mx.cienmexicanos.common.api.PresenterClaim;
import mx.cienmexicanos.common.api.PresenterRoomState;
import mx.cienmexicanos.common.api.RenameTeamRequest;
import mx.cienmexicanos.common.api.RoomProjections;
import mx.cienmexicanos.common.api.RoomState;
import mx.cienmexicanos.game.domain.ErrorCode;
import mx.cienmexicanos.game.domain.GameException;
import mx.cienmexicanos.game.domain.Player;
import mx.cienmexicanos.game.domain.Room;
import mx.cienmexicanos.game.domain.TeamId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Casos de uso de sala, presentador y jugadores (T201, T202, T205b). */
@Service
public class RoomService {

    private final RoomRegistry registry;
    private final RoomCommandRunner runner;
    private final Clock clock;

    public RoomService(RoomRegistry registry, RoomCommandRunner runner, Clock clock) {
        this.registry = registry;
        this.runner = runner;
        this.clock = clock;
    }

    /** AC-01, AC-02, AC-30; spec 002 AC-46. */
    public CreateRoomResponse create(CreateRoomRequest request) {
        CreateRoomRequest r = request == null ? CreateRoomRequest.defaults() : request;
        Room created = registry.create(code -> new Room(code, r.teamANameOrDefault(), r.teamBNameOrDefault(),
                r.roundsOrDefault(), r.targetScoreOrDefault(), r.turnSecondsOrDefault(),
                r.autoStrikeOnTimeoutOrDefault(), clock.instant()));
        return runner.read(created.code(), room -> new CreateRoomResponse(room.code(), room.hostToken().toString(),
                room.claimToken().toString(), RoomProjections.presenterState(room)));
    }

    /** AC-25. */
    public RoomState publicState(String code) {
        return runner.read(code, RoomProjections::publicState);
    }

    public PresenterRoomState presenterState(String code) {
        return runner.read(code, RoomProjections::presenterState);
    }

    /** AC-30. */
    public ClaimPresenterResponse claimPresenter(String code, String claimToken) {
        return runner.execute(code, room -> {
            String token = room.claimPresenter(claimToken).toString();
            return new ClaimPresenterResponse(token, RoomProjections.presenterState(room));
        });
    }

    /** AC-33. */
    public PresenterClaim regenerateClaim(String code) {
        return runner.execute(code, room -> new PresenterClaim(room.regenerateClaim().toString()));
    }

    /** AC-05…AC-07. */
    public JoinRoomResponse join(String code, JoinRoomRequest request) {
        return runner.execute(code, room -> {
            Player p = room.addPlayer(request.name(), request.team());
            return new JoinRoomResponse(RoomProjections.player(p), p.token().toString());
        });
    }

    /** AC-09. */
    public PlayerDto me(String code, String playerToken) {
        if (playerToken == null || playerToken.isBlank()) {
            throw new GameException(ErrorCode.UNAUTHORIZED, "Falta el header X-Player-Token");
        }
        return runner.read(code, room -> room.playerByToken(playerToken)
                .map(RoomProjections::player)
                // El contrato de getMe solo define 401 para credenciales.
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                        "El X-Player-Token no corresponde a un jugador de esta sala")));
    }

    /** Spec 003, AC-63…AC-65: el capitán nombra a su equipo; responde el RoomState público. */
    public RoomState renameTeam(String code, TeamId team, String playerToken, RenameTeamRequest request) {
        if (playerToken == null || playerToken.isBlank()) {
            throw new GameException(ErrorCode.UNAUTHORIZED, "Falta el header X-Player-Token");
        }
        return runner.execute(code, room -> {
            room.renameTeam(playerToken, team, request == null ? null : request.name());
            return RoomProjections.publicState(room);
        });
    }

    /**
     * Spec 005, AC-91: el presentador cierra la sala. Se publica ROOM_CLOSED (con el temporizador detenido, así se
     * cancela su vencimiento) y la sala deja de existir: cualquier comando posterior responde 404.
     */
    public void close(String code, String presenterToken) {
        requirePresenter(code, presenterToken);
        runner.execute(code, (Room room) -> {
            room.close();
            registry.remove(room);
            return null;
        });
    }

    /** AC-26: valida un token de presentador para la sala. */
    public void requirePresenter(String code, String presenterToken) {
        if (presenterToken == null || presenterToken.isBlank()) {
            throw new GameException(ErrorCode.UNAUTHORIZED, "Falta el header X-Presenter-Token");
        }
        boolean valid = runner.read(code, room -> room.isPresenterToken(presenterToken));
        if (!valid) {
            throw new GameException(ErrorCode.FORBIDDEN, "X-Presenter-Token inválido para esta sala");
        }
    }

    /** ¿El token es de presentador en alguna sala activa? (banco de preguntas). */
    public boolean isPresenterOfAnyRoom(String presenterToken) {
        return presenterToken != null && registry.anyMatch(room -> room.isPresenterToken(presenterToken));
    }
}
