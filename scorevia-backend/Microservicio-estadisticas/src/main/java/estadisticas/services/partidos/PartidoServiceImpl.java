package estadisticas.services.partidos;

import estadisticas.dto.internal.EventoPartidoInternoDTO;
import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import estadisticas.dto.partidos.EventoPartidoDTO;
import estadisticas.dto.partidos.PartidoDTO;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;
import estadisticas.entities.partidos.Partido;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.partidos.EstadisticaJugadorMapper;
import estadisticas.mappers.partidos.EventoPartidoMapper;
import estadisticas.mappers.partidos.PartidoMapper;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.EventoPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartidoServiceImpl implements PartidoService {

    private final PartidoRepository partidoRepository;
    private final PartidoMapper partidoMapper;

    private final EstadisticaEquipoPartidoRepository estEquipoRepository;
    private final EstadisticaJugadorPartidoRepository estJugadorRepository;
    private final EstadisticaJugadorMapper jugadorMapper;

    private final EventoPartidoRepository eventoPartidoRepository;
    private final EventoPartidoMapper eventoPartidoMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<PartidoDTO> getPartidos(Pageable pageable) {
        return partidoRepository.findAll(pageable).map(partidoMapper::toDto);
    }

    /**
     * Cuanto hacia atras se mira para dar un partido por "en juego ahora mismo".
     *
     * <p>Seis horas cubre de sobra el peor caso real: 90 minutos, mas descuentos, mas
     * prorroga, mas tanda de penaltis, mas una interrupcion larga por tormenta o por un
     * problema en el campo. Lo que queda fuera de esa ventana y sigue marcado EN_JUEGO no
     * es un partido largo, es un partido que se quedo colgado.</p>
     */
    private static final Duration VENTANA_EN_VIVO = Duration.ofHours(6);

    /**
     * Los partidos que se estan jugando ahora mismo, del que empezo antes al que empezo despues.
     *
     * <p>Ese orden es el que se quiere en un marcador en directo: arriba los que van mas
     * avanzados, que son los que estan a punto de resolverse. Y en cuanto uno termina deja de
     * estar EN_JUEGO, asi que se cae solo de la lista sin nada que lo limpie.</p>
     *
     * <p>La ventana de {@link #VENTANA_EN_VIVO} es la parte importante: sin ella salian
     * partidos de dias anteriores que se quedaron clavados en EN_JUEGO porque la ingesta en
     * vivo no llego a verlos terminar. Se midio sobre la base: 21 partidos del dia anterior
     * ensuciando el directo.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<PartidoDTO> getPartidosEnVivo() {
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        return partidoRepository.findByEstadoAndFechaBetweenOrderByFechaAsc(
                        "EN_JUEGO", ahora.minus(VENTANA_EN_VIVO), ahora.plusMinutes(15))
                .stream()
                .map(partidoMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PartidoDTO getPartidoById(Integer id) {
        Partido partido = partidoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Partido no encontrado", "partido-no-encontrado"));
        return partidoMapper.toDto(partido);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "partidosLiga", key = "#idLiga + '_' + #temporada + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<PartidoDTO> getPartidosByLiga(Integer idLiga, Integer temporada, Pageable pageable) {
        Page<Partido> partidos = temporada != null
                ? partidoRepository.findByLiga_IdLigaAndTemporada(idLiga, temporada, pageable)
                : partidoRepository.findByLiga_IdLiga(idLiga, pageable);
        return partidos.map(partidoMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartidoDTO> getPartidosPorFecha(java.time.LocalDate fecha, Pageable pageable) {
        return partidoRepository
                .findByFechaBetween(fecha.atStartOfDay(), fecha.atTime(23, 59, 59), pageable)
                .map(partidoMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartidoDTO> getPartidosByEquipo(Integer idEquipo, Pageable pageable) {
        return partidoRepository.findByEquipo(idEquipo, pageable).map(partidoMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartidoDTO> getPartidosJugadosByEquipo(Integer idEquipo, Pageable pageable) {
        return partidoRepository.findJugadosByEquipo(idEquipo, pageable).map(partidoMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartidoDTO> getPartidosProgramadosByEquipo(Integer idEquipo, Pageable pageable) {
        return partidoRepository.findProgramadosByEquipo(idEquipo, pageable).map(partidoMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartidoDTO> getEnfrentamientosDirectos(Integer idEquipoA, Integer idEquipoB, Pageable pageable) {
        return partidoRepository.findEnfrentamientosDirectos(idEquipoA, idEquipoB, pageable).map(partidoMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstadisticaEquipoDTO> getEstadisticasPartido(Integer idPartido) {
        List<EstadisticaEquipoPartido> statsBD = estEquipoRepository.findByPartidoIdPartido(idPartido);

        return statsBD.stream().map(est ->
                EstadisticaEquipoDTO.builder()
                        .nombreEquipo(est.getEquipo().getNombre())
                        .posesion(est.getPosesion())
                        .tiros(est.getTiros())
                        .tirosPuerta(est.getTirosPuerta())
                        .corners(est.getCorners())
                        .faltas(est.getFaltas())
                        .tarjetasAmarillas(est.getTarjetasAmarillas())
                        .tarjetasRojas(est.getTarjetasRojas())
                        .golesEsperados(est.getGolesEsperados())
                        .estadisticasExtra(est.getEstadisticasExtra())
                        .build()
        ).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstadisticaJugadorPartidoDTO> getEstadisticasJugadoresPartido(Integer idPartido) {
        List<EstadisticaJugadorPartido> statsBD = estJugadorRepository.findByPartidoIdPartido(idPartido);

        return statsBD.stream()
                .map(jugadorMapper::toDto).toList();
    }



    @Override
    @Transactional(readOnly = true)
    public List<EventoPartidoDTO> getEventosPartido(Integer idPartido) {
        return eventoPartidoRepository.findEventosByPartidoConDetalles(idPartido).stream()
                .map(eventoPartidoMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoPartidoInternoDTO> getEventosPartidoInterno(Integer idPartido) {
        return eventoPartidoRepository.findEventosByPartidoConDetalles(idPartido).stream()
                .map(evento -> EventoPartidoInternoDTO.builder()
                        .tiempoMinuto(evento.getTiempoMinuto())
                        .tiempoExtra(evento.getTiempoExtra())
                        .tipoEvento(evento.getTipoEvento())
                        .detalle(evento.getDetalle())
                        .equipo(EventoPartidoInternoDTO.EquipoInternoDTO.builder()
                                .idEquipo(evento.getEquipo().getIdEquipo())
                                .build())
                        .build())
                .toList();
    }
}