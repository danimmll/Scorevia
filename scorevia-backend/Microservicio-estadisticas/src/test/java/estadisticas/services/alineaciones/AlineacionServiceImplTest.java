package estadisticas.services.alineaciones;

import estadisticas.dto.alineaciones.AlineacionesPartidoDTO;
import estadisticas.dto.alineaciones.EquipoAlineacionDTO;
import estadisticas.dto.alineaciones.JugadorAlineacionDTO;
import estadisticas.entities.arbitros.Arbitro;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.entrenadores.HistorialEntrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;
import estadisticas.entities.partidos.Partido;
import estadisticas.exception.NotFoundException;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.entrenadores.HistorialEntrenadorRepository;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlineacionServiceImplTest {

    private static final int ID_PARTIDO = 293;
    private static final int ID_LOCAL = 38;
    private static final int ID_VISITANTE = 44;

    @Mock private PartidoRepository partidoRepository;
    @Mock private EstadisticaJugadorPartidoRepository estJugadorRepository;
    @Mock private EntrenadorRepository entrenadorRepository;
    @Mock private EstadisticaEquipoPartidoRepository estEquipoRepository;
    @Mock private HistorialEntrenadorRepository historialEntrenadorRepository;

    @InjectMocks private AlineacionServiceImpl service;

    // ------------------------------------------------------------------------- ayudas ----

    private Equipo equipo(int id, String nombre) {
        Equipo e = new Equipo();
        e.setIdEquipo(id);
        e.setNombre(nombre);
        return e;
    }

    private Partido partido(Integer temporada, Arbitro arbitro) {
        Partido p = new Partido();
        p.setIdPartido(ID_PARTIDO);
        p.setEquipoLocal(equipo(ID_LOCAL, "Local FC"));
        p.setEquipoVisitante(equipo(ID_VISITANTE, "Visitante CF"));
        p.setTemporada(temporada);
        p.setArbitro(arbitro);
        return p;
    }

    private EstadisticaJugadorPartido stat(int idJugador, int idEquipo, String linea, boolean titular, Integer minutos) {
        Jugador jugador = new Jugador();
        jugador.setIdJugador(idJugador);
        jugador.setNombre("Jugador " + idJugador);
        jugador.setFotoUrl("foto-" + idJugador + ".png");
        jugador.setDorsal(idJugador);

        EstadisticaJugadorPartido s = new EstadisticaJugadorPartido();
        s.setJugador(jugador);
        s.setEquipo(equipo(idEquipo, "Equipo " + idEquipo));
        s.setPosicionCampo(linea);
        s.setTitular(titular);
        s.setMinutosJugados(minutos);
        return s;
    }

    /**
     * Una estadistica de un partido antiguo: sin posicion en el partido, pero con la posicion
     * natural del jugador en su ficha. Es el 42% de las filas de la base.
     */
    private EstadisticaJugadorPartido statAntigua(int idJugador, int idEquipo, String posicionNatural,
                                                   boolean titular) {
        EstadisticaJugadorPartido s = stat(idJugador, idEquipo, null, titular, 90);
        s.getJugador().setPosicion(posicionNatural);
        return s;
    }

    /** Un once con la distribución pedida, más los suplentes que se indiquen. */
    private List<EstadisticaJugadorPartido> once(int idEquipo, int defensas, int medios, int delanteros, int suplentes) {
        List<EstadisticaJugadorPartido> lista = new ArrayList<>();
        int id = idEquipo * 100;
        lista.add(stat(++id, idEquipo, "G", true, 90));
        for (int i = 0; i < defensas; i++) lista.add(stat(++id, idEquipo, "D", true, 90));
        for (int i = 0; i < medios; i++) lista.add(stat(++id, idEquipo, "M", true, 90));
        for (int i = 0; i < delanteros; i++) lista.add(stat(++id, idEquipo, "F", true, 90));
        for (int i = 0; i < suplentes; i++) lista.add(stat(++id, idEquipo, "M", false, i * 10));
        return lista;
    }

    // ------------------------------------------------------------------------- tests ----

    @Test
    void getAlineaciones_DeduceElDibujoContandoTitularesPorLinea() {
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2024, null)));
        List<EstadisticaJugadorPartido> stats = new ArrayList<>(once(ID_LOCAL, 4, 3, 3, 4));
        stats.addAll(once(ID_VISITANTE, 5, 3, 2, 3));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(stats);

        AlineacionesPartidoDTO dto = service.getAlineacionesPartido(ID_PARTIDO);

        assertEquals("4-3-3", dto.getLocal().getFormacion());
        assertEquals("5-3-2", dto.getVisitante().getFormacion());
        // El portero no entra en el dibujo, pero sí en el once.
        assertEquals(11, dto.getLocal().getTitulares().size());
        assertEquals(4, dto.getLocal().getSuplentes().size());
        assertTrue(dto.getHayDatos());
        assertTrue(dto.getLocal().getFormacionDeducida());
    }

    @Test
    void getAlineaciones_OrdenaElOnceDePorteroADelanteros() {
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2024, null)));
        // Llegan desordenados a propósito: la ingesta no garantiza ningún orden.
        List<EstadisticaJugadorPartido> stats = List.of(
                stat(1, ID_LOCAL, "F", true, 90),
                stat(2, ID_LOCAL, "G", true, 90),
                stat(3, ID_LOCAL, "M", true, 90),
                stat(4, ID_LOCAL, "D", true, 90));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(stats);

        List<String> lineas = service.getAlineacionesPartido(ID_PARTIDO).getLocal().getTitulares()
                .stream().map(j -> j.getLinea()).toList();

        assertEquals(List.of("G", "D", "M", "F"), lineas);
    }

    @Test
    void getAlineaciones_SuplentesPrimeroLosQueJugaronMasMinutos() {
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2024, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of(
                stat(1, ID_LOCAL, "M", false, null),   // ni saltó al campo
                stat(2, ID_LOCAL, "F", false, 30),
                stat(3, ID_LOCAL, "D", false, 5)));

        List<Integer> ids = service.getAlineacionesPartido(ID_PARTIDO).getLocal().getSuplentes()
                .stream().map(j -> j.getIdJugador()).toList();

        assertEquals(List.of(2, 3, 1), ids);
    }

    @Test
    void getAlineaciones_SinPosicionesRegistradas_NoInventaDibujo() {
        // Partidos antiguos donde posicion_campo llegó vacía: mejor sin dibujo que un "0-0-0".
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2010, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of(
                stat(1, ID_LOCAL, null, true, 90),
                stat(2, ID_LOCAL, "", true, 90)));

        EquipoAlineacionDTO local = service.getAlineacionesPartido(ID_PARTIDO).getLocal();

        assertNull(local.getFormacion());
        assertEquals(2, local.getTitulares().size());
    }

    @Test
    void getAlineaciones_PartidoSinJugadores_LoDiceEnLugarDeParecerVacio() {
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2026, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of());

        AlineacionesPartidoDTO dto = service.getAlineacionesPartido(ID_PARTIDO);

        assertFalse(dto.getHayDatos());
        assertTrue(dto.getLocal().getTitulares().isEmpty());
        assertNull(dto.getLocal().getFormacion());
    }

    @Test
    void getAlineaciones_PrefiereElEntrenadorDeLaTemporadaDelPartido() {
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2015, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of());

        Entrenador deLaEpoca = new Entrenador();
        deLaEpoca.setIdEntrenador(7);
        deLaEpoca.setNombre("El de 2015");
        HistorialEntrenador historial = new HistorialEntrenador();
        historial.setEntrenador(deLaEpoca);
        when(historialEntrenadorRepository.findByEquipo_IdEquipoAndTemporada(ID_LOCAL, 2015))
                .thenReturn(List.of(historial));
        when(historialEntrenadorRepository.findByEquipo_IdEquipoAndTemporada(ID_VISITANTE, 2015))
                .thenReturn(List.of());
        // El actual solo se consulta para el equipo que no tiene histórico de esa temporada.
        lenient().when(entrenadorRepository.findByEquipo_IdEquipo(any())).thenReturn(List.of());

        EquipoAlineacionDTO local = service.getAlineacionesPartido(ID_PARTIDO).getLocal();

        assertEquals(7, local.getEntrenador().getIdEntrenador());
        assertFalse(local.getEntrenadorEsElActual());
    }

    @Test
    void getAlineaciones_SinHistoricoCaeAlEntrenadorActualYLoAvisa() {
        // Sin el aviso, la ficha de un partido de hace diez años afirmaría que lo dirigió
        // quien está hoy en el banquillo.
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2012, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of());
        when(historialEntrenadorRepository.findByEquipo_IdEquipoAndTemporada(any(), any()))
                .thenReturn(List.of());

        Entrenador actual = new Entrenador();
        actual.setIdEntrenador(99);
        actual.setNombre("El de ahora");
        when(entrenadorRepository.findByEquipo_IdEquipo(ID_LOCAL)).thenReturn(List.of(actual));
        when(entrenadorRepository.findByEquipo_IdEquipo(ID_VISITANTE)).thenReturn(List.of());

        AlineacionesPartidoDTO dto = service.getAlineacionesPartido(ID_PARTIDO);

        assertEquals(99, dto.getLocal().getEntrenador().getIdEntrenador());
        assertTrue(dto.getLocal().getEntrenadorEsElActual());
        // Sin entrenador no se marca la bandera: no hay nada que matizar.
        assertNull(dto.getVisitante().getEntrenador());
        assertFalse(dto.getVisitante().getEntrenadorEsElActual());
    }

    @Test
    void getAlineaciones_ArbitroSinPais_LoDevuelveNuloYNoComoCadenaVacia() {
        Arbitro arbitro = new Arbitro();
        arbitro.setIdArbitro(5);
        arbitro.setNombre("A. Colegiado");
        // La columna es NOT NULL DEFAULT '', así que "sin país" llega como cadena vacía.
        arbitro.setPais("");
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2024, arbitro)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of());

        AlineacionesPartidoDTO dto = service.getAlineacionesPartido(ID_PARTIDO);

        assertEquals(5, dto.getArbitro().getIdArbitro());
        assertNull(dto.getArbitro().getPais());
    }

    @Test
    void getAlineaciones_PartidoInexistente_LanzaNotFound() {
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getAlineacionesPartido(ID_PARTIDO));
    }

    @Test
    @DisplayName("Con alineación oficial se usa su dibujo y su entrenador, no los deducidos")
    void prefiereLaAlineacionOficial() {
        Entrenador tecnicoDelPartido = new Entrenador();
        tecnicoDelPartido.setIdEntrenador(700);
        tecnicoDelPartido.setNombre("Técnico del partido");

        EstadisticaEquipoPartido fila = new EstadisticaEquipoPartido();
        fila.setSistemaTactico("4-2-3-1");
        fila.setIdEntrenador(700);

        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2024, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of(
                stat(1, ID_LOCAL, "G", true, 90),
                stat(2, ID_LOCAL, "D", true, 90)));
        when(estEquipoRepository.findByPartido_IdPartidoAndEquipo_IdEquipo(ID_PARTIDO, ID_LOCAL))
                .thenReturn(Optional.of(fila));
        when(entrenadorRepository.findById(700)).thenReturn(Optional.of(tecnicoDelPartido));

        AlineacionesPartidoDTO alineaciones = service.getAlineacionesPartido(ID_PARTIDO);
        EquipoAlineacionDTO local = alineaciones.getLocal();

        // El dibujo oficial gana al contado a mano, y el aviso de "deducida" desaparece.
        assertEquals("4-2-3-1", local.getFormacion());
        assertFalse(local.getFormacionDeducida());
        // Y el entrenador es el de ESE partido, no el que dirige hoy al club.
        assertEquals("Técnico del partido", local.getEntrenador().getNombre());
        assertFalse(local.getEntrenadorEsElActual());
    }

    // ------------------------------- alineaciones de partidos antiguos ----
    //
    // En 2.932.997 de 7.055.261 filas (42%) `posicion_campo` llega vacia: son los partidos de
    // temporadas viejas, donde la API da el once y el banquillo pero no el puesto de cada uno.
    // Sin respaldo, esos partidos se quedaban en una lista de nombres sin dibujo.

    @Test
    @DisplayName("Sin posicion en el partido, el dibujo sale de la posicion natural del jugador")
    void getAlineaciones_DeduceElDibujoConLaPosicionNatural() {
        List<EstadisticaJugadorPartido> once = new ArrayList<>();
        once.add(statAntigua(1, ID_LOCAL, "Goalkeeper", true));
        for (int i = 0; i < 4; i++) once.add(statAntigua(10 + i, ID_LOCAL, "Defender", true));
        for (int i = 0; i < 4; i++) once.add(statAntigua(20 + i, ID_LOCAL, "Midfielder", true));
        for (int i = 0; i < 2; i++) once.add(statAntigua(30 + i, ID_LOCAL, "Attacker", true));

        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2015, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(once);

        AlineacionesPartidoDTO dto = service.getAlineacionesPartido(ID_PARTIDO);

        assertEquals("4-4-2", dto.getLocal().getFormacion());
        assertEquals(Boolean.TRUE, dto.getLocal().getFormacionDeducida(), "No es el dibujo oficial y hay que decirlo");
    }

    @Test
    @DisplayName("La posicion del partido manda sobre la natural cuando existe")
    void getAlineaciones_LaPosicionDelPartidoTienePrioridad() {
        // Un defensa que jugo de delantero en ese partido cuenta como delantero: el dato del
        // partido es mas preciso que su ficha.
        EstadisticaJugadorPartido reconvertido = stat(1, ID_LOCAL, "F", true, 90);
        reconvertido.getJugador().setPosicion("Defender");

        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2024, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO))
                .thenReturn(List.of(reconvertido));

        AlineacionesPartidoDTO dto = service.getAlineacionesPartido(ID_PARTIDO);

        assertEquals("0-0-1", dto.getLocal().getFormacion());
    }

    @Test
    @DisplayName("\"Forward\" cuenta como delantero, igual que \"Attacker\"")
    void getAlineaciones_ForwardEsDelantero() {
        // 573 fichas de la base usan "Forward" en vez de "Attacker"; son lo mismo.
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2015, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO))
                .thenReturn(List.of(statAntigua(1, ID_LOCAL, "Forward", true)));

        assertEquals("0-0-1", service.getAlineacionesPartido(ID_PARTIDO).getLocal().getFormacion());
    }

    @Test
    @DisplayName("El once antiguo se ordena de portero a delanteros, no en el orden que llegue")
    void getAlineaciones_OrdenaElOnceAntiguoPorPosicionNatural() {
        // Si el orden mirara solo la posicion del partido, todos serian "sin linea" y saldrian
        // en el orden de llegada, con el portero en medio.
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2015, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO)).thenReturn(List.of(
                statAntigua(1, ID_LOCAL, "Attacker", true),
                statAntigua(2, ID_LOCAL, "Goalkeeper", true),
                statAntigua(3, ID_LOCAL, "Midfielder", true),
                statAntigua(4, ID_LOCAL, "Defender", true)));

        List<JugadorAlineacionDTO> titulares = service.getAlineacionesPartido(ID_PARTIDO)
                .getLocal().getTitulares();

        assertEquals(List.of("G", "D", "M", "F"),
                titulares.stream().map(JugadorAlineacionDTO::getLinea).toList());
    }

    @Test
    @DisplayName("Una posicion que no se reconoce no coloca a nadie donde no toca")
    void getAlineaciones_PosicionNaturalDesconocida() {
        // En la base hay fichas con la posicion a "U", "19" o "25". No se inventa una linea.
        when(partidoRepository.findById(ID_PARTIDO)).thenReturn(Optional.of(partido(2015, null)));
        when(estJugadorRepository.findByPartidoIdPartido(ID_PARTIDO))
                .thenReturn(List.of(statAntigua(1, ID_LOCAL, "19", true)));

        AlineacionesPartidoDTO dto = service.getAlineacionesPartido(ID_PARTIDO);

        assertNull(dto.getLocal().getTitulares().get(0).getLinea());
        assertNull(dto.getLocal().getFormacion(), "Mejor sin dibujo que un 0-0-0");
    }
}
