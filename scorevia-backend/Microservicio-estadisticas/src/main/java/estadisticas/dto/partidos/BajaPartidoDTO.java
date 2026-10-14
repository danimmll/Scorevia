package estadisticas.dto.partidos;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Un jugador que se pierde un partido, o que es duda.
 *
 * <p>Es dato del partido, no del jugador: sale de {@code /injuries}, que responde "quién falta
 * en este encuentro", y no de {@code /sidelined}, que es el historial de lesiones de una
 * carrera y ya vive en {@code historial_bajas}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BajaPartidoDTO {

    @Schema(description = "Id del jugador, para enlazar con su ficha", example = "45398")
    private Integer idJugador;

    @Schema(description = "Nombre del jugador", example = "M. Banjac")
    private String nombre;

    @Schema(description = "Foto del jugador")
    private String foto;

    @Schema(description = "Id del equipo al que pertenece", example = "1079")
    private Integer idEquipo;

    @Schema(description = "Nombre del equipo", example = "Krylia Sovetov")
    private String nombreEquipo;

    /** true si es baja segura; false si es duda ("Questionable" en la API). */
    @Schema(description = "true si no juega seguro, false si es duda", example = "true")
    private boolean baja;

    @Schema(description = "Motivo tal y como lo da la API", example = "Muscle Injury")
    private String motivo;
}
