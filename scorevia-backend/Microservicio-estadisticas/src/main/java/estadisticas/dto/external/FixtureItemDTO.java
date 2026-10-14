package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Un partido individual dentro del array "response" de API-Football.
 *
 * <p>Los cuatro primeros campos vienen en cualquier respuesta de {@code /fixtures}. Los de
 * abajo <b>solo</b> cuando se pide por id ({@code /fixtures?id=} o {@code /fixtures?ids=},
 * hasta 20 de golpe) o, en el caso de {@code events}, también en el feed en vivo
 * ({@code /fixtures?live=all}).</p>
 *
 * <p>Esto último cambia la cuenta de la cuota por completo, y es la razón de que estos campos
 * existan: el timeline de un partido en directo ya venía en la respuesta que se pedía de todas
 * formas, y se estaba pidiendo otra vez aparte. Y un partido acabado necesitaba tres
 * peticiones (estadísticas de equipo, de jugador y eventos) que ahora son una sola compartida
 * entre veinte partidos.</p>
 *
 * @author Daniel
 * @version 2.0
 */
@Data
public class FixtureItemDTO {

    private FixtureDetailDTO fixture;
    private LeagueInfoDTO league;
    private TeamsInfoDTO teams;
    private GoalsInfoDTO goals;

    /** Marcador desglosado: descanso, tiempo reglamentario, prórroga y penaltis. */
    private ScoreDTO score;

    // WRITE_ONLY en los cuatro bloques pesados: se leen de la API, pero no se vuelven a
    // escribir al serializar. El fixture se guarda entero en partidos.datos_api (JSONB) como
    // caché del original, y meter ahí el timeline, las alineaciones y las dos tandas de
    // estadísticas de cada partido multiplicaría por diez esa columna para guardar por
    // segunda vez lo que ya está en sus propias tablas.

    /** Timeline. Viene gratis en {@code live=all} y al pedir el partido por id. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<ApiFootballEventsResponseDTO.EventItemDTO> events;

    /** Alineaciones oficiales, con dibujo, entrenador y sitio de cada jugador en el campo. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<LineupDTO> lineups;

    /**
     * Estadísticas de equipo. Mismo formato exacto que devuelve {@code /fixtures/statistics},
     * así que se reaprovecha el mismo DTO y el mismo mapeo.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<ApiTeamStatsResponseDTO.TeamStatsWrapper> statistics;

    /** Estadísticas de jugador, con el mismo formato que {@code /fixtures/players}. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private List<ApiPlayerStatsResponseDTO.TeamPlayersWrapper> players;
}
