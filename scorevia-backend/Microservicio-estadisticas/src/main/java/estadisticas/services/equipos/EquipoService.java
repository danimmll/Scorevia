package estadisticas.services.equipos;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import java.util.List;

/**
 * Servicio para la consulta de Equipos de fútbol, incluyendo
 * su filtrado por competición, detalles de sus estadios,
 * y su historial de altas y bajas (traspasos).
 *
 * @author Daniel
 * @version 1.0
 */
public interface EquipoService {

    /**
     * Obtiene el perfil detallado de un equipo, incluyendo su estadio si lo tiene.
     * @param idEquipo ID del equipo.
     * @return EquipoDTO con los datos del club.
     */
    EquipoDTO obtenerEquipoPorId(Integer idEquipo);

    /**
     * Recupera todos los equipos que participan en una liga concreta, en una temporada
     * concreta.
     *
     * <p>Se resuelve vía Clasificacion (liga+equipo+temporada), no vía {@code Equipo.liga}:
     * ese campo es una FK única por equipo, así que si un mismo club participa en varias
     * competiciones (liga + Champions + copa nacional...) la última ingesta que corre para
     * "la temporada actual" se lo pisa a las demás. Clasificacion no tiene ese problema --
     * cada fila es su propia tupla (liga, equipo, temporada), no un campo mutable en el equipo.</p>
     *
     * @param idLiga ID de la liga a consultar.
     * @param temporada temporada a consultar.
     * @return Lista de equipos de esa competición en esa temporada.
     */
    List<EquipoDTO> obtenerEquiposPorLiga(Integer idLiga, Integer temporada);

    /**
     * Recupera el historial completo de altas y bajas (traspasos) de un equipo.
     * @param idEquipo ID del equipo.
     * @return Lista de traspasos donde el equipo es origen o destino.
     */
    List<TraspasoDTO> obtenerTraspasosPorEquipo(Integer idEquipo);

    /**
     * Búsqueda global por nombre de equipo, sin restringir a la liga/país seleccionados en el
     * filtro actual.
     * @param nombre texto a buscar (contiene, sin distinguir mayúsculas).
     * @param limite máximo de resultados a devolver.
     */
    List<EquipoDTO> buscarPorNombre(String nombre, int limite);
}