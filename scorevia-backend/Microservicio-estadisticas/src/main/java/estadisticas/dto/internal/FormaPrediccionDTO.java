package estadisticas.dto.internal;

import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.dto.ligas.ClasificacionDTO;
import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.dto.partidos.PartidoDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormaPrediccionDTO {

    private PartidoDTO partido;
    private Long idPartido;
    private Long idLiga;
    private Integer temporada;
    private Long idEquipoLocal;
    private Long idEquipoVisitante;

    private String nombreArbitro;

    private ClasificacionDTO clasificacionLocal;
    private ClasificacionDTO clasificacionVisitante;

    // Historial global
    private List<PartidoDTO> ultimosPartidosLocal;
    private List<PartidoDTO> ultimosPartidosVisitante;

    // MODO DIOS: Historial Puro (Local en casa, Visitante fuera)
    private List<PartidoDTO> ultimosPartidosLocalComoLocal;
    private List<PartidoDTO> ultimosPartidosVisitanteComoVisitante;

    private List<PartidoDTO> historialH2H;

    private List<HistorialBajaDTO> bajasActivasLocal;
    private List<HistorialBajaDTO> bajasActivasVisitante;

    // xG y Tiros para la puntería
    private List<EstadisticaEquipoDTO> historicoStatsEquiposLocal;
    private List<EstadisticaEquipoDTO> historicoStatsEquiposVisitante;

    private List<ContextoJugadorPrediccionDTO> plantillaLocalConStats;
    private List<ContextoJugadorPrediccionDTO> plantillaVisitanteConStats;

    // Entrenadores y Árbitros
    private EntrenadorDTO entrenadorLocal;
    private EntrenadorDTO entrenadorVisitante;
    private ArbitroStatsDTO datosArbitro;
}