package estadisticas.services.rankings;

import estadisticas.dto.ligas.RankingJugadorDTO;

import java.util.List;

/**
 * Los rankings de jugadores de una competición: goleadores, asistentes y tarjetas.
 *
 * @author Daniel
 * @version 1.0
 */
public interface RankingLigaService {

    /** Qué se puede rankear. Cada uno es un endpoint distinto de API-Football. */
    enum Tipo { GOLES, ASISTENCIAS, AMARILLAS, ROJAS }

    /**
     * El top de una liga y temporada, tal cual lo publica API-Football.
     *
     * <p>No se ingiere a una tabla propia a propósito: es un agregado que la API ya calcula y
     * que se mueve como mucho una vez por jornada, así que sale más barato pedirlo cuando
     * alguien lo mira y cachearlo unas horas que mantener una tabla al día. Cuesta <b>una</b>
     * petición por liga, temporada y tipo.</p>
     *
     * <p>Devuelve lista vacía —nunca error— si la competición no cubre esa estadística o si la
     * API falla: es una sección de la pantalla, no la pantalla.</p>
     */
    List<RankingJugadorDTO> getRanking(Integer idLiga, Integer temporada, Tipo tipo);
}
