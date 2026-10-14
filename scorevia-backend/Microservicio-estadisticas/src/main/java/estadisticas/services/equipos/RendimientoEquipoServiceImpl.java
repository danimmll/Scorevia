package estadisticas.services.equipos;

import com.fasterxml.jackson.databind.JsonNode;
import estadisticas.dto.equipos.RendimientoEquipoDTO;
import estadisticas.services.external.ApiFootballClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * El rendimiento de un equipo en una competición: racha, goles por minuto, porterías a cero.
 *
 * <p>Igual que las cuotas, los rankings y las bajas: se pide a API-Football cuando alguien lo
 * mira y se cachea, en vez de ingerirlo a una tabla propia. Es un agregado que ellos ya
 * calculan y que refrescan dos veces al día; mantener una copia al día de 28.000 equipos
 * costaría muchísimo más que pedir el de quien se esté consultando.</p>
 *
 * <p>Cuesta <b>una petición</b> por equipo, competición y temporada.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RendimientoEquipoServiceImpl implements RendimientoEquipoService {

    private static final String RESPONSE = "response";

    private final ApiFootballClient apiFootballClient;

    @Override
    @Cacheable(value = "rendimientoEquipo", key = "#idEquipo + '-' + #idLiga + '-' + #temporada")
    public RendimientoEquipoDTO getRendimiento(Integer idEquipo, Integer idLiga, Integer temporada) {
        try {
            JsonNode raiz = apiFootballClient.getEstadisticasEquipoTemporada(idLiga, idEquipo, temporada);
            JsonNode datos = raiz == null ? null : raiz.get(RESPONSE);

            // Sin datos la API devuelve el objeto con todo a null, no un array vacío: se mira
            // que haya partidos jugados para decidir si hay algo que enseñar.
            if (datos == null || datos.isMissingNode()
                    || datos.path("fixtures").path("played").path("total").asInt(0) == 0) {
                return vacio(idEquipo, idLiga, temporada);
            }
            return mapear(idEquipo, idLiga, temporada, datos);
        } catch (Exception e) {
            // Es una sección de la ficha, no la ficha: si falla, el equipo se ve igual.
            log.warn("No se pudo traer el rendimiento del equipo {} en la liga {} temporada {}: {}",
                    idEquipo, idLiga, temporada, e.getMessage());
            return vacio(idEquipo, idLiga, temporada);
        }
    }

    private RendimientoEquipoDTO vacio(Integer idEquipo, Integer idLiga, Integer temporada) {
        return RendimientoEquipoDTO.builder()
                .idEquipo(idEquipo).idLiga(idLiga).temporada(temporada)
                .hayDatos(false)
                .build();
    }

    private RendimientoEquipoDTO mapear(Integer idEquipo, Integer idLiga, Integer temporada, JsonNode d) {
        JsonNode fixtures = d.path("fixtures");
        JsonNode goles = d.path("goals");
        JsonNode biggest = d.path("biggest");
        JsonNode cards = d.path("cards");

        return RendimientoEquipoDTO.builder()
                .idEquipo(idEquipo).idLiga(idLiga).temporada(temporada)
                .hayDatos(true)
                .forma(d.path("form").asText(null))
                .balance(RendimientoEquipoDTO.BalanceDTO.builder()
                        .jugados(entero(fixtures.path("played").path("total")))
                        .ganados(entero(fixtures.path("wins").path("total")))
                        .empatados(entero(fixtures.path("draws").path("total")))
                        .perdidos(entero(fixtures.path("loses").path("total")))
                        .jugadosCasa(entero(fixtures.path("played").path("home")))
                        .ganadosCasa(entero(fixtures.path("wins").path("home")))
                        .jugadosFuera(entero(fixtures.path("played").path("away")))
                        .ganadosFuera(entero(fixtures.path("wins").path("away")))
                        .build())
                .goles(RendimientoEquipoDTO.GolesDTO.builder()
                        .aFavor(entero(goles.path("for").path("total").path("total")))
                        .enContra(entero(goles.path("against").path("total").path("total")))
                        .mediaAFavor(goles.path("for").path("average").path("total").asText(null))
                        .mediaEnContra(goles.path("against").path("average").path("total").asText(null))
                        .marcadosPorMinuto(franjas(goles.path("for").path("minute")))
                        .encajadosPorMinuto(franjas(goles.path("against").path("minute")))
                        .build())
                .porteriasACero(entero(d.path("clean_sheet").path("total")))
                .partidosSinMarcar(entero(d.path("failed_to_score").path("total")))
                .mayorVictoriaCasa(biggest.path("wins").path("home").asText(null))
                .mayorVictoriaFuera(biggest.path("wins").path("away").asText(null))
                .mayorDerrotaCasa(biggest.path("loses").path("home").asText(null))
                .mayorDerrotaFuera(biggest.path("loses").path("away").asText(null))
                .tarjetasAmarillas(sumarTarjetas(cards.path("yellow")))
                .tarjetasRojas(sumarTarjetas(cards.path("red")))
                .build();
    }

    /**
     * Las ocho franjas de minutos, en el orden en que las manda la API.
     *
     * <p>Se recorren los campos del objeto en vez de escribir "0-15", "16-30"... a mano: así
     * el día que la API añada una franja (o cambie la de la prórroga) sale sola.</p>
     */
    private List<RendimientoEquipoDTO.FranjaMinutosDTO> franjas(JsonNode minuto) {
        List<RendimientoEquipoDTO.FranjaMinutosDTO> franjas = new ArrayList<>();
        if (minuto == null || !minuto.isObject()) {
            return franjas;
        }

        minuto.fields().forEachRemaining(entrada -> {
            JsonNode valor = entrada.getValue();
            franjas.add(RendimientoEquipoDTO.FranjaMinutosDTO.builder()
                    .franja(entrada.getKey())
                    // Una franja sin goles llega como null, no como 0.
                    .goles(valor.path("total").isNull() ? 0 : entero(valor.path("total")))
                    .porcentaje(valor.path("percentage").asText(null))
                    .build());
        });
        return franjas;
    }

    /** Las tarjetas vienen repartidas por franja de minutos; para el resumen basta el total. */
    private Integer sumarTarjetas(JsonNode porMinuto) {
        if (porMinuto == null || !porMinuto.isObject()) {
            return null;
        }
        int total = 0;
        var campos = porMinuto.fields();
        while (campos.hasNext()) {
            JsonNode valor = campos.next().getValue().path("total");
            if (!valor.isNull() && valor.isNumber()) {
                total += valor.asInt();
            }
        }
        return total;
    }

    private Integer entero(JsonNode nodo) {
        return nodo == null || nodo.isMissingNode() || nodo.isNull() ? null : nodo.asInt();
    }
}
