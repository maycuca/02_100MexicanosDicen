package mx.cienmexicanos.game;

import jakarta.validation.Valid;
import mx.cienmexicanos.common.api.AssignControlRequest;
import mx.cienmexicanos.common.api.PresenterRoomState;
import mx.cienmexicanos.common.api.SetSoundRequest;
import mx.cienmexicanos.common.api.TimerRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acciones del presentador (T204). El X-Presenter-Token lo valida
 * {@link mx.cienmexicanos.config.PresenterTokenInterceptor} (AC-26).
 */
@RestController
@RequestMapping("/api/rooms/{code}")
public class GameController {

    private final GameService game;

    public GameController(GameService game) {
        this.game = game;
    }

    @PostMapping("/start")
    public PresenterRoomState startGame(@PathVariable String code) {
        return game.start(code);
    }

    @PostMapping("/rounds/current/answers/{answerId}/reveal")
    public PresenterRoomState revealAnswer(@PathVariable String code, @PathVariable long answerId) {
        return game.reveal(code, answerId);
    }

    @PostMapping("/rounds/current/strike")
    public PresenterRoomState markStrike(@PathVariable String code) {
        return game.strike(code);
    }

    /** Spec 008, AC-122 (operationId changeQuestion). */
    @PostMapping("/rounds/current/question")
    public PresenterRoomState changeQuestion(@PathVariable String code) {
        return game.changeQuestion(code);
    }

    /** Spec 005, AC-81 (operationId controlTimer). */
    @PostMapping("/rounds/current/timer")
    public PresenterRoomState controlTimer(@PathVariable String code, @Valid @RequestBody TimerRequest request) {
        return game.controlTimer(code, request.action());
    }

    @PostMapping("/rounds/current/control")
    public PresenterRoomState assignControl(@PathVariable String code,
                                            @Valid @RequestBody AssignControlRequest request) {
        return game.assignControl(code, request.team());
    }

    @PostMapping("/next-round")
    public PresenterRoomState nextRound(@PathVariable String code) {
        return game.nextRound(code);
    }

    @PostMapping("/rematch")
    public PresenterRoomState rematch(@PathVariable String code) {
        return game.rematch(code);
    }

    /** Spec 002, AC-53 (operationId setSound). */
    @PostMapping("/sound")
    public PresenterRoomState setSound(@PathVariable String code, @Valid @RequestBody SetSoundRequest request) {
        return game.setSound(code, request.enabled());
    }
}
