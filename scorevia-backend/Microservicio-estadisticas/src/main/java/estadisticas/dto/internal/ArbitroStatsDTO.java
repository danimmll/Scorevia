package estadisticas.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArbitroStatsDTO {
    private String nombre;
    private BigDecimal mediaTarjetasAmarillas;
    private BigDecimal mediaTarjetasRojas;
    private BigDecimal mediaFaltasPorPartido;
}