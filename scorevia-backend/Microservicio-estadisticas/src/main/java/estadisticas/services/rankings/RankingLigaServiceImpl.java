package estadisticas.services.rankings;

import com.fasterxml.jackson.databind.JsonNode;
import estadisticas.dto.ligas.RankingJugadorDTO;
import estadisticas.services.external.ApiFootballClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Goleadores, asistentes y tarjetas de una competición.
 *
 * <p>Los cuatro endpoints de la API devuelven la misma forma —una lista de jugadores, cada uno
 * con un bloque {@code statistics} de la competición pedida—, así que el mapeo es común y lo
 * único que cambia es de qué campo sale el número que ordena la tabla.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingLigaServiceImpl implements RankingLigaService {

    private static final String RESPONSE = "response";

    private final ApiFootballClient apiFootballClient;

    @Override
    @Cacheable(value = "rankingsLiga", key = "#idLiga + '-' + #temporada + '-' + #tipo")
    public List<RankingJugadorDTO> getRanking(Integer idLiga, Integer temporada, Tipo tipo) {
        try {
            JsonNode respuesta = pedir(idLiga, temporada, tipo);
            JsonNode jugadores = respuesta == null ? null : respuesta.get(RESPONSE);
            if (jugadores == null || !jugadores.isArray()) {
                return List.of();
            }

            List<RankingJugadorDTO> filas = new ArrayList<>();
            int puesto = 1;
            for (JsonNode nodo : jugadores) {
                RankingJugadorDTO fila = aFila(nodo, puesto, tipo);
                if (fila != null) {
                    filas.add(fila);
                    puesto++;
                }
            }
            return filas;
        } catch (Exception e) {
            // Es una sección de la pantalla de la liga, no la pantalla: si la API no da esta
            // estadística (o falla), se enseña la clasificación igual y esta tabla vacía.
            log.warn("No se pudo traer el ranking {} de la liga {} temporada {}: {}",
                    tipo, idLiga, temporada, e.getMessage());
            return List.of();
        }
    }

    private JsonNode pedir(Integer idLiga, Integer temporada, Tipo tipo) {
        return switch (tipo) {
            case GOLES -> apiFootballClient.getTopScorers(idLiga, temporada);
            case ASISTENCIAS -> apiFootballClient.getTopAssists(idLiga, temporada);
            case AMARILLAS -> apiFootballClient.getTopYellowCards(idLiga, temporada);
            case ROJAS -> apiFootballClient.getTopRedCards(idLiga, temporada);
        };
    }

    private RankingJugadorDTO aFila(JsonNode nodo, int puesto, Tipo tipo) {
        JsonNode jugador = nodo.path("player");
        JsonNode stats = nodo.path("statistics");
        if (jugador.isMissingNode() || !stats.isArray() || stats.isEmpty()) {
            return null;
        }

        // El array trae una entrada por equipo cuando el jugador cambió de club a mitad de
        // temporada. La primera es la del equipo con el que está fichado ahora, que es la que
        // corresponde a la competición consultada.
        JsonNode s = stats.get(0);
        JsonNode equipo = s.path("team");
        JsonNode games = s.path("games");

        return RankingJugadorDTO.builder()
                .puesto(puesto)
                .idJugador(jugador.path("id").isMissingNode() ? null : jugador.path("id").asInt())
                .nombre(jugador.path("name").asText(null))
                .foto(jugador.path("photo").asText(null))
                .idEquipo(equipo.path("id").isMissingNode() ? null : equipo.path("id").asInt())
                .nombreEquipo(equipo.path("name").asText(null))
                .escudoEquipo(equipo.path("logo").asText(null))
                .valor(valorDe(s, tipo))
                .partidos(games.path("appearences").isMissingNode() ? null : games.path("appearences").asInt())
                .minutos(games.path("minutes").isMissingNode() ? null : games.path("minutes").asInt())
                .build();
    }

    private Integer valorDe(JsonNode stats, Tipo tipo) {
        JsonNode nodo = switch (tipo) {
            case GOLES -> stats.path("goals").path("total");
            case ASISTENCIAS -> stats.path("goals").path("assists");
            case AMARILLAS -> stats.path("cards").path("yellow");
            case ROJAS -> stats.path("cards").path("red");
        };
        return nodo.isMissingNode() || nodo.isNull() ? null : nodo.asInt();
    }
}
