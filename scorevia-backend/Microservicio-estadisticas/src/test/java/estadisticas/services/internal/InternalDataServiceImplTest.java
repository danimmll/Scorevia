package estadisticas.services.internal;

import estadisticas.dto.internal.FormaPrediccionDTO;
import estadisticas.entities.arbitros.Arbitro;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.entities.ligas.Liga;
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
import estadisticas.repositories.jugadores.HistorialBajaRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalDataServiceImplTest {

    @Mock private PartidoRepository partidoRepository;
    @Mock private ClasificacionRepository clasificacionRepository;
    @Mock private HistorialBajaRepository historialBajaRepository;
    @Mock private JugadorRepository jugadorRepository;
    @Mock private EstadisticaJugadorPartidoRepository estadisticaJugadorPartidoRepository;
    @Mock private EstadisticaEquipoPartidoRepository estadisticaEquipoPartidoRepository;
    @Mock private EntrenadorRepository entrenadorRepository;

    @Mock private PartidoMapper partidoMapper;
    @Mock private ClasificacionMapper clasificacionMapper;
    @Mock private HistorialBajaMapper bajaMapper;
    @Mock private JugadorMapper jugadorMapper;
    @Mock private EstadisticaJugadorMapper estadisticaJugadorMapper;
    @Mock private EstadisticaEquipoMapper estadisticaEquipoMapper;
    @Mock private EntrenadorMapper entrenadorMapper;

    @InjectMocks
    private InternalDataServiceImpl internalDataService;

    private Partido partido;

    @BeforeEach
    void setUp() {
        partido = new Partido();
        partido.setIdPartido(1);
        partido.setTemporada(2026);

        Liga liga = new Liga();
        liga.setIdLiga(5);
        partido.setLiga(liga);

        Equipo local = new Equipo();
        local.setIdEquipo(10);
        partido.setEquipoLocal(local);

        Equipo visitante = new Equipo();
        visitante.setIdEquipo(20);
        partido.setEquipoVisitante(visitante);

        // En producción 'self' es el proxy de Spring inyectado de forma perezosa (para que
        // @Cacheable se aplique en la llamada interna); en este test unitario no hay
        // contexto de Spring, así que apuntamos 'self' al propio objeto para conservar el
        // comportamiento (sin aplicar realmente el caché, que no es lo que se testea aquí).
        ReflectionTestUtils.setField(internalDataService, "self", internalDataService);
    }

    @Test
    void generarContextoPorPartido_NoEncontrado_LanzaExcepcion() {
        when(partidoRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> internalDataService.generarContextoPorPartido(99));
    }

    @Test
    void generarContextoPorPartido_PartidoCompleto_GeneraContexto() {
        // Árbitro para coverage de su if
        Arbitro arbitro = new Arbitro();
        arbitro.setIdArbitro(2);
        arbitro.setNombre("Mateu Lahoz");
        partido.setArbitro(arbitro);

        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));

        // Mocks de estadísticas del árbitro
        EstadisticaEquipoPartido estArbitro = new EstadisticaEquipoPartido();
        estArbitro.setTarjetasAmarillas(4);
        estArbitro.setTarjetasRojas(1);
        estArbitro.setFaltas(20);
        when(estadisticaEquipoPartidoRepository.findByPartido_Arbitro_IdArbitro(2))
                .thenReturn(List.of(estArbitro));

        // Mocks Entrenadores
        when(entrenadorRepository.findByEquipo_IdEquipo(10)).thenReturn(List.of(new Entrenador()));
        when(entrenadorRepository.findByEquipo_IdEquipo(20)).thenReturn(List.of(new Entrenador()));

        // Mocks Clasificaciones
        when(clasificacionRepository.findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(5, 10, 2026))
                .thenReturn(Optional.of(new Clasificacion()));
        when(clasificacionRepository.findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(5, 20, 2026))
                .thenReturn(Optional.of(new Clasificacion()));

        // Mocks Rachas y H2H (solo FINALIZADO -- ver PartidoRepository.findUltimosFinalizadosByEquipo)
        when(partidoRepository.findUltimosFinalizadosByEquipo(anyInt(), any())).thenReturn(new PageImpl<>(List.of(partido)));
        when(partidoRepository.findByEquipoLocal_IdEquipoAndEstado(anyInt(), eq("FINALIZADO"), any())).thenReturn(new PageImpl<>(List.of(partido)));
        when(partidoRepository.findByEquipoVisitante_IdEquipoAndEstado(anyInt(), eq("FINALIZADO"), any())).thenReturn(new PageImpl<>(List.of(partido)));
        when(partidoRepository.findEnfrentamientosDirectosFinalizados(anyInt(), anyInt(), any())).thenReturn(new PageImpl<>(List.of(partido)));

        // Mocks Bajas
        when(historialBajaRepository.findBajasActivas(anyLong(), any())).thenReturn(List.of());

        // Mocks Stats Globales
        when(estadisticaEquipoPartidoRepository.findByEquipoIdAndPartidoLigaIdAndPartidoTemporada(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(new EstadisticaEquipoPartido()));

        // Mocks Player Props
        Jugador jugador = new Jugador();
        jugador.setIdJugador(999);
        when(jugadorRepository.findByEquipo_IdEquipo(anyInt(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(jugador)));

        EstadisticaJugadorPartido ejp = new EstadisticaJugadorPartido();
        ejp.setJugador(jugador);
        when(estadisticaJugadorPartidoRepository.findHistorialEquipoTemporada(anyInt(), anyLong(), anyInt()))
                .thenReturn(List.of(ejp));

        FormaPrediccionDTO resultado = internalDataService.generarContextoPorPartido(1);

        assertNotNull(resultado);
        assertEquals(1L, resultado.getIdPartido());
        assertEquals(5L, resultado.getIdLiga());
        assertNotNull(resultado.getDatosArbitro());
        assertEquals("Mateu Lahoz", resultado.getDatosArbitro().getNombre());
    }

    @Test
    void generarContexto_PartidoSinArbitro_NoFalla() {
        partido.setArbitro(null); // Probamos rama sin árbitro
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));

        when(partidoRepository.findUltimosFinalizadosByEquipo(anyInt(), any())).thenReturn(new PageImpl<>(List.of()));
        when(partidoRepository.findByEquipoLocal_IdEquipoAndEstado(anyInt(), eq("FINALIZADO"), any())).thenReturn(new PageImpl<>(List.of()));
        when(partidoRepository.findByEquipoVisitante_IdEquipoAndEstado(anyInt(), eq("FINALIZADO"), any())).thenReturn(new PageImpl<>(List.of()));
        when(partidoRepository.findEnfrentamientosDirectosFinalizados(anyInt(), anyInt(), any())).thenReturn(new PageImpl<>(List.of()));

        // === MOCKS AÑADIDOS PARA EVITAR EL NULL POINTER EXCEPTION ===
        when(jugadorRepository.findByEquipo_IdEquipo(anyInt(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(estadisticaJugadorPartidoRepository.findHistorialEquipoTemporada(anyInt(), anyLong(), anyInt())).thenReturn(List.of());
        when(estadisticaEquipoPartidoRepository.findByEquipoIdAndPartidoLigaIdAndPartidoTemporada(anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        FormaPrediccionDTO resultado = internalDataService.generarContexto(1L, 10L, 20L, 5L, 2026);

        assertNotNull(resultado);
        assertNull(resultado.getDatosArbitro());
    }

}