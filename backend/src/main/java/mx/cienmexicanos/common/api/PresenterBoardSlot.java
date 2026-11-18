package mx.cienmexicanos.common.api;

/** Esquema PresenterBoardSlot (solo vista del presentador). */
public record PresenterBoardSlot(long answerId, int position, boolean revealed, String text, int points) {}
