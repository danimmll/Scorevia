package predicciones.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Estadísticas detalladas de un equipo en un partido")
public class EstadisticaEquipoDTO {

    private Long idPartido;
    private Long idEquipo;

    @NotBlank(message = "El nombre del equipo es obligatorio")
    @Schema(description = "Nombre del equipo", example = "Real Madrid")
    private String nombreEquipo;

    @Schema(description = "Porcentaje de posesión", example = "55.5")
    private BigDecimal posesion;

    @Schema(description = "Tiros totales", example = "15")
    private Integer tiros;

    @Schema(description = "Tiros a puerta", example = "6")
    private Integer tirosPuerta;

    @Schema(description = "Córners a favor", example = "5")
    private Integer corners;

    @Schema(description = "Faltas cometidas", example = "12")
    private Integer faltas;

    @Schema(description = "Tarjetas amarillas", example = "2")
    private Integer tarjetasAmarillas;

    @Schema(description = "Tarjetas rojas", example = "0")
    private Integer tarjetasRojas;

    @Schema(description = "Goles esperados (xG)", example = "1.85")
    private BigDecimal golesEsperados;

    @Schema(description = "Sistemas tacticos de los equipos", example = "4-3-3")
    private String sistemaTactico;

    @Schema(description = "Otras estadísticas extra en formato clave-valor")
    private Map<String, String> estadisticasExtra;
}