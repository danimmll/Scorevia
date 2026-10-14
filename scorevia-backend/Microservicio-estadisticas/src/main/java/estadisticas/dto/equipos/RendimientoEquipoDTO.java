package estadisticas.dto.equipos;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * Cómo le va a un equipo en una competición y temporada.
 *
 * <p>Sale de {@code /teams/statistics}, que API-Football ya tiene calculado: racha, partidos
 * en casa y fuera, porterías a cero, en qué minutos marca y encaja, mayores goleadas y
 * tarjetas. Es un agregado suyo, así que no se ingiere a ninguna tabla nuestra: se pide
 * cuando alguien abre la ficha y se cachea (ellos lo refrescan dos veces al día).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RendimientoEquipoDTO {

    @Schema(description = "Id del equipo", example = "529")
    private Integer idEquipo;

    @Schema(description = "Id de la competición", example = "140")
    private Integer idLiga;

    @Schema(description = "Temporada", example = "2025")
    private Integer temporada;

    /**
     * Falso cuando la API no tiene nada de este equipo en esa competición. El cliente lo
     * necesita para distinguir "no hay datos" de "todavía cargando".
     */
    @Schema(description = "false si la API no tiene estadísticas de este equipo aquí")
    private boolean hayDatos;

    /**
     * Últimos resultados, del más antiguo al más reciente: "WWDWL". Viene así de la API, con
     * las letras en inglés; el cliente las traduce para pintarlas.
     */
    @Schema(description = "Racha de resultados, letra por partido", example = "WWDWL")
    private String forma;

    @Schema(description = "Partidos jugados, ganados, empatados y perdidos")
    private BalanceDTO balance;

    @Schema(description = "Goles a favor y en contra, con su reparto por franja de minutos")
    private GolesDTO goles;

    @Schema(description = "Partidos sin encajar", example = "15")
    private Integer porteriasACero;

    @Schema(description = "Partidos sin marcar", example = "4")
    private Integer partidosSinMarcar;

    @Schema(description = "Mayor victoria en casa", example = "6-0")
    private String mayorVictoriaCasa;

    @Schema(description = "Mayor victoria fuera", example = "0-3")
    private String mayorVictoriaFuera;

    @Schema(description = "Mayor derrota en casa", example = "0-4")
    private String mayorDerrotaCasa;

    @Schema(description = "Mayor derrota fuera", example = "5-1")
    private String mayorDerrotaFuera;

    @Schema(description = "Tarjetas amarillas en la temporada", example = "78")
    private Integer tarjetasAmarillas;

    @Schema(description = "Tarjetas rojas en la temporada", example = "3")
    private Integer tarjetasRojas;

    /** Jugados/ganados/empatados/perdidos, separando casa y fuera. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BalanceDTO {
        private Integer jugados;
        private Integer ganados;
        private Integer empatados;
        private Integer perdidos;
        private Integer jugadosCasa;
        private Integer ganadosCasa;
        private Integer jugadosFuera;
        private Integer ganadosFuera;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GolesDTO {
        @Schema(description = "Goles marcados", example = "102")
        private Integer aFavor;

        @Schema(description = "Goles encajados", example = "39")
        private Integer enContra;

        @Schema(description = "Media de goles marcados por partido", example = "2.7")
        private String mediaAFavor;

        @Schema(description = "Media de goles encajados por partido", example = "1.0")
        private String mediaEnContra;

        /** Las ocho franjas que da la API: 0-15, 16-30... hasta 106-120. */
        @Schema(description = "Reparto de goles marcados por franja de minutos")
        private List<FranjaMinutosDTO> marcadosPorMinuto;

        @Schema(description = "Reparto de goles encajados por franja de minutos")
        private List<FranjaMinutosDTO> encajadosPorMinuto;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FranjaMinutosDTO {
        @Schema(description = "Franja de minutos", example = "0-15")
        private String franja;

        @Schema(description = "Goles en esa franja", example = "16")
        private Integer goles;

        @Schema(description = "Porcentaje sobre el total, tal cual lo da la API", example = "17.02%")
        private String porcentaje;
    }
}
