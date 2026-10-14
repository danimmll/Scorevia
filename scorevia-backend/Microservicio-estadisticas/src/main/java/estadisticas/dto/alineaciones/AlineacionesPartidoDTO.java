package estadisticas.dto.alineaciones;

import com.fasterxml.jackson.annotation.JsonInclude;
import estadisticas.dto.arbitros.ArbitroDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Quién saltó al campo en un partido: los dos onces, sus banquillos, los dos entrenadores y
 * el árbitro.
 *
 * <p>Todo sale de datos que ya estaban en la base —{@code estadisticas_jugador_partido} guarda
 * desde siempre {@code titular} y {@code posicion_campo}— así que esto no cuesta ni una
 * petición a API-Football, y funciona igual para un partido de hoy que para uno de 2010.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlineacionesPartidoDTO {

    @Schema(description = "Id del partido", example = "1208021")
    private Integer idPartido;

    @Schema(description = "Árbitro del partido, si consta")
    private ArbitroDTO arbitro;

    @Schema(description = "Alineación del equipo local")
    private EquipoAlineacionDTO local;

    @Schema(description = "Alineación del equipo visitante")
    private EquipoAlineacionDTO visitante;

    /**
     * Falso cuando el partido todavía no tiene estadísticas de jugadores (no se ha jugado, o
     * la ingesta aún no ha pasado por él). El cliente lo necesita para distinguir "no hay
     * alineación todavía" de "hay alineación y está vacía".
     */
    @Schema(description = "false si aún no consta ningún jugador para este partido")
    private Boolean hayDatos;
}
