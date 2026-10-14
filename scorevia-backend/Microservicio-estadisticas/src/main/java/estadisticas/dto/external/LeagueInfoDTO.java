package estadisticas.dto.external;

import lombok.Data;

/**
 * Datos de la liga dentro de un fixture. {@code round} es del tipo
 * "Regular Season - 12" o "Quarter-finals"; de ahi se extrae la jornada
 * cuando es numerica.
 *
 * @author Daniel
 * @version 1.0
 */
@Data
public class LeagueInfoDTO {

    private Integer id;
    private String name;
    private String country;
    private Integer season;
    private String round;
}
