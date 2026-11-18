package mx.cienmexicanos.game.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GameRulesTest {

    @ParameterizedTest(name = "ronda {0} de {1} → ×{2}")
    @CsvSource({"1,5,1", "2,5,1", "3,5,1", "4,5,2", "5,5,3", "1,3,1", "2,3,2", "3,3,3", "6,8,1", "7,8,2", "8,8,3"})
    @DisplayName("AC-21 multiplicador ×1 normal, ×2 penúltima, ×3 última")
    void multiplier(int round, int total, int expected) {
        assertThat(GameRules.multiplier(round, total)).isEqualTo(expected);
    }

    @Test
    @DisplayName("AC-21 ronda fuera de rango no tiene multiplicador")
    void multiplierOutOfRange() {
        assertThatThrownBy(() -> GameRules.multiplier(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameRules.multiplier(6, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-18 tres strikes abren el robo")
    void steal() {
        assertThat(GameRules.opensSteal(2)).isFalse();
        assertThat(GameRules.opensSteal(3)).isTrue();
    }

    @Test
    @DisplayName("AC-19 acierto en robo → rival; X en robo → equipo con control")
    void stealWinner() {
        assertThat(GameRules.stealWinner(TeamId.A, true)).isEqualTo(TeamId.B);
        assertThat(GameRules.stealWinner(TeamId.A, false)).isEqualTo(TeamId.A);
        assertThat(GameRules.stealWinner(TeamId.B, true)).isEqualTo(TeamId.A);
    }

    @Test
    @DisplayName("AC-20 el bote se multiplica al cerrar la ronda")
    void award() {
        assertThat(GameRules.roundAward(70, 3)).isEqualTo(210);
    }

    @Test
    @DisplayName("AC-22 fin por meta o por última ronda; empate como TIE")
    void gameOver() {
        assertThat(GameRules.isGameOver(299, 0, 300, 2, 5)).isFalse();
        assertThat(GameRules.isGameOver(300, 0, 300, 2, 5)).isTrue();
        assertThat(GameRules.isGameOver(0, 301, 300, 2, 5)).isTrue();
        assertThat(GameRules.isGameOver(10, 20, 300, 5, 5)).isTrue();
        assertThat(GameRules.winner(10, 20)).isEqualTo("B");
        assertThat(GameRules.winner(20, 10)).isEqualTo("A");
        assertThat(GameRules.winner(15, 15)).isEqualTo("TIE");
    }

    @Test
    @DisplayName("TeamId.rival")
    void rival() {
        assertThat(TeamId.A.rival()).isEqualTo(TeamId.B);
        assertThat(TeamId.B.rival()).isEqualTo(TeamId.A);
    }
}
