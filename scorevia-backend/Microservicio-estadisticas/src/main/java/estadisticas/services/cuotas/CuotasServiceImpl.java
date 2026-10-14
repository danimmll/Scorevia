package estadisticas.services.cuotas;

import estadisticas.dto.external.ApiOddsResponseDTO;
import estadisticas.dto.partidos.CuotasPartidoDTO;
import estadisticas.entities.partidos.Partido;
import estadisticas.exception.NotFoundException;
import estadisticas.repositories.partidos.PartidoRepository;
import estadisticas.services.external.ApiFootballClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Cuotas de un partido, pedidas a API-Football en el momento en que alguien las mira.
 *
 * <p><b>Por qué bajo demanda y no ingeridas.</b> Un día de partidos son cientos de fixtures;
 * traerse las cuotas de todos por adelantado cuesta una petición por cada diez partidos y la
 * inmensa mayoría no las va a mirar nadie. Pedirlas al abrir la ficha cuesta una petición por
 * partido <b>visto</b>, y la caché hace que el segundo visitante no cueste nada.</p>
 *
 * <p><b>Cuándo ni se pregunta.</b> La documentación dice que las cuotas existen entre 14 y 1
 * días antes del partido y que se guardan 7 días de histórico. Fuera de esa ventana la
 * respuesta viene vacía, así que preguntar es tirar una petición: se corta antes.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuotasServiceImpl implements CuotasService {

    /** Nombres de mercado en la API, con su clave y su etiqueta para la pantalla. */
    private static final String MERCADO_GANADOR = "Match Winner";
    private static final String MERCADO_OVER_UNDER = "Goals Over/Under";
    private static final String MERCADO_AMBOS_MARCAN = "Both Teams Score";

    /** La línea de goles que se enseña del mercado Over/Under. Es la de referencia. */
    private static final String LINEA_GOLES = "2.5";

    private final PartidoRepository partidoRepository;
    private final ApiFootballClient apiFootballClient;

    /**
     * Casas por orden de preferencia. Se coge la primera que traiga alguno de los mercados que
     * se enseñan; si no hay ninguna de la lista, la primera que venga con datos. Va por nombre
     * y no por id porque los ids de casa no salen en la documentación de los ejemplos y el
     * nombre es estable.
     */
    @Value("${api.football.casas-preferidas:Bet365,Pinnacle,1xBet,Marathonbet,William Hill}")
    private String casasPreferidas;

    /** Días hacia atrás durante los que la API conserva las cuotas de un partido jugado. */
    @Value("${api.football.dias-historico-cuotas:7}")
    private int diasHistoricoCuotas;

    /** Días hacia delante a partir de los cuales todavía no hay cuotas publicadas. */
    @Value("${api.football.dias-antelacion-cuotas:14}")
    private int diasAntelacionCuotas;

    @Override
    @Cacheable(value = "cuotasPartido", key = "#idPartido")
    public CuotasPartidoDTO getCuotasPartido(Integer idPartido) {
        Partido partido = partidoRepository.findById(idPartido)
                .orElseThrow(() -> new NotFoundException("Partido no encontrado", "partido-no-encontrado"));

        if (!estaEnVentanaDeCuotas(partido)) {
            log.debug("Partido {} fuera de la ventana de cuotas: no se pregunta a la API.", idPartido);
            return sinCuotas(idPartido);
        }

        try {
            ApiOddsResponseDTO respuesta = apiFootballClient.getOddsByFixture(idPartido);
            return mapear(idPartido, respuesta);
        } catch (Exception e) {
            // Las cuotas son un extra de la ficha: si la API falla, la pantalla se pinta igual
            // sin ellas en vez de romperse entera.
            log.warn("No se pudieron traer las cuotas del partido {}: {}", idPartido, e.getMessage());
            return sinCuotas(idPartido);
        }
    }

    /**
     * ¿Tiene sentido preguntar por las cuotas de este partido?
     *
     * <p>Sin fecha se pregunta igual: es mejor gastar una petición que esconder un dato que sí
     * podría estar.</p>
     */
    private boolean estaEnVentanaDeCuotas(Partido partido) {
        if (partido.getFecha() == null) {
            return true;
        }
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("UTC"));
        return partido.getFecha().isAfter(ahora.minusDays(diasHistoricoCuotas))
                && partido.getFecha().isBefore(ahora.plusDays(diasAntelacionCuotas));
    }

    private CuotasPartidoDTO sinCuotas(Integer idPartido) {
        return CuotasPartidoDTO.builder()
                .idPartido(idPartido)
                .hayCuotas(false)
                .mercados(List.of())
                .build();
    }

    private CuotasPartidoDTO mapear(Integer idPartido, ApiOddsResponseDTO respuesta) {
        if (respuesta == null || respuesta.getResponse() == null || respuesta.getResponse().isEmpty()) {
            return sinCuotas(idPartido);
        }

        ApiOddsResponseDTO.OddsFixtureDTO entrada = respuesta.getResponse().get(0);
        Optional<ApiOddsResponseDTO.BookmakerDTO> casa = elegirCasa(entrada.getBookmakers());
        if (casa.isEmpty()) {
            return sinCuotas(idPartido);
        }

        List<CuotasPartidoDTO.MercadoCuotasDTO> mercados = new ArrayList<>();
        Map<String, ApiOddsResponseDTO.BetDTO> porNombre = indexarMercados(casa.get().getBets());

        anadirMercado(mercados, porNombre.get(MERCADO_GANADOR.toLowerCase(Locale.ROOT)),
                "GANADOR", "Ganador del partido",
                Map.of("home", "Local", "draw", "Empate", "away", "Visitante"), null);

        anadirMercado(mercados, porNombre.get(MERCADO_OVER_UNDER.toLowerCase(Locale.ROOT)),
                "GOLES", "Más o menos de " + LINEA_GOLES + " goles",
                Map.of("over " + LINEA_GOLES, "Más de " + LINEA_GOLES,
                        "under " + LINEA_GOLES, "Menos de " + LINEA_GOLES),
                // El mercado trae todas las líneas (0.5, 1.5, 2.5...); solo interesa una, y
                // sin este filtro saldrían veinte opciones en pantalla.
                valor -> valor.toLowerCase(Locale.ROOT).endsWith(LINEA_GOLES));

        anadirMercado(mercados, porNombre.get(MERCADO_AMBOS_MARCAN.toLowerCase(Locale.ROOT)),
                "AMBOS_MARCAN", "Ambos equipos marcan",
                Map.of("yes", "Sí", "no", "No"), null);

        if (mercados.isEmpty()) {
            return sinCuotas(idPartido);
        }

        return CuotasPartidoDTO.builder()
                .idPartido(idPartido)
                .casa(casa.get().getName())
                .actualizado(entrada.getUpdate())
                .hayCuotas(true)
                .mercados(mercados)
                .build();
    }

    private Map<String, ApiOddsResponseDTO.BetDTO> indexarMercados(List<ApiOddsResponseDTO.BetDTO> bets) {
        Map<String, ApiOddsResponseDTO.BetDTO> porNombre = new LinkedHashMap<>();
        if (bets == null) {
            return porNombre;
        }
        for (ApiOddsResponseDTO.BetDTO bet : bets) {
            if (bet.getName() != null) {
                porNombre.putIfAbsent(bet.getName().toLowerCase(Locale.ROOT), bet);
            }
        }
        return porNombre;
    }

    /**
     * Pasa un mercado de la API a uno de los nuestros, traduciendo las etiquetas.
     *
     * @param etiquetas    valor de la API (en minúsculas) -> etiqueta que se enseña. Lo que no
     *                     esté en el mapa se enseña tal cual viene.
     * @param filtroValor  para quedarse solo con parte de las opciones (el Over/Under trae
     *                     todas las líneas); null para no filtrar.
     */
    private void anadirMercado(List<CuotasPartidoDTO.MercadoCuotasDTO> destino,
                               ApiOddsResponseDTO.BetDTO bet,
                               String clave, String nombre,
                               Map<String, String> etiquetas,
                               java.util.function.Predicate<String> filtroValor) {
        if (bet == null || bet.getValues() == null || bet.getValues().isEmpty()) {
            return;
        }

        List<CuotasPartidoDTO.OpcionCuotaDTO> opciones = bet.getValues().stream()
                .filter(v -> v.getValue() != null && v.getOdd() != null)
                .filter(v -> filtroValor == null || filtroValor.test(v.getValue()))
                .map(v -> CuotasPartidoDTO.OpcionCuotaDTO.builder()
                        .etiqueta(etiquetas.getOrDefault(v.getValue().toLowerCase(Locale.ROOT), v.getValue()))
                        .cuota(v.getOdd())
                        .build())
                .toList();

        if (!opciones.isEmpty()) {
            destino.add(CuotasPartidoDTO.MercadoCuotasDTO.builder()
                    .clave(clave)
                    .nombre(nombre)
                    .opciones(opciones)
                    .build());
        }
    }

    /** La casa preferida que esté disponible; si ninguna, la primera que traiga mercados. */
    private Optional<ApiOddsResponseDTO.BookmakerDTO> elegirCasa(List<ApiOddsResponseDTO.BookmakerDTO> casas) {
        if (casas == null || casas.isEmpty()) {
            return Optional.empty();
        }

        List<String> preferidas = List.of(casasPreferidas.split(",")).stream()
                .map(String::trim)
                .map(c -> c.toLowerCase(Locale.ROOT))
                .toList();

        return casas.stream()
                .filter(c -> c.getBets() != null && !c.getBets().isEmpty())
                .min(Comparator.comparingInt(c -> {
                    int posicion = c.getName() == null ? -1 : preferidas.indexOf(c.getName().toLowerCase(Locale.ROOT));
                    // Las que no están en la lista van detrás de todas las que sí.
                    return posicion >= 0 ? posicion : preferidas.size();
                }));
    }
}
