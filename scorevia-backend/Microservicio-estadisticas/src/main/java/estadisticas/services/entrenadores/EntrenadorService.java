package estadisticas.services.entrenadores;

import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.dto.entrenadores.HistorialEntrenadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;

import java.util.List;

/**
 * Servicio para la gestión de Entrenadores y su palmarés.
 *
 * @author Daniel
 * @version 1.0
 */
public interface EntrenadorService {

    /**
     * Obtiene el perfil detallado de un entrenador.
     * @param idEntrenador ID del entrenador.
     * @return DTO con los datos del entrenador.
     */
    EntrenadorDTO obtenerEntrenadorPorId(Integer idEntrenador);

    /**
     * Obtiene el entrenador actual de un equipo específico.
     * @param idEquipo ID del equipo.
     * @return DTO del entrenador, o null si el equipo no tiene entrenador asignado.
     */
    EntrenadorDTO obtenerEntrenadorPorEquipo(Integer idEquipo);

    /**
     * Recupera el palmarés (trofeos) ganados por un entrenador a lo largo de su carrera.
     * @param idEntrenador ID del entrenador.
     * @return Lista de trofeos.
     */
    List<TrofeoDTO> obtenerTrofeosEntrenador(Integer idEntrenador);

    /**
     * Entrenadores de los equipos que participan en una liga concreta, en una temporada
     * concreta. Vía Clasificacion, mismo motivo que EquipoService.obtenerEquiposPorLiga.
     */
    List<EntrenadorDTO> obtenerEntrenadoresPorLiga(Integer idLiga, Integer temporada);

    /** Clubes que ha entrenado este técnico a lo largo de su carrera, más reciente primero. */
    List<HistorialEntrenadorDTO> obtenerHistorialClubes(Integer idEntrenador);

    /** Técnicos que han pasado por el banquillo de este equipo, más reciente primero. */
    List<HistorialEntrenadorDTO> obtenerHistorialPorEquipo(Integer idEquipo);

    /**
     * Busca técnicos por nombre (autocompletado).
     */
    List<EntrenadorDTO> buscarPorNombre(String nombre, int limite);
}