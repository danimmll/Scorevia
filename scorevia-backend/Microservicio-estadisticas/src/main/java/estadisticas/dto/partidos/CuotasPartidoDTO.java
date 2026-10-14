package estadisticas.dto.partidos;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * Las cuotas de una casa de apuestas para un partido.
 *
 * <p>Se devuelven como una lista de mercados con sus opciones, y no con un campo por cuota
 * ({@code local}, {@code empate}, {@code visitante}...), para que añadir un mercado más sea
 * cosa de la configuración y no de cambiar el contrato y el frontend.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CuotasPartidoDTO {

    @Schema(description = "Id del partido", example = "1208021")
    private Integer idPartido;

    @Schema(description = "Casa de apuestas de la que salen estas cuotas", example = "Bet365")
    private String casa;

    @Schema(description = "Cuándo las actualizó la API por última vez", example = "2026-09-05T09:00:00+00:00")
    private String actualizado;

    /**
     * Falso cuando no hay cuotas que enseñar. Puede ser porque la competición no las cubre,
     * porque el partido queda lejos (se publican como pronto 14 días antes) o porque ya pasó
     * (la API guarda 7 días de histórico). El cliente lo necesita para distinguir "no hay"
     * de "todavía no han cargado".
     */
    @Schema(description = "false si no hay cuotas disponibles para este partido")
    private boolean hayCuotas;

    @Schema(description = "Mercados disponibles, en el orden en el que conviene pintarlos")
    private List<MercadoCuotasDTO> mercados;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MercadoCuotasDTO {

        @Schema(description = "Clave estable del mercado", example = "GANADOR")
        private String clave;

        @Schema(description = "Nombre para enseñar", example = "Ganador del partido")
        private String nombre;

        @Schema(description = "Opciones del mercado con su cuota")
        private List<OpcionCuotaDTO> opciones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpcionCuotaDTO {

        @Schema(description = "Etiqueta ya traducida", example = "Local")
        private String etiqueta;

        @Schema(description = "La cuota", example = "1.95")
        private String cuota;
    }
}
