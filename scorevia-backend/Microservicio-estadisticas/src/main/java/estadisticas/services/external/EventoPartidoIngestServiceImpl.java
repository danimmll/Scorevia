// estadisticas/services/external/EventoPartidoIngestServiceImpl.java
package estadisticas.services.external;

import estadisticas.dto.external.ApiFootballEventsResponseDTO;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.partidos.EventoPartido;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.partidos.EventoPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Servicio de ingesta de eventos de partido.
 * <p>
 * {@code ingestarEventosDePartido} se invoca tanto desde fuera (Feign/otros servicios)
 * como desde el propio backfill histórico de esta clase. Para que el {@code @Transactional}
 * se respete también en el segundo caso, el propio bean se inyecta a sí mismo de forma
 * perezosa ({@code self}) y las llamadas internas pasan por él en vez de por 'this',
 * evitando el problema clásico de auto-invocación que salta el proxy de Spring AOP.
 * </p>
 */
@Slf4j
@Service
public class EventoPartidoIngestServiceImpl implements EventoPartidoIngestService {

    private static final int TAMANO_LOTE = 500;
    private static final int MAX_INTENTOS_EVENTOS = 3;

    private final ApiFootballClient apiClient;
    private final EventoPartidoRepository eventoPartidoRepository;
    private final JugadorRepository jugadorRepository;
    private final PartidoRepository partidoRepository;
    private final EventoPartidoIngestService self;

    public EventoPartidoIngestServiceImpl(ApiFootballClient apiClient,
                                          EventoPartidoRepository eventoPartidoRepository,
                                          JugadorRepository jugadorRepository,
                                          PartidoRepository partidoRepository,
                                          @Lazy EventoPartidoIngestService self) {
        this.apiClient = apiClient;
        this.eventoPartidoRepository = eventoPartidoRepository;
        this.jugadorRepository = jugadorRepository;
        this.partidoRepository = partidoRepository;
        this.self = self;
    }

    @Override
    @Transactional
    public void guardarEventos(Partido partido, List<ApiFootballEventsResponseDTO.EventItemDTO> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            registrarIntentoSinEventos(partido);
            return;
        }
        registrarEventosEncontrados(partido);
        procesarEventos(partido, eventos);
    }

    /**
     * Igual que {@link #guardarEventos}, pero <b>sin</b> escribir el partido: deja las banderas
     * puestas en el objeto y que las guarde quien llame.
     *
     * <p>Existe por el bloqueo optimista. {@code Partido} lleva {@code @Version} y aquí llega
     * siempre desasociado de la sesión, así que cada {@code save} es un merge que sube la
     * versión en la base sin actualizar el objeto que tenemos en la mano. Con dos guardados
     * seguidos del mismo partido —uno por los eventos y otro por las estadísticas— el segundo
     * iba con la versión vieja y saltaba {@code OptimisticLockingFailureException}: la ingesta
     * se daba por fallida <b>después</b> de haber guardado bien los datos, y el partido acababa
     * contando un intento fallido que no lo era.</p>
     *
     * @return true si había timeline que guardar.
     */
    @Override
    @Transactional
    public boolean guardarEventosSinPersistirPartido(Partido partido,
                                                     List<ApiFootballEventsResponseDTO.EventItemDTO> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            int intentos = Optional.ofNullable(partido.getIntentosEventos()).orElse(0) + 1;
            partido.setIntentosEventos(intentos);
            if (intentos >= MAX_INTENTOS_EVENTOS) {
                partido.setEventosNotFound(true);
            }
            return false;
        }

        partido.setIntentosEventos(0);
        partido.setEventosNotFound(false);
        procesarEventos(partido, eventos);
        return true;
    }

    @Override
    @Transactional
    public void ingestarEventosDePartido(Partido partido) {
        try {
            // Esta versión sí gasta una petición. Solo tiene sentido para un partido suelto
            // que no venga acompañado de su timeline; cuando el timeline ya está en la mano
            // (feed en vivo, o /fixtures?ids=) hay que usar guardarEventos.
            ApiFootballEventsResponseDTO respuesta = apiClient.getEventosFixture(partido.getIdPartido());
            if (respuesta.getResponse() == null || respuesta.getResponse().isEmpty()) {
                registrarIntentoSinEventos(partido);
                return;
            }
            registrarEventosEncontrados(partido);
            procesarEventos(partido, respuesta.getResponse());
        } catch (RuntimeException e) {
            // Relanzamos a propósito: procesarEventos ya borró los eventos viejos antes de
            // reinsertar los nuevos, así que si algo falla a mitad (violación de constraint,
            // timeout de BD...) hay que deshacer también ese borrado. Antes se tragaba la
            // excepción aquí y la transacción se confirmaba igual, dejando el partido sin
            // ningún evento en vez de con los viejos.
            log.warn("No se pudieron descargar/procesar los eventos del partido {}: {}", partido.getIdPartido(), e.getMessage());
            throw e;
        }
    }

    // Mismo patrón que EstadisticasIngestServiceImpl.registrarIntentoFallido/statsNotFound:
    // antes se bloqueaba la ingesta de eventos de un partido para siempre al primer hueco
    // vacío en la API, aunque fuera transitorio (la API de eventos suele tardar unos minutos
    // en poblarse tras terminar un partido). Ahora hacen falta MAX_INTENTOS_EVENTOS respuestas
    // vacías consecutivas antes de bloquearlo.
    private void registrarIntentoSinEventos(Partido partido) {
        int intentos = Optional.ofNullable(partido.getIntentosEventos()).orElse(0) + 1;
        partido.setIntentosEventos(intentos);

        if (intentos >= MAX_INTENTOS_EVENTOS) {
            partido.setEventosNotFound(true);
            log.warn("Partido {} bloqueado tras {} intentos fallidos de ingesta de eventos",
                    partido.getIdPartido(), intentos);
        }

        partidoRepository.save(partido);
    }

    private void registrarEventosEncontrados(Partido partido) {
        partido.setIntentosEventos(0);
        partido.setEventosNotFound(false);
        partidoRepository.save(partido);
    }

    private void procesarEventos(Partido partido, List<ApiFootballEventsResponseDTO.EventItemDTO> eventosApi) {
        eventoPartidoRepository.deleteByPartido_IdPartido(partido.getIdPartido());

        List<EventoPartido> eventosParaGuardar = eventosApi.stream()
                .map(item -> mapearEvento(partido, item))
                .filter(Objects::nonNull)
                .toList();

        eventoPartidoRepository.saveAll(eventosParaGuardar);
    }

    private EventoPartido mapearEvento(Partido partido, ApiFootballEventsResponseDTO.EventItemDTO item) {
        if (item.getTeam() == null || item.getTime() == null) {
            return null;
        }

        Equipo equipo = resolverEquipoDelPartido(partido, item.getTeam().getId());
        if (equipo == null) {
            return null;
        }

        EventoPartido evento = new EventoPartido();
        evento.setPartido(partido);
        evento.setEquipo(equipo);
        evento.setTiempoMinuto(item.getTime().getElapsed());
        evento.setTiempoExtra(item.getTime().getExtra());
        evento.setTipoEvento(item.getType());
        evento.setDetalle(item.getDetail());

        if (item.getPlayer() != null && item.getPlayer().getId() != null) {
            jugadorRepository.findById(item.getPlayer().getId()).ifPresent(evento::setJugadorPrincipal);
        }
        if (item.getAssist() != null && item.getAssist().getId() != null) {
            jugadorRepository.findById(item.getAssist().getId()).ifPresent(evento::setJugadorSecundario);
        }

        return evento;
    }

    @Override
    public int ingestarEventosFaltantesHistoricos() {
        log.info("Iniciando backfill de eventos históricos...");

        Set<Integer> ignorados = new HashSet<>();
        int procesadosTotales = 0;
        int pagina = 0;
        boolean continuar = true;

        while (continuar && apiClient.hayCuotaDisponibleParaBackfill()) {
            Page<Partido> paginaResultado = obtenerSiguienteLote(pagina);
            List<Partido> pendientes = paginaResultado.getContent().stream()
                    .filter(p -> !ignorados.contains(p.getIdPartido()))
                    .toList();

            if (pendientes.isEmpty()) {
                // La página vino llena pero todos sus partidos ya se procesaron en esta
                // ronda. Puede haber más partidos pendientes en páginas posteriores que
                // todavía no se han consultado (la paginación fija en 0 los dejaría
                // varados), así que avanzamos de página en vez de dar la ronda por
                // finalizada.
                boolean paginaLlena = paginaResultado.getContent().size() == TAMANO_LOTE;
                if (paginaLlena) {
                    pagina++;
                } else {
                    log.info("¡Backfill al día! No hay más partidos pendientes de eventos en esta ronda.");
                }
                continuar = paginaLlena;
            } else {
                procesadosTotales += procesarLote(pendientes, ignorados);
            }
        }

        log.info("Ronda de backfill de eventos completada. Procesados: {}", procesadosTotales);
        return procesadosTotales;
    }

    private Page<Partido> obtenerSiguienteLote(int pagina) {
        Pageable limit = PageRequest.of(pagina, TAMANO_LOTE, Sort.by("fecha").descending());
        return partidoRepository.findPartidosConStatsSinEventos(limit);
    }

    private int procesarLote(List<Partido> pendientes, Set<Integer> ignorados) {
        int procesados = 0;
        for (Partido partido : pendientes) {
            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Cuota agotada en medio de un lote de eventos. Parando de forma segura.");
                break;
            }
            try {
                self.ingestarEventosDePartido(partido);
                procesados++;
            } catch (Exception e) {
                log.error("Error al ingestar eventos del partido {}: {}", partido.getIdPartido(), e.getMessage());
            } finally {
                ignorados.add(partido.getIdPartido());
            }
        }
        return procesados;
    }

    private Equipo resolverEquipoDelPartido(Partido partido, Integer idEquipo) {
        if (partido.getEquipoLocal() != null && partido.getEquipoLocal().getIdEquipo().equals(idEquipo)) {
            return partido.getEquipoLocal();
        } else if (partido.getEquipoVisitante() != null && partido.getEquipoVisitante().getIdEquipo().equals(idEquipo)) {
            return partido.getEquipoVisitante();
        }
        log.warn("El ID de equipo {} devuelto por la API no coincide con los del partido {}", idEquipo, partido.getIdPartido());
        return null;
    }
}