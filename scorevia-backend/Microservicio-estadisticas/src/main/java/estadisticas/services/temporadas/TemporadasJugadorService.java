package estadisticas.services.temporadas;

import estadisticas.dto.jugadores.TemporadaJugadorDTO;

import java.util.List;

/**
 * El historial de un jugador temporada a temporada.
 *
 * @author Daniel
 * @version 1.0
 */
public interface TemporadasJugadorService {

    /**
     * Lo que hizo el jugador en cada temporada y competicion, de la mas reciente a la mas
     * antigua.
     *
     * <p>Sale de agregar sus partidos, asi que no cuesta ninguna peticion a API-Football. Un
     * jugador que cambio de club a mitad de temporada aparece con una fila por etapa.</p>
     */
    List<TemporadaJugadorDTO> porTemporadas(Integer idJugador);
}
