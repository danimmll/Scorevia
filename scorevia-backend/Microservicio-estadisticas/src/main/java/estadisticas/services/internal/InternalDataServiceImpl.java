package estadisticas.services.internal;

import estadisticas.dto.internal.ArbitroStatsDTO;
import estadisticas.dto.internal.ContextoJugadorPrediccionDTO;
import estadisticas.dto.internal.FormaPrediccionDTO;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;
import estadisticas.entities.partidos.Partido;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.entrenadores.EntrenadorMapper;
import estadisticas.mappers.jugadores.HistorialBajaMapper;
import estadisticas.mappers.jugadores.JugadorMapper;
import estadisticas.mappers.ligas.ClasificacionMapper;
import estadisticas.mappers.partidos.EstadisticaEquipoMapper;
import estadisticas.mappers.partidos.EstadisticaJugadorMapper;
import estadisticas.mappers.partidos.PartidoMapper;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.jugadores.HistorialBajaRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InternalDataServiceImpl implements InternalDataService {

    private final PartidoRepository partidoRepository;
    private final ClasificacionRepository clasificacionRepository;
    private final HistorialBajaRepository historialBajaRepository;
    private final JugadorRepository jugadorRepository;
    private final EstadisticaJugadorPartidoRepository estadisticaJugadorPartidoRepository;
    private final EstadisticaEquipoPartidoRepository estadisticaEquipoPartidoRepository;
    private final EntrenadorRepository entrenadorRepository;
    private final PartidoMapper partidoMapper;
    private final ClasificacionMapper clasificacionMapper;
    private final HistorialBajaMapper bajaMapper;
    private final JugadorMapper jugadorMapper;
    private final EstadisticaJugadorMapper estadisticaJugadorMapper;
    private final EstadisticaEquipoMapper estadisticaEquipoMapper;
    private final EntrenadorMapper entrenadorMapper;

    // FIX auto-invocación: @Cacheable en generarContexto() no se aplicaría si
    // generarContextoPorPartido() la llamara vía 'this' dentro de la misma clase,
    // saltándose el proxy de Spring AOP (mismo problema documentado y resuelto en
    // EventoPartidoIngestServiceImpl/ApiFootballClient). Se inyecta el propio bean de
    // forma perezosa para que la llamada interna pase por el proxy y sí se cachee.
    private final InternalDataService self;

    public InternalDataServiceImpl(PartidoRepository partidoRepository,
                                    ClasificacionRepository clasificacionRepository,
                                    HistorialBajaRepository historialBajaRepository,
                                    JugadorRepository jugadorRepository,
                                    EstadisticaJugadorPartidoRepository estadisticaJugadorPartidoRepository,
                                    EstadisticaEquipoPartidoRepository estadisticaEquipoPartidoRepository,
                                    EntrenadorRepository entrenadorRepository,
                                    PartidoMapper partidoMapper,
                                    ClasificacionMapper clasificacionMapper,
                                    HistorialBajaMapper bajaMapper,
                                    JugadorMapper jugadorMapper,
                                    EstadisticaJugadorMapper estadisticaJugadorMapper,
                                    EstadisticaEquipoMapper estadisticaEquipoMapper,
                                    EntrenadorMapper entrenadorMapper,
                                    @Lazy InternalDataService self) {
        this.partidoRepository = partidoRepository;
        this.clasificacionRepository = clasificacionRepository;
        this.historialBajaRepository = historialBajaRepository;
        this.jugadorRepository = jugadorRepository;
        this.estadisticaJugadorPartidoRepository = estadisticaJugadorPartidoRepository;
        this.estadisticaEquipoPartidoRepository = estadisticaEquipoPartidoRepository;
        this.entrenadorRepository = entrenadorRepository;
        this.partidoMapper = partidoMapper;
        this.clasificacionMapper = clasificacionMapper;
        this.bajaMapper = bajaMapper;
        this.jugadorMapper = jugadorMapper;
        this.estadisticaJugadorMapper = estadisticaJugadorMapper;
        this.estadisticaEquipoMapper = estadisticaEquipoMapper;
        this.entrenadorMapper = entrenadorMapper;
        this.self = self;
    }

    @Override
    @Transactional(readOnly = true)
    public FormaPrediccionDTO generarContextoPorPartido(Integer idPartido) {
        Partido partido = partidoRepository.findById(idPartido)
                .orElseThrow(() -> new NotFoundException("Partido no encontrado en la base de datos"));

        FormaPrediccionDTO contexto = self.generarContexto(
                partido.getIdPartido().longValue(),
                partido.getEquipoLocal().getIdEquipo().longValue(),
                partido.getEquipoVisitante().getIdEquipo().longValue(),
                partido.getLiga().getIdLiga().longValue(),
                partido.getTemporada()
        );

        // El estado del partido es el dato que más rápido cambia dentro del contexto completo
        // (en vivo -> finalizado en segundos), y es justo lo que usa predicciones para bloquear
        // predicciones de partidos ya terminados (PredictionServiceImpl.obtenerContexto).
        // generarContexto() está cacheado 15 min: si no refrescáramos esto, ese bloqueo
        // podría tardar hasta 15 min en aplicarse tras el pitido final. El resto del
        // contexto (rachas, stats, bajas...) sí puede quedarse con el valor cacheado.
        contexto.setPartido(partidoMapper.toDto(partido));

        return contexto;
    }

    @Override
    @Cacheable(value = "contextoPrediccion", key = "#partidoId")
    public FormaPrediccionDTO generarContexto(Long partidoId, Long localId, Long visitanteId, Long ligaId, Integer temporada) {
        FormaPrediccionDTO contexto = new FormaPrediccionDTO();
        contexto.setIdPartido(partidoId);
        contexto.setIdLiga(ligaId);
        contexto.setTemporada(temporada);
        contexto.setIdEquipoLocal(localId);
        contexto.setIdEquipoVisitante(visitanteId);

        // 1. Datos del partido objetivo y Árbitro
        partidoRepository.findById(Math.toIntExact(partidoId)).ifPresent(p -> {
            contexto.setPartido(partidoMapper.toDto(p));

            if (p.getArbitro() != null) {
                String nombreArbitro = p.getArbitro().getNombre();
                List<EstadisticaEquipoPartido> statsArbitro = estadisticaEquipoPartidoRepository.findByPartido_Arbitro_IdArbitro(p.getArbitro().getIdArbitro());

                // getTarjetasAmarillas()/getTarjetasRojas()/getFaltas() son Integer y quedan
                // null cuando la API no trajo ese tipo de estadística para el partido; sin
                // filtrar el unboxing de mapToInt lanzaría NullPointerException.
                double mediaAmarillas = statsArbitro.stream().map(EstadisticaEquipoPartido::getTarjetasAmarillas)
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0.0);
                double mediaRojas = statsArbitro.stream().map(EstadisticaEquipoPartido::getTarjetasRojas)
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0.0);
                double mediaFaltas = statsArbitro.stream().map(EstadisticaEquipoPartido::getFaltas)
                        .filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0.0);

                contexto.setDatosArbitro(ArbitroStatsDTO.builder()
                        .nombre(nombreArbitro)
                        .mediaTarjetasAmarillas(java.math.BigDecimal.valueOf(mediaAmarillas))
                        .mediaTarjetasRojas(java.math.BigDecimal.valueOf(mediaRojas))
                        .mediaFaltasPorPartido(java.math.BigDecimal.valueOf(mediaFaltas))
                        .build());
            }
        });

        // 2. Entrenadores Actuales
        entrenadorRepository.findByEquipo_IdEquipo(Math.toIntExact(localId))
                .stream().findFirst().ifPresent(e -> contexto.setEntrenadorLocal(entrenadorMapper.toDto(e)));

        entrenadorRepository.findByEquipo_IdEquipo(Math.toIntExact(visitanteId))
                .stream().findFirst().ifPresent(e -> contexto.setEntrenadorVisitante(entrenadorMapper.toDto(e)));

        // 3. Posiciones en la Tabla
        clasificacionRepository.findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(
                        Math.toIntExact(ligaId), Math.toIntExact(localId), temporada)
                .ifPresent(c -> contexto.setClasificacionLocal(clasificacionMapper.toDto(c)));

        clasificacionRepository.findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(
                        Math.toIntExact(ligaId), Math.toIntExact(visitanteId), temporada)
                .ifPresent(c -> contexto.setClasificacionVisitante(clasificacionMapper.toDto(c)));

        // 4. Rachas Recientes Globales
        // El bloque siguiente, sin excepción, usa las variantes "...Finalizados" del repositorio (solo
        // p.estado = 'FINALIZADO'), no las que usa PartidoServiceImpl para los listados
        // públicos -- sin ese filtro, "los últimos 5 partidos" incluía partidos programados
        // sin jugar y, en particular, el propio partido que se está prediciendo (el más
        // próximo en el tiempo, así que cae primero al ordenar por fecha descendente). Ver el
        // javadoc de PartidoRepository.findUltimosFinalizadosByEquipo para el detalle completo.
        PageRequest limit5 = PageRequest.of(0, 5, Sort.by("fecha").descending());
        contexto.setUltimosPartidosLocal(partidoRepository.findUltimosFinalizadosByEquipo(Math.toIntExact(localId), limit5)
                .getContent().stream().map(partidoMapper::toDto).toList());
        contexto.setUltimosPartidosVisitante(partidoRepository.findUltimosFinalizadosByEquipo(Math.toIntExact(visitanteId), limit5)
                .getContent().stream().map(partidoMapper::toDto).toList());

        // 5. Rachas Puras (Local como Local, Visitante como Visitante)
        contexto.setUltimosPartidosLocalComoLocal(partidoRepository.findByEquipoLocal_IdEquipoAndEstado(Math.toIntExact(localId), "FINALIZADO", limit5)
                .getContent().stream().map(partidoMapper::toDto).toList());
        contexto.setUltimosPartidosVisitanteComoVisitante(partidoRepository.findByEquipoVisitante_IdEquipoAndEstado(Math.toIntExact(visitanteId), "FINALIZADO", limit5)
                .getContent().stream().map(partidoMapper::toDto).toList());

        // 6. Cara a Cara
        contexto.setHistorialH2H(partidoRepository.findEnfrentamientosDirectosFinalizados(Math.toIntExact(localId), Math.toIntExact(visitanteId), limit5)
                .getContent().stream().map(partidoMapper::toDto).toList());

        // 7. Bajas Activas
        LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        contexto.setBajasActivasLocal(historialBajaRepository.findBajasActivas(localId, hoy)
                .stream().map(bajaMapper::toDto).toList());
        contexto.setBajasActivasVisitante(historialBajaRepository.findBajasActivas(visitanteId, hoy)
                .stream().map(bajaMapper::toDto).toList());

        // 8. Estadísticas Globales de Equipo
        contexto.setHistoricoStatsEquiposLocal(estadisticaEquipoPartidoRepository
                .findByEquipoIdAndPartidoLigaIdAndPartidoTemporada(Math.toIntExact(localId), Math.toIntExact(ligaId), temporada)
                .stream().map(estadisticaEquipoMapper::toDto).toList());

        contexto.setHistoricoStatsEquiposVisitante(estadisticaEquipoPartidoRepository
                .findByEquipoIdAndPartidoLigaIdAndPartidoTemporada(Math.toIntExact(visitanteId), Math.toIntExact(ligaId), temporada)
                .stream().map(estadisticaEquipoMapper::toDto).toList());

        // 9. PLAYER PROPS LOCAL
        Integer idLocalInt = Math.toIntExact(localId);
        List<EstadisticaJugadorPartido> statsLocalDb = estadisticaJugadorPartidoRepository.findHistorialEquipoTemporada(idLocalInt, ligaId, temporada);
        Map<Integer, List<EstadisticaJugadorPartido>> statsAgrupadasLocal = statsLocalDb.stream().collect(Collectors.groupingBy(e -> e.getJugador().getIdJugador()));

        contexto.setPlantillaLocalConStats(jugadorRepository.findByEquipo_IdEquipo(idLocalInt, Pageable.ofSize(30))
                .stream().map(jugador -> ContextoJugadorPrediccionDTO.builder()
                        .jugador(jugadorMapper.toDto(jugador))
                        .historialStatsTemporada(statsAgrupadasLocal.getOrDefault(jugador.getIdJugador(), Collections.emptyList())
                                .stream().map(estadisticaJugadorMapper::toDto).toList())
                        .build()
                ).toList());

        // 10. PLAYER PROPS VISITANTE
        Integer idVisitanteInt = Math.toIntExact(visitanteId);
        List<EstadisticaJugadorPartido> statsVisitanteDb = estadisticaJugadorPartidoRepository.findHistorialEquipoTemporada(idVisitanteInt, ligaId, temporada);
        Map<Integer, List<EstadisticaJugadorPartido>> statsAgrupadasVisitante = statsVisitanteDb.stream().collect(Collectors.groupingBy(e -> e.getJugador().getIdJugador()));

        contexto.setPlantillaVisitanteConStats(jugadorRepository.findByEquipo_IdEquipo(idVisitanteInt, Pageable.ofSize(30))
                .stream().map(jugador -> ContextoJugadorPrediccionDTO.builder()
                        .jugador(jugadorMapper.toDto(jugador))
                        .historialStatsTemporada(statsAgrupadasVisitante.getOrDefault(jugador.getIdJugador(), Collections.emptyList())
                                .stream().map(estadisticaJugadorMapper::toDto).toList())
                        .build()
                ).toList());

        return contexto;
    }

}