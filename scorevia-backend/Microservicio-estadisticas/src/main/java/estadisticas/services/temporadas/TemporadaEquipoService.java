package estadisticas.services.temporadas;

import estadisticas.dto.equipos.TemporadaEquipoDTO;

import java.util.List;

/**
 * La temporada de un equipo, vista entera.
 *
 * @author Daniel
 * @version 1.0
 */
public interface TemporadaEquipoService {

    /** Las temporadas en las que el equipo jugo, de la mas reciente a la mas antigua. */
    List<Integer> temporadasDisponibles(Integer idEquipo);

    /**
     * Plantilla, entrenadores, competiciones, titulos y totales de esa temporada.
     *
     * <p>Todo de datos ya ingeridos: no cuesta ninguna peticion a API-Football.</p>
     */
    TemporadaEquipoDTO detalle(Integer idEquipo, Integer temporada);
}
