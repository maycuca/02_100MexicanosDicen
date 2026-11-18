package mx.cienmexicanos.game.domain;

import java.util.Comparator;
import java.util.List;

/** Pregunta elegida para una ronda (AC-11). */
public record QuestionSnapshot(long id, String text, List<AnswerSnapshot> answers) {

    public QuestionSnapshot {
        answers = answers.stream().sorted(Comparator.comparingInt(AnswerSnapshot::position)).toList();
    }
}
