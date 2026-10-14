package estadisticas.dto.external;

import java.util.List;

import estadisticas.dto.partidos.PartidoDTO;
import lombok.*;

/**
 * Objeto envoltorio de la respuesta de {@code GET /fixtures} de API-Football.
 * Vive en su propio subpaquete ({@code dto.external}) separado de los DTOs de
 * salida (como {@link PartidoDTO})
 * porque estos representan el contrato de la API externa, no el nuestro.
 *
 * @author Daniel
 * @version 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ApiFootballFixturesResponseDTO {

    private String get;
    private int results;
    private List<FixtureItemDTO> response;
}
