package estadisticas.services.jugadores;

import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.equipos.Traspaso;
import estadisticas.entities.jugadores.HistorialBaja;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.entities.ligas.Trofeo;
import estadisticas.exception.NoContentException;
import estadisticas.exception.NotFoundException;
import estadisticas.mappers.equipos.TraspasoMapper;
import estadisticas.mappers.jugadores.HistorialBajaMapper;
import estadisticas.mappers.jugadores.JugadorMapper;
import estadisticas.mappers.ligas.TrofeoMapper;
import estadisticas.repositories.equipos.TraspasoRepository;
import estadisticas.repositories.jugadores.HistorialBajaRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.ligas.TrofeoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JugadorServiceImplTest {

    @Mock private JugadorRepository jugadorRepository;
    @Mock private JugadorMapper jugadorMapper;
    @Mock private TrofeoRepository trofeoRepository;
    @Mock private TrofeoMapper trofeoMapper;
    @Mock private HistorialBajaRepository historialBajaRepository;
    @Mock private HistorialBajaMapper historialBajaMapper;
    @Mock private TraspasoRepository traspasoRepository;
    @Mock private TraspasoMapper traspasoMapper;
    @Mock private ClasificacionRepository clasificacionRepository;
    @Mock private estadisticas.services.ligas.BanderasPais banderasPais;

    @InjectMocks private JugadorServiceImpl jugadorService;

    private Jugador jugador;
    private JugadorDTO jugadorDTO;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        jugador = new Jugador();
        jugador.setIdJugador(1);
        jugadorDTO = new JugadorDTO();
        pageable = PageRequest.of(0, 20);
    }

    @Test
    void obtenerJugadorPorId_Existe_RetornaDTO() {
        when(jugadorRepository.findById(1)).thenReturn(Optional.of(jugador));
        when(jugadorMapper.toDto(jugador)).thenReturn(jugadorDTO);

        JugadorDTO resultado = jugadorService.obtenerJugadorPorId(1);

        assertNotNull(resultado);
        verify(jugadorRepository).findById(1);
    }

    @Test
    void obtenerJugadorPorId_NoExiste_LanzaNotFoundException() {
        when(jugadorRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jugadorService.obtenerJugadorPorId(99));
    }

    @Test
    void obtenerPlantillaPorEquipo_ConDatos_RetornaPagina() {
        Page<Jugador> pagina = new PageImpl<>(List.of(jugador));
        when(jugadorRepository.findByEquipo_IdEquipo(10, pageable)).thenReturn(pagina);
        when(jugadorMapper.toDto(jugador)).thenReturn(jugadorDTO);

        Page<JugadorDTO> resultado = jugadorService.obtenerPlantillaPorEquipo(10, pageable);

        assertEquals(1, resultado.getTotalElements());
    }

    @Test
    void obtenerPlantillaPorEquipo_SinDatos_LanzaNoContentException() {
        Page<Jugador> paginaVacia = new PageImpl<>(Collections.emptyList());
        when(jugadorRepository.findByEquipo_IdEquipo(99, pageable)).thenReturn(paginaVacia);

        assertThrows(NoContentException.class, () -> jugadorService.obtenerPlantillaPorEquipo(99, pageable));
    }

    @Test
    void obtenerTrofeosJugador_RetornaLista() {
        Trofeo trofeo = new Trofeo();
        when(trofeoRepository.findByJugador_IdJugador(1)).thenReturn(List.of(trofeo));
        when(trofeoMapper.toDto(trofeo)).thenReturn(new TrofeoDTO());

        List<TrofeoDTO> resultado = jugadorService.obtenerTrofeosJugador(1);

        assertEquals(1, resultado.size());
    }

    @Test
    void obtenerHistorialBajasJugador_RetornaLista() {
        HistorialBaja baja = new HistorialBaja();
        when(historialBajaRepository.findByJugador_IdJugadorOrderByFechaInicioDesc(1)).thenReturn(List.of(baja));
        when(historialBajaMapper.toDto(baja)).thenReturn(new HistorialBajaDTO());

        List<HistorialBajaDTO> resultado = jugadorService.obtenerHistorialBajasJugador(1);

        assertEquals(1, resultado.size());
    }

    @Test
    void obtenerTraspasosJugador_RetornaLista() {
        Traspaso traspaso = new Traspaso();
        when(traspasoRepository.findByJugadorIdJugadorOrderByFechaDesc(1)).thenReturn(List.of(traspaso));
        when(traspasoMapper.toDto(traspaso)).thenReturn(new TraspasoDTO());

        List<TraspasoDTO> resultado = jugadorService.obtenerTraspasosJugador(1);

        assertEquals(1, resultado.size());
    }

    @Test
    void obtenerJugadorAleatorioParaJuego_ConJugadores_RetornaJugadorDTO() {
        when(jugadorRepository.countByEquipoIsNotNull()).thenReturn(10L);
        Page<Jugador> pagina = new PageImpl<>(List.of(jugador));
        when(jugadorRepository.findByEquipoIsNotNull(any(PageRequest.class))).thenReturn(pagina);
        when(jugadorMapper.toDto(jugador)).thenReturn(jugadorDTO);

        JugadorDTO resultado = jugadorService.obtenerJugadorAleatorioParaJuego();

        assertNotNull(resultado);
    }

    @Test
    void obtenerJugadorAleatorioParaJuego_SinJugadores_LanzaNotFoundException() {
        when(jugadorRepository.countByEquipoIsNotNull()).thenReturn(0L);

        assertThrows(NotFoundException.class, () -> jugadorService.obtenerJugadorAleatorioParaJuego());
    }

    @Test
    void obtenerJugadorAleatorioParaJuego_JugadorBorradoEntreElConteoYLaPagina_LanzaNotFoundException() {
        // Ventana de carrera: countByEquipoIsNotNull() > 0 pero la página del índice
        // elegido viene vacía (alguien borró ese jugador justo entre medias).
        when(jugadorRepository.countByEquipoIsNotNull()).thenReturn(10L);
        when(jugadorRepository.findByEquipoIsNotNull(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));

        assertThrows(NotFoundException.class, () -> jugadorService.obtenerJugadorAleatorioParaJuego());
    }

    @Test
    void getAllJugadores_ConDatos_RetornaPagina() {
        Page<Jugador> pagina = new PageImpl<>(List.of(jugador));
        // findTodosPriorizandoConEquipo y no findAll: el listado saca primero a los jugadores
        // con club, porque el 91% de las fichas de la tabla no tiene equipo y con el orden
        // natural la primera página no enseñaba a nadie reconocible.
        when(jugadorRepository.findTodosPriorizandoConEquipo(pageable)).thenReturn(pagina);
        when(jugadorMapper.toDto(jugador)).thenReturn(jugadorDTO);

        Page<JugadorDTO> resultado = jugadorService.getAllJugadores(pageable);

        assertEquals(1, resultado.getTotalElements());
    }

    @Test
    void getAllJugadores_SinDatos_LanzaNoContentException() {
        Page<Jugador> paginaVacia = new PageImpl<>(Collections.emptyList());
        when(jugadorRepository.findTodosPriorizandoConEquipo(pageable)).thenReturn(paginaVacia);

        assertThrows(NoContentException.class, () -> jugadorService.getAllJugadores(pageable));
    }

    @Test
    void getJugadoresPorLiga_ConDatos_RetornaPagina() {
        // Vía Clasificacion, no Equipo.liga -- ver el javadoc de la interfaz para el porqué.
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(10);
        Clasificacion clasificacion = new Clasificacion();
        clasificacion.setEquipo(equipo);

        Page<Jugador> pagina = new PageImpl<>(List.of(jugador));
        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(5, 2026))
                .thenReturn(List.of(clasificacion));
        when(jugadorRepository.findByEquipo_IdEquipoIn(List.of(10), pageable)).thenReturn(pagina);
        when(jugadorMapper.toDto(jugador)).thenReturn(jugadorDTO);

        Page<JugadorDTO> resultado = jugadorService.getJugadoresPorLiga(5, 2026, pageable);

        assertEquals(1, resultado.getTotalElements());
    }

    @Test
    void getJugadoresPorLiga_SinEquiposEnClasificacion_LanzaNoContentException() {
        when(clasificacionRepository.findByLiga_IdLigaAndTemporadaOrderByPosicionAsc(99, 2026))
                .thenReturn(Collections.emptyList());

        assertThrows(NoContentException.class, () -> jugadorService.getJugadoresPorLiga(99, 2026, pageable));
    }
}