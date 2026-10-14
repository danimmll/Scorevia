package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Nota: {@code logo} es el campo que faltaba en la primera version de este DTO
 * (la de Gemini) y que necesitamos para rellenar {@code Equipo.escudoUrl}.
 *
 * @author Daniel
 * @version 1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamInfoDTO {

    private Integer id;
    private String name;
    private String logo;
    private Boolean winner;
}
