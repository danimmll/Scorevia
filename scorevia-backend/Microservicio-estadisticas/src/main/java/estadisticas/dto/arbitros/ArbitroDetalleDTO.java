package estadisticas.dto.arbitros;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ficha de un árbitro.
 *
 * <p>API-Football no da al árbitro como entidad propia (llega como texto suelto dentro del
 * fixture), así que no hay foto ni biografía que enseñar. Lo que sí se puede contar es su
 * trabajo: cuántos partidos ha dirigido en la base y con qué mano va de tarjetas.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArbitroDetalleDTO {

    @Schema(description = "Id del árbitro", example = "312")
    private Integer idArbitro;

    @Schema(description = "Nombre del árbitro", example = "Mateu Lahoz")
    private String nombre;

    @Schema(description = "País del árbitro", example = "Spain")
    private String pais;

    @Schema(description = "Partidos dirigidos que constan en la base", example = "184")
    private long partidosDirigidos;

    @Schema(description = "Tarjetas amarillas mostradas en esos partidos", example = "812")
    private long tarjetasAmarillas;

    @Schema(description = "Tarjetas rojas mostradas en esos partidos", example = "31")
    private long tarjetasRojas;

    /**
     * Amarillas por partido. Solo se calcula sobre los partidos de los que constan eventos:
     * dividir entre todos los dirigidos daría una media artificialmente baja para un árbitro
     * cuyos partidos antiguos nunca llegaron a tener timeline ingerido.
     */
    @Schema(description = "Media de amarillas por partido con eventos registrados", example = "4.4")
    private BigDecimal mediaAmarillas;

    @Schema(description = "Partidos suyos de los que sí constan eventos, base de la media", example = "184")
    private long partidosConEventos;
}
