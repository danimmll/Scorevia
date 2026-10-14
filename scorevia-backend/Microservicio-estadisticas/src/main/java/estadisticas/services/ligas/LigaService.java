package estadisticas.services.ligas;

import estadisticas.dto.ligas.LigaDTO;
import estadisticas.dto.ligas.TemporadaLigaDTO;
import java.util.List;

/**
 * Servicio para la consulta y gestión de Ligas y Competiciones.
 *
 * @author Daniel
 * @version 1.0
 */
public interface LigaService {

    /**
     * Recupera todas las ligas almacenadas en el sistema.
     * @return Lista de LigaDTO.
     */
    List<LigaDTO> obtenerTodasLigas();

    /**
     * Busca una liga específica por su identificador de la API externa.
     * @param idLiga Identificador único de la liga.
     * @return LigaDTO con los detalles de la competición.
     */
    LigaDTO obtenerLigaPorId(Integer idLiga);

    /**
     * Temporadas de las que hay clasificación para una liga, de la más reciente a la más
     * antigua, marcando cuáles tienen partidos jugados.
     *
     * @param idLiga Identificador único de la liga.
     * @return Lista de TemporadaLigaDTO; vacía si la liga no tiene ninguna clasificación.
     */
    List<TemporadaLigaDTO> obtenerTemporadasDisponibles(Integer idLiga);
}