package mx.cienmexicanos.question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.security.SecureRandom;
import mx.cienmexicanos.common.api.QuestionDto;
import mx.cienmexicanos.game.domain.AnswerSnapshot;
import mx.cienmexicanos.game.domain.QuestionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Banco de preguntas en SQLite y selección aleatoria por partida (T205, AC-11). */
@Service
public class QuestionService {

    private final QuestionRepository repository;
    private final Random random = new SecureRandom();

    public QuestionService(QuestionRepository repository) {
        this.repository = repository;
    }

    /** AC-11: {@code count} preguntas activas distintas al azar (o todas si no alcanzan). */
    @Transactional(readOnly = true)
    public List<QuestionSnapshot> pickRandom(int count) {
        List<Question> active = new ArrayList<>(repository.findByActiveTrue());
        Collections.shuffle(active, random);
        return active.stream().limit(count).map(QuestionService::snapshot).toList();
    }

    /** Spec 008 (AC-122): una pregunta activa al azar que no esté en {@code exclude}; null si no queda ninguna. */
    @Transactional(readOnly = true)
    public QuestionSnapshot pickOneExcluding(java.util.Set<Long> exclude) {
        List<Question> candidates = new ArrayList<>(repository.findByActiveTrue().stream()
                .filter(q -> !exclude.contains(q.getId()))
                .toList());
        if (candidates.isEmpty()) {
            return null;
        }
        return snapshot(candidates.get(random.nextInt(candidates.size())));
    }

    @Transactional(readOnly = true)
    public List<QuestionDto> listActive() {
        return repository.findByActiveTrue().stream()
                .map(q -> new QuestionDto(q.getId(), q.getText(), q.getAnswers().stream()
                        .map(a -> new QuestionDto.AnswerDto(a.getId(), a.getText(), a.getPoints(), a.getPosition()))
                        .toList()))
                .toList();
    }

    private static QuestionSnapshot snapshot(Question q) {
        return new QuestionSnapshot(q.getId(), q.getText(), q.getAnswers().stream()
                .map(a -> new AnswerSnapshot(a.getId(), a.getText(), a.getPoints(), a.getPosition()))
                .toList());
    }
}
