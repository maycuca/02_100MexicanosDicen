package mx.cienmexicanos.common.api;

import java.util.List;

/** Esquema Question. */
public record QuestionDto(long id, String text, List<AnswerDto> answers) {

    public record AnswerDto(long id, String text, int points, int position) {}
}
