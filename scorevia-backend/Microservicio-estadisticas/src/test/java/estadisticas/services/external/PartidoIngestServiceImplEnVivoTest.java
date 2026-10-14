package estadisticas.services.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.dto.external.*;
import estadisticas.entities.ligas.Liga;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.arbitros.ArbitroRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.ligas.LigaRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Cuánta cuota gasta la ingesta en vivo.
 *
 * <p>Un tick del directo es <b>una sola petición</b> ({@code fixtures?live=all}) para todos los
 * partidos del mundo, y esa respuesta ya trae el timeline de cada uno. Lo que estos tests
 * fijan es que no se vuelva a pedir por separado —hubo una versión que lo hacía, con el
 * scheduler a 10s y 1.500 partidos un sábado eran más de 13 millones de peticiones diarias
 * contra una cuota de 150.000— y que escribir el timeline en la base solo ocurra cuando algo
 * se ha movido de verdad.</p>
 */
@ExtendWith(MockitoExtension.class)
class PartidoIngestServiceImplEnVivoTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private LigaRepository ligaRepository;
    @Mock private EquipoRepository equipoRepository;
    @Mock private ArbitroRepository arbitroRepository;
    @Mock private PartidoRepository partidoRepository;
    @Mock private EventoPartidoIngestService eventoPartidoIngestService;
    @Mock private ColaPostPartido colaPostPartido;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private PartidoIngestServiceImpl servicio;

    @BeforeEach
    void setUp() {
        // @Value no se inyecta en un test de Mockito puro.
        ReflectionTestUtils.setField(servicio, "refrescoEventosSegundos", 300L);
        ReflectionTestUtils.setField(servicio, "umbralPartidosVivo", 300);

        lenient().when(partidoRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(partidoRepository.save(any(Partido.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ligaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(equipoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Primer tick: guarda el timeline de cada partido que ve por primera vez")
    void primerTickBajaEventos() {
        responderConVivo(fixture(1, "1H", 0, 0), fixture(2, "1H", 1, 0));

        servicio.ingestarEnVivo();

        verify(eventoPartidoIngestService, times(2)).guardarEventos(any(), any());
    }

    @Test
    @DisplayName("Si no se mueve nada, los ticks siguientes no reescriben el timeline")
    void ticksSinCambiosNoGastanCuota() {
        responderConVivo(fixture(1, "1H", 0, 0), fixture(2, "1H", 1, 0));
        servicio.ingestarEnVivo();
        clearInvocations(eventoPartidoIngestService);

        // Diez ticks más con exactamente el mismo marcador y estado: son 100 segundos de
        // scheduler que antes costaban 20 peticiones y ahora deben costar cero.
        for (int i = 0; i < 10; i++) {
            servicio.ingestarEnVivo();
        }

        verifyNoInteractions(eventoPartidoIngestService);
    }

    @Test
    @DisplayName("Un gol dispara la bajada solo del partido que ha marcado")
    void soloElPartidoQueMarcaGastaPeticion() {
        responderConVivo(fixture(1, "1H", 0, 0), fixture(2, "1H", 1, 0));
        servicio.ingestarEnVivo();
        clearInvocations(eventoPartidoIngestService);

        // El partido 1 marca; el 2 sigue igual.
        responderConVivo(fixture(1, "1H", 1, 0), fixture(2, "1H", 1, 0));
        servicio.ingestarEnVivo();

        verify(eventoPartidoIngestService, times(1))
                .guardarEventos(argThat(p -> p.getIdPartido().equals(1)), any());
        verify(eventoPartidoIngestService, never())
                .guardarEventos(argThat(p -> p.getIdPartido().equals(2)), any());
    }

    @Test
    @DisplayName("El cambio de fase (descanso, final) también cuenta como cambio")
    void elCambioDeFaseDisparaLaBajada() {
        responderConVivo(fixture(1, "1H", 1, 0));
        servicio.ingestarEnVivo();
        clearInvocations(eventoPartidoIngestService);

        responderConVivo(fixture(1, "HT", 1, 0));
        servicio.ingestarEnVivo();

        verify(eventoPartidoIngestService, times(1)).guardarEventos(any(), any());
    }

    @Test
    @DisplayName("Al acabar se baja el timeline una última vez y el partido queda cerrado")
    void alAcabarSeCierraYNoSeVuelveAPedir() {
        responderConVivo(fixture(1, "2H", 2, 1));
        servicio.ingestarEnVivo();
        clearInvocations(eventoPartidoIngestService);

        // Pita el final: una última bajada con el timeline completo.
        responderConVivo(fixture(1, "FT", 2, 1));
        servicio.ingestarEnVivo();
        verify(eventoPartidoIngestService, times(1)).guardarEventos(any(), any());

        // API-Football sigue devolviéndolo un rato en live=all: ya no se pide nada más.
        clearInvocations(eventoPartidoIngestService);
        servicio.ingestarEnVivo();
        servicio.ingestarEnVivo();
        verifyNoInteractions(eventoPartidoIngestService);
    }

    @Test
    @DisplayName("Los partidos que salen del directo se olvidan, para no comerse la memoria")
    void seOlvidanLosPartidosQueYaNoEstanEnVivo() {
        responderConVivo(fixture(1, "1H", 0, 0));
        servicio.ingestarEnVivo();

        // El partido desaparece del feed...
        responderConVivo();
        servicio.ingestarEnVivo();
        clearInvocations(eventoPartidoIngestService);

        // ...y si volviera a aparecer, se trata como nuevo (no queda estado colgando).
        responderConVivo(fixture(1, "1H", 0, 0));
        servicio.ingestarEnVivo();

        verify(eventoPartidoIngestService, times(1)).guardarEventos(any(), any());
    }

    @Test
    @DisplayName("Al pitido final el partido se encola para traerse sus estadísticas")
    void alTerminarSeEncolaParaLasStats() {
        responderConVivo(fixture(7, "2H", 1, 0));
        servicio.ingestarEnVivo();
        verify(colaPostPartido, never()).encolar(any());

        // El mismo partido, ahora acabado: es el único momento en que se sabe que la API ya
        // tiene (o va a tener enseguida) sus estadísticas definitivas.
        responderConVivo(fixture(7, "FT", 1, 0));
        servicio.ingestarEnVivo();

        verify(colaPostPartido).encolar(7);
    }

    @Test
    @DisplayName("Un partido acabado que sigue en el feed no se encola una y otra vez")
    void partidoAcabadoNoSeEncolaEnCadaTick() {
        responderConVivo(fixture(7, "2H", 1, 0));
        servicio.ingestarEnVivo();
        responderConVivo(fixture(7, "FT", 1, 0));
        servicio.ingestarEnVivo();

        // API-Football lo sigue devolviendo un rato en live=all después del final.
        for (int i = 0; i < 5; i++) {
            servicio.ingestarEnVivo();
        }

        verify(colaPostPartido, times(1)).encolar(7);
    }

    @Test
    @DisplayName("Con muchos partidos a la vez, el refresco periódico se estira en proporción")
    void elRefrescoPeriodicoSeEstiraConLaCarga() {
        // El refresco periódico cuesta partidos/intervalo peticiones por segundo. Si el
        // intervalo fuera fijo, 3.000 partidos simultáneos serían 10 peticiones por segundo
        // solo para recoger tarjetas y cambios: el limitador del cliente entero, gastado en
        // lo que menos corre prisa.
        assertEquals(300, servicio.refrescoEventosPara(1).toSeconds());
        assertEquals(300, servicio.refrescoEventosPara(300).toSeconds());
        assertEquals(1500, servicio.refrescoEventosPara(1500).toSeconds());
        assertEquals(3000, servicio.refrescoEventosPara(3000).toSeconds());

        // Que es justo lo que mantiene el gasto plano: partidos / intervalo = 1 pet/s.
        assertEquals(1, 3000 / servicio.refrescoEventosPara(3000).toSeconds());
    }

    @Test
    @DisplayName("El timeline se guarda con los eventos que ya vienen en el feed, sin pedir nada más")
    void losEventosSalenDelPropioFeed() {
        FixtureItemDTO item = fixture(1, "1H", 1, 0);
        ApiFootballEventsResponseDTO.EventItemDTO gol = new ApiFootballEventsResponseDTO.EventItemDTO();
        gol.setType("Goal");
        item.setEvents(List.of(gol));
        responderConVivo(item);

        servicio.ingestarEnVivo();

        // Comprobado contra la API el 2026-09-05: los 58 partidos que devolvió live=all
        // traían su array "events". Pedirlos aparte era pagar dos veces por el mismo dato.
        verify(eventoPartidoIngestService).guardarEventos(any(), eq(List.of(gol)));
        verify(eventoPartidoIngestService, never()).ingestarEventosDePartido(any());
    }

    @Test
    @DisplayName("El JSONB del partido no engorda con el timeline y las alineaciones")
    void elCacheJsonNoGuardaLosBloquesPesados() throws Exception {
        FixtureItemDTO item = fixture(1, "1H", 1, 0);
        ApiFootballEventsResponseDTO.EventItemDTO gol = new ApiFootballEventsResponseDTO.EventItemDTO();
        gol.setType("Goal");
        item.setEvents(List.of(gol));
        responderConVivo(item);

        servicio.ingestarEnVivo();

        ArgumentCaptor<Partido> guardado = ArgumentCaptor.forClass(Partido.class);
        verify(partidoRepository, atLeastOnce()).save(guardado.capture());
        String json = guardado.getAllValues().get(guardado.getAllValues().size() - 1).getDatosApi();

        // partidos.datos_api es una caché del fixture original. El timeline, las alineaciones
        // y las estadísticas ya viven en sus propias tablas: duplicarlas aquí multiplicaría el
        // tamaño de esa columna en cada uno del millón y medio de partidos de la base.
        assertFalse(json.contains("\"events\""), "El JSON cacheado no debe llevar los eventos");
        assertFalse(json.contains("\"lineups\""), "El JSON cacheado no debe llevar las alineaciones");
    }

    @Test
    @DisplayName("Un partido parado por el árbitro sigue estando EN JUEGO, no aplazado")
    void suspendidoEInterrumpidoSiguenEnJuego() {
        // SUSP e INT son "In Play" en la tabla de estados de la documentación: el árbitro para
        // el partido y se reanuda. Marcándolos APLAZADO desaparecían del directo para siempre.
        ArgumentCaptor<Partido> guardado = ArgumentCaptor.forClass(Partido.class);

        responderConVivo(fixture(1, "SUSP", 1, 1), fixture(2, "INT", 0, 0));
        servicio.ingestarEnVivo();

        verify(partidoRepository, atLeastOnce()).save(guardado.capture());
        assertTrue(guardado.getAllValues().stream().allMatch(p -> "EN_JUEGO".equals(p.getEstado())),
                "SUSP e INT deben quedarse en EN_JUEGO");
    }

    @Test
    @DisplayName("Una incomparecencia es un partido terminado y se encola como tal")
    void incomparecenciaEsPartidoTerminado() {
        // AWD (pérdida por decisión) y WO (incomparecencia) acaban CON resultado. Al tratarlos
        // como aplazados no entraban nunca en la cola post-partido y se quedaban sin datos.
        responderConVivo(fixture(3, "1H", 0, 0));
        servicio.ingestarEnVivo();

        responderConVivo(fixture(3, "WO", 3, 0));
        servicio.ingestarEnVivo();

        verify(colaPostPartido).encolar(3);
    }

    // ------------------------------------------------------------------
    private void responderConVivo(FixtureItemDTO... items) {
        ApiFootballFixturesResponseDTO respuesta = new ApiFootballFixturesResponseDTO();
        respuesta.setResponse(List.of(items));
        respuesta.setResults(items.length);
        when(apiFootballClient.getLiveFixtures()).thenReturn(respuesta);
    }

    private FixtureItemDTO fixture(int id, String estado, int golesLocal, int golesVisitante) {
        FixtureItemDTO item = new FixtureItemDTO();

        FixtureDetailDTO detalle = new FixtureDetailDTO();
        detalle.setId(id);
        detalle.setDate("2026-09-04T15:00:00+00:00");
        FixtureStatusDTO status = new FixtureStatusDTO();
        status.setShortStatus(estado);
        detalle.setStatus(status);
        item.setFixture(detalle);

        LeagueInfoDTO liga = new LeagueInfoDTO();
        liga.setId(5);
        liga.setSeason(2026);
        liga.setRound("Regular Season - 3");
        item.setLeague(liga);

        TeamsInfoDTO equipos = new TeamsInfoDTO();
        TeamInfoDTO local = new TeamInfoDTO();
        local.setId(10 * id);
        TeamInfoDTO visitante = new TeamInfoDTO();
        visitante.setId(10 * id + 1);
        equipos.setHome(local);
        equipos.setAway(visitante);
        item.setTeams(equipos);

        GoalsInfoDTO goles = new GoalsInfoDTO();
        goles.setHome(golesLocal);
        goles.setAway(golesVisitante);
        item.setGoals(goles);

        return item;
    }

    // =====================================================================================
    // El minuto congelado
    // =====================================================================================
    // Medido en produccion el 2026-09-08: un partido que empezo hace 212 minutos seguia
    // como EN_JUEGO en el minuto 90, y otros cuatro entre 152 y 182. Cuando la API deja de
    // devolver un partido en live=all, el ultimo minuto se queda clavado, y como casi todos
    // se paran en el 90, el marcador ensenaba varios "en el minuto 90" acabados hacia horas.

    @Test
    @DisplayName("Al partido que el directo ya no devuelve se le borra el minuto")
    void minutoSeBorraCuandoElDirectoNoLoConfirma() {
        Partido colgado = new Partido();
        colgado.setIdPartido(99);
        colgado.setEstado("EN_JUEGO");
        colgado.setMinuto(90);
        when(partidoRepository.findEnJuegoColgados(any())).thenReturn(List.of(colgado));

        // El directo devuelve otro partido distinto: el 99 ya no esta.
        responderConVivo(fixture(1, "1H", 0, 0));
        servicio.ingestarEnVivo();

        assertNull(colgado.getMinuto(),
                "el minuto no lo confirma nadie, no puede seguir enseniandose");
        // El estado NO se toca aqui: de eso se encarga cerrarPartidosColgados, que espera mas.
        assertEquals("EN_JUEGO", colgado.getEstado());
    }

    @Test
    @DisplayName("Si el partido sigue en el directo, su minuto no se toca")
    void minutoSeRespetaMientrasElDirectoLoDevuelve() {
        Partido enJuego = new Partido();
        enJuego.setIdPartido(1);
        enJuego.setEstado("EN_JUEGO");
        enJuego.setMinuto(75);
        when(partidoRepository.findEnJuegoColgados(any())).thenReturn(List.of(enJuego));

        // El 1 SI viene en la respuesta del directo.
        responderConVivo(fixture(1, "2H", 1, 0));
        servicio.ingestarEnVivo();

        assertEquals(75, enJuego.getMinuto(),
                "un partido que sigue en el directo conserva su minuto");
    }

    @Test
    @DisplayName("Sin partidos colgados no se escribe en la base")
    void sinColgadosNoHayEscrituras() {
        when(partidoRepository.findEnJuegoColgados(any())).thenReturn(List.of());

        responderConVivo(fixture(1, "1H", 0, 0));
        servicio.ingestarEnVivo();

        verify(partidoRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Al colgado se le PREGUNTA su estado real, no se le deja EN_JUEGO")
    void alColgadoSeLePreguntaSuEstado() {
        // Antes solo se encolaba, y la cola trae estadisticas pero no toca el estado: el
        // partido seguia EN_JUEGO hasta la reconciliacion, que corre una vez al dia.
        Partido colgado = new Partido();
        colgado.setIdPartido(99);
        colgado.setEstado("EN_JUEGO");
        colgado.setMinuto(90);
        when(partidoRepository.findEnJuegoColgados(any())).thenReturn(List.of(colgado));

        ApiFootballFixturesResponseDTO cierre = new ApiFootballFixturesResponseDTO();
        cierre.setResponse(List.of(fixture(99, "FT", 2, 1)));
        when(apiFootballClient.getFixturesByIds(anyList())).thenReturn(cierre);

        responderConVivo(fixture(1, "1H", 0, 0));
        servicio.ingestarEnVivo();

        verify(apiFootballClient).getFixturesByIds(anyList());
        // Y ademas se encola para traerse estadisticas y timeline, que tampoco tenia.
        verify(colaPostPartido).encolar(99);
    }

    @Test
    @DisplayName("Una competicion destacada refresca su timeline mas a menudo")
    void ligaDestacadaRefrescaAntes() {
        ReflectionTestUtils.setField(servicio, "ligasDestacadasRaw", "140,39");
        ReflectionTestUtils.setField(servicio, "refrescoEventosDestacadasSegundos", 150L);
        ReflectionTestUtils.setField(servicio, "ligasDestacadas", null);

        // Una tarjeta o un cambio en un partido que la gente esta mirando no puede tardar
        // cinco minutos en salir; en una liga regional ese refresco es lo que evita que una
        // tarde con mil partidos se coma la cuota.
        assertEquals(Duration.ofSeconds(150), refrescoDeLigaConId(140));
        assertEquals(Duration.ofSeconds(300), refrescoDeLigaConId(9999));
    }

    /** El refresco que le tocaria a un partido de la liga indicada. */
    private Duration refrescoDeLigaConId(int idLiga) {
        Liga liga = new Liga();
        liga.setIdLiga(idLiga);
        Partido partido = new Partido();
        partido.setLiga(liga);
        return ReflectionTestUtils.invokeMethod(servicio, "refrescoEventosDe", partido);
    }
}
