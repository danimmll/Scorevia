package estadisticas.services.temporadas;

import estadisticas.dto.equipos.TemporadaEquipoDTO;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.entrenadores.HistorialEntrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.entities.ligas.Liga;
import estadisticas.exception.NotFoundException;
import estadisticas.repositories.entrenadores.HistorialEntrenadorRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemporadaEquipoServiceImplTest {

    private static final int ID_EQUIPO = 541;
    private static final int TEMPORADA = 2024;

    @Mock private EquipoRepository equipoRepository;
    @Mock private PartidoRepository partidoRepository;
    @Mock private ClasificacionRepository clasificacionRepository;
    @Mock private HistorialEntrenadorRepository historialEntrenadorRepository;
    @Mock private EstadisticaJugadorPartidoRepository estadisticaRepository;

    @InjectMocks private TemporadaEquipoServiceImpl servicio;

    @BeforeEach
    void equipoQueExiste() {
        Equipo equipo = new Equipo();
        equipo.setIdEquipo(ID_EQUIPO);
        equipo.setNombre("Real Madrid");
        equipo.setEscudoUrl("escudo.png");
        lenient().when(equipoRepository.findById(ID_EQUIPO)).thenReturn(Optional.of(equipo));
        lenient().when(historialEntrenadorRepository
                .findByEquipo_IdEquipoAndTemporadaLessThanEqualOrderByTemporadaDesc(ID_EQUIPO, TEMPORADA))
                .thenReturn(List.of());
        lenient().when(clasificacionRepository
                .findByEquipo_IdEquipoAndTemporadaOrderByPosicionAsc(ID_EQUIPO, TEMPORADA)).thenReturn(List.of());
        lenient().when(estadisticaRepository
                .resumenPlantillaDeTemporada(ID_EQUIPO, TEMPORADA)).thenReturn(List.of());
    }

    /** Una fila de plantilla con la forma que devuelve la consulta. */
    private Object[] jugador(int id, String nombre, long minutos, Long goles, Long amarillas) {
        return new Object[]{id, nombre, "foto.png", "Attacker", 30L, minutos, goles, 5L, amarillas, 0L, 7.5, 25L};
    }

    private Clasificacion posicion(int idLiga, String nombre, int puesto) {
        Liga liga = new Liga();
        liga.setIdLiga(idLiga);
        liga.setNombre(nombre);
        Clasificacion c = new Clasificacion();
        c.setLiga(liga);
        c.setPosicion(puesto);
        c.setPuntos(90);
        c.setPartidosJugados(38);
        return c;
    }

    @Test
    @DisplayName("Un primer puesto en la clasificacion final cuenta como titulo")
    void losTitulosSalenDelPrimerPuesto() {
        // La tabla `trofeos` cuelga de jugadores y entrenadores, nunca de equipos: el primer
        // puesto de la clasificacion es la misma informacion y ya la tenemos.
        when(clasificacionRepository.findByEquipo_IdEquipoAndTemporadaOrderByPosicionAsc(ID_EQUIPO, TEMPORADA))
                .thenReturn(List.of(posicion(140, "La Liga", 1), posicion(2, "Champions League", 4)));

        TemporadaEquipoDTO t = servicio.detalle(ID_EQUIPO, TEMPORADA);

        assertEquals(2, t.getCompeticiones().size());
        assertEquals(1, t.getTitulos().size());
        assertEquals("La Liga", t.getTitulos().get(0).getNombre());
    }

    @Test
    @DisplayName("Los totales suman toda la plantilla")
    void sumaLosTotales() {
        when(estadisticaRepository.resumenPlantillaDeTemporada(ID_EQUIPO, TEMPORADA))
                .thenReturn(List.<Object[]>of(
                        jugador(1, "Uno", 2800L, 17L, 4L),
                        jugador(2, "Dos", 2100L, 8L, 6L)));

        TemporadaEquipoDTO.TotalesDTO totales = servicio.detalle(ID_EQUIPO, TEMPORADA).getTotales();

        assertEquals(2, totales.getJugadoresUtilizados());
        assertEquals(25, totales.getGoles());
        assertEquals(10, totales.getAmarillas());
    }

    @Test
    @DisplayName("Un jugador sin goles registrados cuenta como cero, no rompe la suma")
    void agregadosNulos() {
        // Un SUM sobre columnas todas nulas vuelve null, no cero: pasa en las competiciones
        // que no publican estadisticas de jugador.
        when(estadisticaRepository.resumenPlantillaDeTemporada(ID_EQUIPO, TEMPORADA))
                .thenReturn(List.<Object[]>of(
                        jugador(1, "Uno", 2800L, null, null),
                        jugador(2, "Dos", 2100L, 8L, 6L)));

        TemporadaEquipoDTO.TotalesDTO totales = servicio.detalle(ID_EQUIPO, TEMPORADA).getTotales();

        assertEquals(8, totales.getGoles());
        assertEquals(6, totales.getAmarillas());
    }

    @Test
    @DisplayName("Una temporada con destitucion enseña a los dos entrenadores")
    void variosEntrenadores() {
        when(historialEntrenadorRepository
                .findByEquipo_IdEquipoAndTemporadaLessThanEqualOrderByTemporadaDesc(ID_EQUIPO, TEMPORADA))
                .thenReturn(List.of(historial(1, "Zidane", TEMPORADA), historial(2, "Ancelotti", TEMPORADA)));

        List<TemporadaEquipoDTO.EntrenadorTemporadaDTO> entrenadores =
                servicio.detalle(ID_EQUIPO, TEMPORADA).getEntrenadores();

        assertEquals(2, entrenadores.size());
        assertEquals("Ancelotti", entrenadores.get(0).getNombre(), "Se ordenan por nombre");
    }

    @Test
    @DisplayName("Se coge la etapa vigente aunque el registro sea de una temporada anterior")
    void etapaQueVieneDeAntes() {
        // El historial guarda la temporada en que EMPIEZA cada etapa, no todas las que cubre.
        // Comprobado con el Real Madrid: hay fila de 2021 (Ancelotti) y la siguiente es de
        // 2025, asi que preguntar por 2024 no devolvia a nadie aunque siguiera en el banquillo.
        when(historialEntrenadorRepository
                .findByEquipo_IdEquipoAndTemporadaLessThanEqualOrderByTemporadaDesc(ID_EQUIPO, TEMPORADA))
                .thenReturn(List.of(historial(1, "Ancelotti", 2021), historial(2, "Zidane", 2019)));

        List<TemporadaEquipoDTO.EntrenadorTemporadaDTO> entrenadores =
                servicio.detalle(ID_EQUIPO, TEMPORADA).getEntrenadores();

        assertEquals(1, entrenadores.size(), "Solo la etapa vigente, no todo el historico");
        assertEquals("Ancelotti", entrenadores.get(0).getNombre());
        // Se manda de que ano sale el dato, para que la interfaz pueda decir "desde 2021".
        assertEquals(2021, entrenadores.get(0).getTemporadaRegistro());
    }

    @Test
    @DisplayName("Una temporada sin datos devuelve la ficha vacia, no un error")
    void temporadaSinDatos() {
        TemporadaEquipoDTO t = servicio.detalle(ID_EQUIPO, TEMPORADA);

        assertTrue(t.getPlantilla().isEmpty());
        assertTrue(t.getTitulos().isEmpty());
        assertEquals(0, t.getTotales().getGoles());
        assertEquals("Real Madrid", t.getEquipo());
    }

    @Test
    @DisplayName("Un equipo que no existe da 404, no una ficha en blanco")
    void equipoInexistente() {
        when(equipoRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> servicio.detalle(999, TEMPORADA));
    }

    @Test
    @DisplayName("Las temporadas salen de los partidos, no de las clasificaciones")
    void temporadasDisponibles() {
        // Hay competiciones sin tabla (copas a partido unico, amistosos) en las que el equipo
        // jugo igual, y esas temporadas tambien tienen que poder abrirse.
        when(partidoRepository.temporadasDeEquipo(ID_EQUIPO)).thenReturn(List.of(2024, 2023, 2022));

        assertEquals(List.of(2024, 2023, 2022), servicio.temporadasDisponibles(ID_EQUIPO));
    }

    private HistorialEntrenador historial(int idEntrenador, String nombre, int temporada) {
        Entrenador entrenador = new Entrenador();
        entrenador.setIdEntrenador(idEntrenador);
        entrenador.setNombre(nombre);
        HistorialEntrenador h = new HistorialEntrenador();
        h.setEntrenador(entrenador);
        h.setTemporada(temporada);
        return h;
    }
}
