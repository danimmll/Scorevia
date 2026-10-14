package estadisticas.services.clasificacion;

import estadisticas.dto.ligas.ClasificacionDTO;
import java.util.List;

/**
 * Servicio para consultar las tablas de clasificación de las ligas.
 *
 * @author Daniel
 * @version 1.0
 */
public interface ClasificacionService {

    /**
     * Recupera la tabla de clasificación completa de una liga para una temporada específica,
     * ordenada por posición.
     *
     * @param idLiga ID de la liga.
     * @param temporada Año de la temporada (ej: 2026).
     * @return Lista de ClasificacionDTO ordenada por posición.
     */
    List<ClasificacionDTO> obtenerClasificacion(Integer idLiga, Integer temporada);
}