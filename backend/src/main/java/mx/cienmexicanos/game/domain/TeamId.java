package mx.cienmexicanos.game.domain;

/** Esquema TeamId: los dos equipos de la sala. */
public enum TeamId {
    A, B;

    public TeamId rival() {
        return this == A ? B : A;
    }
}
