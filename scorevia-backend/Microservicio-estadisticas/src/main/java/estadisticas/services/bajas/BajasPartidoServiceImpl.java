package estadisticas.services.bajas;

import estadisticas.dto.external.ApiInjuriesResponseDTO;
import estadisticas.dto.partidos.BajaPartidoDTO;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Las bajas de un partido: quién no juega y quién es duda.
 *
 * <p>Igual que las cuotas, va bajo demanda y cacheado. Traerlas de antemano para todos los
 * partidos del día sería pagar por lo que casi nadie mira, y aquí el dato además caduca solo:
 * pasado el partido ya no le interesa a nadie.</p>
 *
 * <p>Solo tiene sentido en partidos que aún no se han jugado o acaban de jugarse. Para uno de
 * hace tres años la API responde vacío, así que ni se pregunta.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BajasPartidoServiceImpl implements BajasPartidoService {

    /** Lo que la API llama a una baja segura; lo demás ("Questionable") es duda. */
    private static final String TIPO_BAJA_SEGURA = "missing fixture";

    private final PartidoRepository partidoRepository;
    private final ApiFootballClient apiFootballClient;

    /** Días antes y después del partido en los que se pregunta por las bajas. */
    @Value("${api.football.dias-ventana-bajas:30}")
    private int diasVentanaBajas;

    @Override
    @Cacheable(value = "bajasPartido", key = "#idPartido")
    public List<BajaPartidoDTO> getBajasPartido(Integer idPartido) {
        Partido partido = partidoRepository.findById(idPartido)
                .orElseThrow(() -> new NotFoundException("Partido no encontrado", "partido-no-encontrado"));

        if (!mereceLaPenaPreguntar(partido)) {
            return List.of();
        }

        try {
            ApiInjuriesResponseDTO respuesta = apiFootballClient.getInjuriesByFixtures(List.of(idPartido));
            if (respuesta == null || respuesta.getResponse() == null) {
                return List.of();
            }
            return respuesta.getResponse().stream()
                    .filter(item -> item.getPlayer() != null)
                    .map(this::aDTO)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            // Las bajas son un extra de la ficha: si fallan, la pantalla se pinta sin ellas.
            log.warn("No se pudieron traer las bajas del partido {}: {}", idPartido, e.getMessage());
            return List.of();
        }
    }

    /**
     * La API solo tiene bajas desde abril de 2021 y solo mientras el partido es actualidad.
     * Sin fecha se pregunta igual, que es mejor que esconder un dato que podría estar.
     */
    private boolean mereceLaPenaPreguntar(Partido partido) {
        if (partido.getFecha() == null) {
            return true;
        }
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("UTC"));
        return partido.getFecha().isAfter(ahora.minusDays(diasVentanaBajas))
                && partido.getFecha().isBefore(ahora.plusDays(diasVentanaBajas));
    }

    private BajaPartidoDTO aDTO(ApiInjuriesResponseDTO.InjuryItemDTO item) {
        ApiInjuriesResponseDTO.InjuryPlayerDTO jugador = item.getPlayer();
        String tipo = jugador.getType() == null ? "" : jugador.getType().toLowerCase(Locale.ROOT);

        return BajaPartidoDTO.builder()
                .idJugador(jugador.getId())
                .nombre(jugador.getName())
                .foto(jugador.getPhoto())
                .idEquipo(item.getTeam() != null ? item.getTeam().getId() : null)
                .nombreEquipo(item.getTeam() != null ? item.getTeam().getName() : null)
                .baja(tipo.contains(TIPO_BAJA_SEGURA))
                .motivo(jugador.getReason())
                .build();
    }
}
