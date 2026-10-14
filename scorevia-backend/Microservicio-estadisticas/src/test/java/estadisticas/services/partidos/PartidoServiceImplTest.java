package estadisticas.services.partidos;

import estadisticas.dto.internal.EventoPartidoInternoDTO;
import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import estadisticas.dto.partidos.PartidoDTO;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;
import estadisticas.entities.partidos.EventoPartido;
import estadisticas.entities.partidos.Partido;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.partidos.EstadisticaJugadorMapper;
import estadisticas.mappers.partidos.EventoPartidoMapper;
import estadisticas.mappers.partidos.PartidoMapper;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.EventoPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.jspecify.annotations.NonNull;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartidoServiceImplTest {

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private PartidoMapper partidoMapper;

    @Mock
    private EstadisticaEquipoPartidoRepository estEquipoRepository;

    @Mock
    private EstadisticaJugadorPartidoRepository estJugadorRepository;

    @Mock
    private EstadisticaJugadorMapper jugadorMapper;

    @Mock
    private EventoPartidoRepository eventoPartidoRepository;

    @Mock
    private EventoPartidoMapper eventoPartidoMapper;

    @InjectMocks
    private PartidoServiceImpl partidoService;

    @Test
    void getPartidos_DebeRetornarPaginaDePartidos() {
        Pageable pageable = PageRequest.of(0, 10);
        Partido partido = new Partido();
        PartidoDTO partidoDTO = new PartidoDTO();

        when(partidoRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(partido)));
        when(partidoMapper.toDto(partido)).thenReturn(partidoDTO);

        Page<PartidoDTO> result = partidoService.getPartidos(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(partidoRepository).findAll(pageable);
        verify(partidoMapper).toDto(partido);
    }

    @Test
    void getPartidosEnVivo_DebeRetornarListaDePartidosEnVivo() {
        Partido partido = new Partido();
        PartidoDTO partidoDTO = new PartidoDTO();

        when(partidoRepository.findByEstadoAndFechaBetweenOrderByFechaAsc(
                eq("EN_JUEGO"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(partido));
        when(partidoMapper.toDto(partido)).thenReturn(partidoDTO);

        List<PartidoDTO> result = partidoService.getPartidosEnVivo();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("El directo se pide en una ventana de horas, no de todo lo que haya en EN_JUEGO")
    void getPartidosEnVivo_FiltraPorVentanaDeTiempo() {
        // Sin esta ventana salian en el marcador partidos de dias anteriores que se quedaron
        // clavados en EN_JUEGO porque la ingesta no llego a verlos terminar. Medido sobre la
        // base: 21 partidos del dia anterior ensuciando el directo.
        when(partidoRepository.findByEstadoAndFechaBetweenOrderByFechaAsc(
                eq("EN_JUEGO"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        partidoService.getPartidosEnVivo();

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(partidoRepository).findByEstadoAndFechaBetweenOrderByFechaAsc(
                eq("EN_JUEGO"), desde.capture(), hasta.capture());

        // La ventana tiene que cubrir un partido largo (prorroga y penaltis) sin llegar a
        // recoger los del dia anterior.
        long horas = Duration.between(desde.getValue(), hasta.getValue()).toHours();
        assertTrue(horas >= 3 && horas <= 12,
                "La ventana del directo deberia rondar unas horas, y son " + horas);
    }

    @Test
    void getPartidoById_CuandoExiste_DebeRetornarPartido() {
        Integer idPartido = 1;
        Partido partido = new Partido();
        PartidoDTO partidoDTO = new PartidoDTO();

        when(partidoRepository.findById(idPartido)).thenReturn(Optional.of(partido));
        when(partidoMapper.toDto(partido)).thenReturn(partidoDTO);

        PartidoDTO result = partidoService.getPartidoById(idPartido);

        assertNotNull(result);
        verify(partidoRepository).findById(idPartido);
    }

    @Test
    void getPartidoById_CuandoNoExiste_DebeLanzarNotFoundException() {
        Integer idPartido = 999;

        when(partidoRepository.findById(idPartido)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> partidoService.getPartidoById(idPartido));
        verify(partidoRepository).findById(idPartido);
        verifyNoInteractions(partidoMapper);
    }

    @Test
    void getPartidosByLiga_DebeRetornarPagina() {
        Integer idLiga = 140;
        Pageable pageable = PageRequest.of(0, 10);
        Partido partido = new Partido();
        PartidoDTO partidoDTO = new PartidoDTO();

        when(partidoRepository.findByLiga_IdLiga(idLiga, pageable)).thenReturn(new PageImpl<>(List.of(partido)));
        when(partidoMapper.toDto(partido)).thenReturn(partidoDTO);

        Page<PartidoDTO> result = partidoService.getPartidosByLiga(idLiga, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(partidoRepository).findByLiga_IdLiga(idLiga, pageable);
    }

    @Test
    void getPartidosByEquipo_DebeRetornarPagina() {
        Integer idEquipo = 541;
        Pageable pageable = PageRequest.of(0, 10);
        Partido partido = new Partido();
        PartidoDTO partidoDTO = new PartidoDTO();

        when(partidoRepository.findByEquipo(idEquipo, pageable)).thenReturn(new PageImpl<>(List.of(partido)));
        when(partidoMapper.toDto(partido)).thenReturn(partidoDTO);

        Page<PartidoDTO> result = partidoService.getPartidosByEquipo(idEquipo, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(partidoRepository).findByEquipo(idEquipo, pageable);
    }

    @Test
    void getEnfrentamientosDirectos_DebeRetornarPagina() {
        Integer idEquipoA = 541;
        Integer idEquipoB = 529;
        Pageable pageable = PageRequest.of(0, 10);
        Partido partido = new Partido();
        PartidoDTO partidoDTO = new PartidoDTO();

        when(partidoRepository.findEnfrentamientosDirectos(idEquipoA, idEquipoB, pageable))
                .thenReturn(new PageImpl<>(List.of(partido)));
        when(partidoMapper.toDto(partido)).thenReturn(partidoDTO);

        Page<PartidoDTO> result = partidoService.getEnfrentamientosDirectos(idEquipoA, idEquipoB, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(partidoRepository).findEnfrentamientosDirectos(idEquipoA, idEquipoB, pageable);
    }

    @Test
    void getEstadisticasPartido_DebeMapearYRetornarLista() {
        Integer idPartido = 100;
        EstadisticaEquipoPartido est = getEstadisticaEquipoPartido();

        when(estEquipoRepository.findByPartidoIdPartido(idPartido)).thenReturn(List.of(est));

        List<EstadisticaEquipoDTO> result = partidoService.getEstadisticasPartido(idPartido);

        assertNotNull(result);
        assertEquals(1, result.size());

        EstadisticaEquipoDTO dto = result.getFirst();
        assertEquals("Real Madrid", dto.getNombreEquipo());
        assertEquals(new BigDecimal("60.0"), dto.getPosesion());
        assertEquals(15, dto.getTiros());
        assertEquals(5, dto.getTirosPuerta());
        assertEquals(6, dto.getCorners());
        assertEquals(10, dto.getFaltas());
        assertEquals(2, dto.getTarjetasAmarillas());
        assertEquals(0, dto.getTarjetasRojas());
        assertEquals(new BigDecimal("2.1"), dto.getGolesEsperados());
        assertEquals(Map.of("pases", "500"), dto.getEstadisticasExtra());

        verify(estEquipoRepository).findByPartidoIdPartido(idPartido);
    }

    @Test
    void getEventosPartidoInterno_MapeaTiempoTipoDetalleYIdEquipo() {
        Integer idPartido = 100;

        Equipo equipo = new Equipo();
        equipo.setIdEquipo(541);

        EventoPartido evento = new EventoPartido();
        evento.setTiempoMinuto(75);
        evento.setTiempoExtra(2);
        evento.setTipoEvento("Card");
        evento.setDetalle("Red Card");
        evento.setEquipo(equipo);

        when(eventoPartidoRepository.findEventosByPartidoConDetalles(idPartido)).thenReturn(List.of(evento));

        List<EventoPartidoInternoDTO> result = partidoService.getEventosPartidoInterno(idPartido);

        assertEquals(1, result.size());
        EventoPartidoInternoDTO dto = result.getFirst();
        assertEquals(75, dto.getTiempoMinuto());
        assertEquals(2, dto.getTiempoExtra());
        assertEquals("Card", dto.getTipoEvento());
        assertEquals("Red Card", dto.getDetalle());
        assertEquals(541, dto.getEquipo().getIdEquipo());
    }

    private static @NonNull EstadisticaEquipoPartido getEstadisticaEquipoPartido() {
        EstadisticaEquipoPartido est = new EstadisticaEquipoPartido();
        Equipo equipo = new Equipo();
        equipo.setNombre("Real Madrid");

        est.setEquipo(equipo);
        est.setPosesion(new BigDecimal("60.0"));
        est.setTiros(15);
        est.setTirosPuerta(5);
        est.setCorners(6);
        est.setFaltas(10);
        est.setTarjetasAmarillas(2);
        est.setTarjetasRojas(0);
        est.setGolesEsperados(new BigDecimal("2.1"));
        est.setEstadisticasExtra(Map.of("pases", "500"));
        return est;
    }

    @Test
    void getEstadisticasJugadoresPartido_DebeRetornarListaDTO() {
        Integer idPartido = 100;
        EstadisticaJugadorPartido est = new EstadisticaJugadorPartido();
        EstadisticaJugadorPartidoDTO dto = new EstadisticaJugadorPartidoDTO();

        when(estJugadorRepository.findByPartidoIdPartido(idPartido)).thenReturn(List.of(est));
        when(jugadorMapper.toDto(est)).thenReturn(dto);

        List<EstadisticaJugadorPartidoDTO> result = partidoService.getEstadisticasJugadoresPartido(idPartido);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(estJugadorRepository).findByPartidoIdPartido(idPartido);
        verify(jugadorMapper).toDto(est);
    }
}