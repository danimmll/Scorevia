package estadisticas.services.partidos;

import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import estadisticas.dto.internal.EventoPartidoInternoDTO;
import estadisticas.dto.partidos.EventoPartidoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import estadisticas.dto.partidos.PartidoDTO;

import java.util.List;

/**
 * Servicio de consulta de partidos. De momento solo lectura: los partidos los
 * crea el motor de ingesta (Fase 1), no se crean a mano via API.
 */

public interface PartidoService {
    Page<PartidoDTO> getPartidos(Pageable pageable);
    List<PartidoDTO> getPartidosEnVivo();
    PartidoDTO getPartidoById(Integer id);
    Page<PartidoDTO> getPartidosByLiga(Integer idLiga, Integer temporada, Pageable pageable);
    /** Partidos de un día concreto (de 00:00 a 23:59:59). */
    Page<PartidoDTO> getPartidosPorFecha(java.time.LocalDate fecha, Pageable pageable);

    Page<PartidoDTO> getPartidosByEquipo(Integer idEquipo, Pageable pageable);

    /** Partidos del equipo con marcador ya registrado. */
    Page<PartidoDTO> getPartidosJugadosByEquipo(Integer idEquipo, Pageable pageable);

    /** Partidos del equipo todavía sin jugar. */
    Page<PartidoDTO> getPartidosProgramadosByEquipo(Integer idEquipo, Pageable pageable);
    Page<PartidoDTO> getEnfrentamientosDirectos(Integer idEquipoA, Integer idEquipoB, Pageable pageable);
    List<EstadisticaEquipoDTO> getEstadisticasPartido(Integer idPartido);
    List<EstadisticaJugadorPartidoDTO> getEstadisticasJugadoresPartido(Integer idPartido);

    List<EventoPartidoDTO> getEventosPartido(Integer idPartido);

    // Forma "interna" (con id de equipo, no nombre) para el motor de predicciones vía Feign.
    List<EventoPartidoInternoDTO> getEventosPartidoInterno(Integer idPartido);
}