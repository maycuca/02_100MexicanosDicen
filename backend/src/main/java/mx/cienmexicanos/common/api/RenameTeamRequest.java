package mx.cienmexicanos.common.api;

/**
 * Esquema RenameTeamRequest (spec 003, AC-63). La longitud 2–20 se valida en el dominio tras recortar espacios, después
 * de las comprobaciones de token, capitán y estado (AC-64).
 */
public record RenameTeamRequest(String name) {}
