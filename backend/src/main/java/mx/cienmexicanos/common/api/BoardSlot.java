package mx.cienmexicanos.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Esquema BoardSlot: {@code text} y {@code points} solo se incluyen si la casilla está revelada (AC-25). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoardSlot(int position, boolean revealed, String text, Integer points) {}
