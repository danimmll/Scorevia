package estadisticas.services.equipos;

import estadisticas.dto.equipos.RendimientoEquipoDTO;

/**
 * Cómo le va a un equipo en una competición: racha, goles por minuto, porterías a cero.
 *
 * @author Daniel
 * @version 1.0
 */
public interface RendimientoEquipoService {

    /**
     * El rendimiento de un equipo en una competición y temporada.
     *
     * <p>Se pide a API-Football en el momento y se cachea: es un agregado que ellos ya
     * calculan y refrescan dos veces al día, así que mantener una copia propia de 28.000
     * equipos costaría mucho más que pedir el del que se esté mirando. Cuesta <b>una
     * petición</b> por equipo, liga y temporada.</p>
     *
     * <p>Nunca lanza: si la API falla o no tiene datos, devuelve un objeto con
     * {@code hayDatos = false}. Es una sección de la ficha, no la ficha.</p>
     */
    RendimientoEquipoDTO getRendimiento(Integer idEquipo, Integer idLiga, Integer temporada);
}
