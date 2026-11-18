package mx.cienmexicanos.game.domain;

/** Casilla del tablero de la ronda. */
public final class Slot {

    private final AnswerSnapshot answer;
    private boolean revealed;

    Slot(AnswerSnapshot answer) {
        this.answer = answer;
    }

    public long answerId() { return answer.id(); }
    public int position() { return answer.position(); }
    public String text() { return answer.text(); }
    public int points() { return answer.points(); }
    public boolean revealed() { return revealed; }

    void reveal() {
        revealed = true;
    }
}
