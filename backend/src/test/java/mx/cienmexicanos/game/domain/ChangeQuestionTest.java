package mx.cienmexicanos.game.domain;

import static mx.cienmexicanos.game.domain.Fixtures.T0;
import static mx.cienmexicanos.game.domain.Fixtures.currentAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 008 (T1321): cambiar la pregunta de la ronda (AC-122). */
class ChangeQuestionTest {

    static void assertError(ErrorCode code, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    @DisplayName("AC-122 reemplaza la pregunta, reinicia el cara a cara y publica QUESTION_CHANGED")
    void replacesQuestionAndResetsFaceOff() {
        Room room = Fixtures.faceOff();
        Round before = room.currentRound().orElseThrow();
        room.buzz(before.faceOff().playerA(), T0);
        room.controlTimer(TimerAction.START, T0);
        room.drainEvents();
        long version = room.version();

        room.changeQuestion(Fixtures.question(99));

        Round after = room.currentRound().orElseThrow();
        assertThat(after.question().id()).isEqualTo(99);
        assertThat(after.number()).isEqualTo(before.number());
        assertThat(after.multiplier()).isEqualTo(before.multiplier());
        assertThat(after.faceOff().playerA()).isEqualTo(before.faceOff().playerA());
        assertThat(after.faceOff().playerB()).isEqualTo(before.faceOff().playerB());
        assertThat(after.faceOff().buzzWinner()).as("sin buzzer").isEmpty();
        assertThat(after.activeTeam()).isEmpty();
        assertThat(after.timerRunning()).isFalse();
        assertThat(after.timerRemainingMs()).isEqualTo(room.turnSeconds() * 1000L);
        assertThat(after.pot()).isZero();
        assertThat(room.status()).isEqualTo(RoomStatus.FACE_OFF);
        assertThat(room.version()).isEqualTo(version + 1);
        assertThat(room.drainEvents()).extracting(DomainEvent::type).containsExactly(GameEventType.QUESTION_CHANGED);
    }

    @Test
    @DisplayName("AC-122 no se repiten: se evitan las preguntas de la partida y las ya descartadas")
    void avoidsUsedAndDiscarded() {
        Room room = Fixtures.faceOff(); // preguntas 1..5
        assertThat(room.questionIdsToAvoid()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
        room.changeQuestion(Fixtures.question(99));
        assertThat(room.questionIdsToAvoid()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 99L);
        room.changeQuestion(Fixtures.question(100));
        assertThat(room.questionIdsToAvoid()).as("la 99 queda descartada").contains(99L, 100L);
    }

    @Test
    @DisplayName("AC-122 errores: fuera de FACE_OFF o con respuesta revelada → INVALID_STATE; sin preguntas → NO_MORE_QUESTIONS")
    void errors() {
        assertError(ErrorCode.INVALID_STATE, () -> Fixtures.lobby(5, 300).changeQuestion(Fixtures.question(99)));
        assertError(ErrorCode.INVALID_STATE, () -> Fixtures.playing(TeamId.A).changeQuestion(Fixtures.question(99)));

        Room revealed = Fixtures.faceOff();
        revealed.buzz(revealed.currentRound().orElseThrow().faceOff().playerA(), T0);
        revealed.reveal(currentAnswer(revealed, 2), T0);
        long version = revealed.version();
        assertError(ErrorCode.INVALID_STATE, () -> revealed.changeQuestion(Fixtures.question(99)));
        assertThat(revealed.version()).isEqualTo(version);

        assertError(ErrorCode.NO_MORE_QUESTIONS, () -> Fixtures.faceOff().changeQuestion(null));
    }
}
