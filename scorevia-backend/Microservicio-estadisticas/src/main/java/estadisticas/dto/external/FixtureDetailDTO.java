package estadisticas.dto.external;

import lombok.Data;

/**
 * Datos basicos del encuentro. {@code id} es el que usamos como
 * {@code Partido.idPartido}. {@code referee} suele venir como "Nombre" o
 * "Nombre, Pais"; se normaliza en el servicio de ingesta.
 *
 * @author Daniel
 * @version 1.0
 */
@Data
public class FixtureDetailDTO {

    private Integer id;
    private String referee;

    /** ISO-8601 con offset, ej. "2026-06-29T19:00:00+00:00". */
    private String date;

    private FixtureStatusDTO status;
}
