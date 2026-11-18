package mx.cienmexicanos.question;

import java.util.List;
import mx.cienmexicanos.common.ApiException;
import mx.cienmexicanos.common.api.QuestionDto;
import mx.cienmexicanos.game.domain.ErrorCode;
import mx.cienmexicanos.game.domain.GameException;
import mx.cienmexicanos.room.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** operationId listQuestions (T205): solo para un presentador de alguna sala activa. */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questions;
    private final RoomService rooms;

    public QuestionController(QuestionService questions, RoomService rooms) {
        this.questions = questions;
        this.rooms = rooms;
    }

    @GetMapping
    public List<QuestionDto> listQuestions(@RequestHeader(value = "X-Presenter-Token", required = false) String token) {
        if (token == null || token.isBlank()) {
            throw new GameException(ErrorCode.UNAUTHORIZED, "Falta el header X-Presenter-Token");
        }
        if (!rooms.isPresenterOfAnyRoom(token)) {
            // El contrato solo define 401 para esta operación.
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "X-Presenter-Token inválido");
        }
        return questions.listActive();
    }
}
