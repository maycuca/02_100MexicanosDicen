package mx.cienmexicanos.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** RNF-5: al menos 10 preguntas, 3–8 respuestas, puntos ≤ 100. Verifica Flyway sobre SQLite (T004). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuestionSeedTest {

    @Autowired
    QuestionRepository repository;

    @Test
    void seededQuestionsRespectInvariants() {
        List<Question> questions = repository.findByActiveTrue();

        assertThat(questions).hasSizeGreaterThanOrEqualTo(10);
        assertThat(questions).allSatisfy(q -> {
            assertThat(q.getAnswers()).hasSizeBetween(3, 8);
            assertThat(q.getAnswers().stream().mapToInt(Answer::getPoints).sum()).isLessThanOrEqualTo(100);
        });
    }
}
