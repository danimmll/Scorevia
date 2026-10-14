package estadisticas.dto.ligas;

import com.fasterxml.jackson.annotation.JsonInclude;
import estadisticas.dto.equipos.EquipoDTO;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClasificacionDTO {
    private Integer posicion;
    private Integer puntos;
    private Integer partidosJugados;
    private Integer partidosGanados;
    private Integer partidosEmpatados;
    private Integer partidosPerdidos;
    private Integer golesFavor;
    private Integer golesContra;
    private Integer diferenciaGoles;
    private String estadoCompeticion;

    /**
     * Grupo dentro de la competición ("Group A"...). Null en una liga de tabla única.
     *
     * <p>El cliente lo necesita para pintar una tabla por grupo: en una competición con
     * fase de grupos, todas las filas tienen posiciones del 1 al 4 y sin esto se ven como
     * una única clasificación con doce equipos empatados en el primer puesto.</p>
     */
    private String grupo;

    private EquipoDTO equipo;
    private String logoEquipo;

    // "V"/"E"/"D" de los últimos partidos finalizados, más antiguo primero (el más reciente
    // queda el último del array). No viene del mapper -- ClasificacionServiceImpl lo rellena
    // aparte tras mapear, calculado sobre la marcha desde Partido.
    private List<String> forma;
}