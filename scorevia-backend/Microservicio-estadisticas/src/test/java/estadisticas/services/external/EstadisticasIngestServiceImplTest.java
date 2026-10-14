package estadisticas.services.external;

import estadisticas.dto.external.*;
import java.util.Map;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.ligas.Liga;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;
import estadisticas.entities.partidos.Partido;
import java.time.LocalDateTime;
import java.time.ZoneId;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstadisticasIngestServiceImplTest {

    @Mock private ApiFootballClient apiClient;
    @Mock private PartidoRepository partidoRepository;
    @Mock private EstadisticaEquipoPartidoRepository estEquipoRepository;
    @Mock private JugadorRepository jugadorRepository;
    @Mock private EstadisticaJugadorPartidoRepository estJugadorRepository;
    @Mock
    private EventoPartidoIngestService eventoPartidoIngestService;

    @InjectMocks private EstadisticasIngestServiceImpl estadisticasIngestService;

    private Partido partido;

    @BeforeEach
    void setUp() {
        partido = new Partido();
        partido.setIdPartido(1);

        Equipo local = new Equipo(); local.setIdEquipo(10);
        Equipo visitante = new Equipo(); visitante.setIdEquipo(20);
        partido.setEquipoLocal(local);
        partido.setEquipoVisitante(visitante);
    }

    @Test
    void ingestarEstadisticasPendientes_SinPendientes_TerminaRapido() {
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findPartidosPendientesDeStats(any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        int procesados = estadisticasIngestService.ingestarEstadisticasPendientes();

        assertEquals(0, procesados);
    }

    @Test
    void ingestarEstadisticasPendientes_CuotaAgotada_Break() {
        // Devuelve true para el while, y false para el interior del for
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true, false);
        when(partidoRepository.findPartidosPendientesDeStats(any(), any(Pageable.class)))
                .thenReturn(List.of(partido));

        int procesados = estadisticasIngestService.ingestarEstadisticasPendientes();
        assertEquals(0, procesados);
    }

    @Test
    void ingestarEstadisticasPendientes_ApiVacia_SumaIntentos() {
        // Necesitamos 3 true: 1 para el while, 1 para el for, 1 para la sig. ronda del while
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true, true, false);
        when(partidoRepository.findPartidosPendientesDeStats(any(), any(Pageable.class)))
                .thenReturn(List.of(partido));

        responderConFixture(List.of(), List.of());

        int procesados = estadisticasIngestService.ingestarEstadisticasPendientes();

        assertEquals(0, procesados);
        verify(partidoRepository).save(partido);
        assertEquals(1, partido.getIntentosIngesta());
    }

    @Test
    void ingestarEstadisticasPendientes_FallaLaPeticion_NoQuemaIntentos() {
        // Si se cae la petición, se caen los veinte partidos de la tanda a la vez. Anotarles
        // un intento fallido a cada uno por un problema que no es suyo sería la forma de
        // marcar media base como "sin estadísticas" durante una caída de la API.
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true, true, false);
        when(partidoRepository.findPartidosPendientesDeStats(any(), any(Pageable.class)))
                .thenReturn(List.of(partido));
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(List.of(partido));
        when(apiClient.getFixturesByIds(any())).thenThrow(new RuntimeException("API caída"));

        partido.setIntentosIngesta(2);
        estadisticasIngestService.ingestarEstadisticasPendientes();

        assertEquals(2, partido.getIntentosIngesta());
        assertNotEquals(Boolean.TRUE, partido.getStatsNotFound());
    }

    @Test
    void ingestarEstadisticasPendientes_RespuestaSinEsePartido_SumaIntentos() {
        // La petición fue bien pero la API no trae nada de este partido: eso sí es suyo.
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true, true, false);
        when(partidoRepository.findPartidosPendientesDeStats(any(), any(Pageable.class)))
                .thenReturn(List.of(partido));
        responderConFixture(List.of(), List.of());

        partido.setIntentosIngesta(2); // Al sumar 1, llegará al máximo (3)
        estadisticasIngestService.ingestarEstadisticasPendientes();

        assertEquals(3, partido.getIntentosIngesta());
        assertTrue(partido.getStatsNotFound()); // Se bloquea
        verify(partidoRepository).save(partido);
    }

    @Test
    void ingestarEstadisticasPendientes_Exito_CubreRamas() {
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true, true, false);
        when(partidoRepository.findPartidosPendientesDeStats(any(), any(Pageable.class)))
                .thenReturn(List.of(partido));

        // == TEAM STATS ==
        ApiTeamStatsResponseDTO teamRes = new ApiTeamStatsResponseDTO();
        ApiTeamStatsResponseDTO.TeamStatsWrapper twLocal = new ApiTeamStatsResponseDTO.TeamStatsWrapper();
        TeamInfoDTO tiLocal = new TeamInfoDTO(); tiLocal.setId(10); twLocal.setTeam(tiLocal);
        twLocal.setStatistics(List.of(
                crearStat("Ball Possession", "60%"),
                crearStat("Total Shots", "15"),
                crearStat("Shots on Goal", "5"),
                crearStat("Corner Kicks", "8"),
                crearStat("Fouls", "10"),
                crearStat("Yellow Cards", "2"),
                crearStat("Red Cards", "0"),
                crearStat("expected_goals", "2.5"),
                crearStat("Unknown", "100"), // Switch default
                crearStat("Total Shots", "Invalido"), // NumberFormatException
                crearStat("NullValue", null) // Valor nulo
        ));

        // Cubrimos rama de equipo que no pertenece al partido
        ApiTeamStatsResponseDTO.TeamStatsWrapper twDesconocido = new ApiTeamStatsResponseDTO.TeamStatsWrapper();
        TeamInfoDTO tiDesc = new TeamInfoDTO(); tiDesc.setId(99); twDesconocido.setTeam(tiDesc);
        twDesconocido.setStatistics(List.of(crearStat("Total Shots", "1")));

        teamRes.setResponse(List.of(twLocal, twDesconocido));
        when(estEquipoRepository.findByPartidoAndEquipo(any(), any())).thenReturn(Optional.of(new EstadisticaEquipoPartido()));

        // == PLAYER STATS ==
        ApiPlayerStatsResponseDTO playRes = new ApiPlayerStatsResponseDTO();
        ApiPlayerStatsResponseDTO.TeamPlayersWrapper pw = new ApiPlayerStatsResponseDTO.TeamPlayersWrapper();
        pw.setTeam(tiLocal);

        // Jugador normal
        ApiPlayerStatsResponseDTO.PlayerStatsWrapper p1 = getPlayerStatsWrapper();

        // Jugador que provoca NumberFormatException en rating
        ApiPlayerStatsResponseDTO.PlayerStatsWrapper p2 = getStatsWrapper();

        pw.setPlayers(List.of(p1, p2));

        ApiPlayerStatsResponseDTO.TeamPlayersWrapper pwDesc = new ApiPlayerStatsResponseDTO.TeamPlayersWrapper();
        pwDesc.setTeam(tiDesc);
        pwDesc.setPlayers(List.of(p1));

        playRes.setResponse(List.of(pw, pwDesc));
        responderConFixture(teamRes.getResponse(), playRes.getResponse());

        when(jugadorRepository.findAllById(any())).thenReturn(List.of(new Jugador()));
        when(estJugadorRepository.findByPartidoIdPartido(any())).thenReturn(List.of());

        int procesados = estadisticasIngestService.ingestarEstadisticasPendientes();

        assertEquals(1, procesados);
        verify(partidoRepository).save(partido);
        assertFalse(partido.getStatsNotFound()); // Se guardó correctamente
    }

    @Test
    void ingestarEstadisticasPendientes_PaginaLlenaTodosIgnorados_AvanzaDePaginaEnVezDeParar() {
        // Regresión Fix 4: si los 500 partidos más antiguos de la página 0 fallan todos en
        // la misma ronda, la ingesta no debe darse por terminada (paginación fija en 0),
        // debe avanzar a la página 1 para no dejar varados partidos pendientes reales que
        // existen más allá del lote de 500.
        int tamanoLote = 500;
        List<Partido> partidosPagina0 = IntStream.rangeClosed(1, tamanoLote)
                .mapToObj(this::crearPartidoConId)
                .toList();

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(partidosPagina0);
        when(apiClient.getFixturesByIds(any())).thenThrow(new RuntimeException("API caída"));

        when(partidoRepository.findPartidosPendientesDeStats(any(), argThat(p -> p != null && p.getPageNumber() == 0)))
                .thenReturn(partidosPagina0);
        when(partidoRepository.findPartidosPendientesDeStats(any(), argThat(p -> p != null && p.getPageNumber() == 1)))
                .thenReturn(Collections.emptyList());

        int procesados = estadisticasIngestService.ingestarEstadisticasPendientes();

        assertEquals(0, procesados);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(partidoRepository, atLeast(2)).findPartidosPendientesDeStats(any(), pageableCaptor.capture());
        assertTrue(pageableCaptor.getAllValues().stream().anyMatch(p -> p.getPageNumber() == 1),
                "Debe haber consultado la página 1 tras agotar (con fallos) la página 0 llena");
    }

    @Test
    void ingestarEstadisticasPendientes_PartidoMasAntiguoQueElUltimoGateado_NoPisaElEquipo() {
        // Regresión: un backfill que procese un partido más antiguo que el último que ya
        // fijó el club del jugador no debe pisarlo con uno obsoleto.
        partido.setFecha(java.time.LocalDateTime.of(2025, java.time.Month.JANUARY, 1, 0, 0));

        Equipo equipoOriginal = new Equipo();
        equipoOriginal.setIdEquipo(999);
        Jugador jugadorExistente = new Jugador();
        jugadorExistente.setIdJugador(1); // coincide con getPlayerStatsWrapper()
        jugadorExistente.setEquipo(equipoOriginal);
        jugadorExistente.setFechaEquipoActualizado(java.time.LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 0, 0));

        prepararMocksIngestaConJugador(jugadorExistente);

        estadisticasIngestService.ingestarEstadisticasPendientes();

        ArgumentCaptor<List<Jugador>> captor = ArgumentCaptor.forClass(List.class);
        verify(jugadorRepository).saveAll(captor.capture());
        Jugador guardado = captor.getValue().stream()
                .filter(j -> j.getIdJugador().equals(1)).findFirst().orElseThrow();
        assertEquals(999, guardado.getEquipo().getIdEquipo(), "No debe pisar el equipo con uno de un partido más antiguo");
        assertEquals(java.time.LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 0, 0), guardado.getFechaEquipoActualizado());
    }

    @Test
    void ingestarEstadisticasPendientes_PartidoMasRecienteQueElUltimoGateado_ActualizaElEquipo() {
        partido.setFecha(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 1, 0, 0));

        Equipo equipoOriginal = new Equipo();
        equipoOriginal.setIdEquipo(999);
        Jugador jugadorExistente = new Jugador();
        jugadorExistente.setIdJugador(1);
        jugadorExistente.setEquipo(equipoOriginal);
        jugadorExistente.setFechaEquipoActualizado(java.time.LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 0, 0));

        prepararMocksIngestaConJugador(jugadorExistente);

        estadisticasIngestService.ingestarEstadisticasPendientes();

        ArgumentCaptor<List<Jugador>> captor = ArgumentCaptor.forClass(List.class);
        verify(jugadorRepository).saveAll(captor.capture());
        Jugador guardado = captor.getValue().stream()
                .filter(j -> j.getIdJugador().equals(1)).findFirst().orElseThrow();
        assertEquals(10, guardado.getEquipo().getIdEquipo(), "Un partido más reciente sí debe actualizar el equipo");
        assertEquals(java.time.LocalDateTime.of(2026, java.time.Month.JUNE, 1, 0, 0), guardado.getFechaEquipoActualizado());
    }

    private void prepararMocksIngestaConJugador(Jugador jugadorExistente) {
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true, true, false);
        when(partidoRepository.findPartidosPendientesDeStats(any(), any(Pageable.class)))
                .thenReturn(List.of(partido));

        ApiTeamStatsResponseDTO teamRes = new ApiTeamStatsResponseDTO();
        ApiTeamStatsResponseDTO.TeamStatsWrapper tw = new ApiTeamStatsResponseDTO.TeamStatsWrapper();
        TeamInfoDTO ti = new TeamInfoDTO(); ti.setId(10); tw.setTeam(ti);
        tw.setStatistics(List.of(crearStat("Total Shots", "5")));
        teamRes.setResponse(List.of(tw));
        when(estEquipoRepository.findByPartidoAndEquipo(any(), any())).thenReturn(Optional.of(new EstadisticaEquipoPartido()));

        ApiPlayerStatsResponseDTO playRes = new ApiPlayerStatsResponseDTO();
        ApiPlayerStatsResponseDTO.TeamPlayersWrapper pw = new ApiPlayerStatsResponseDTO.TeamPlayersWrapper();
        pw.setTeam(ti);
        pw.setPlayers(List.of(getPlayerStatsWrapper()));
        playRes.setResponse(List.of(pw));
        responderConFixture(teamRes.getResponse(), playRes.getResponse());

        when(jugadorRepository.findAllById(any())).thenReturn(List.of(jugadorExistente));
        when(estJugadorRepository.findByPartidoIdPartido(any())).thenReturn(List.of());
    }

    // =================================================================================
    // Ingesta de UN partido (la que dispara el pitido final)
    // =================================================================================

    @Test
    void ingestarEstadisticasDePartido_CompeticionSinCobertura_NoGastaNiUnaPeticion() {
        // De los partidos de 2026 ya acabados en la base, 9 de cada 10 son de competiciones
        // que API-Football no cubre. Preguntar por ellos son 2 peticiones para guardar nada.
        Liga sinCobertura = new Liga();
        sinCobertura.setIdLiga(4);
        sinCobertura.setCoberturaStatsPartido(false);
        partido.setLiga(sinCobertura);
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(List.of(partido));

        ResultadoIngestaPartido resultado = estadisticasIngestService.ingestarEstadisticasDePartido(1);

        assertEquals(ResultadoIngestaPartido.SIN_COBERTURA, resultado);
        verify(apiClient, never()).getFixturesByIds(any());
    }

    @Test
    void ingestarEstadisticasDePartido_CoberturaDesconocida_SeIntentaIgual() {
        // Liga que todavía no ha pasado por una sincronización de /leagues: cobertura null es
        // "no se sabe", y bloquearla dejaría fuera a las ligas nuevas para siempre.
        partido.setLiga(new Liga());
        responderConFixture(List.of(), List.of());

        ResultadoIngestaPartido resultado = estadisticasIngestService.ingestarEstadisticasDePartido(1);

        assertEquals(ResultadoIngestaPartido.SIN_DATOS, resultado);
        verify(apiClient).getFixturesByIds(List.of(1));
    }

    @Test
    void ingestarEstadisticasDePartido_SinDatos_NoQuemaElContadorDeIntentos() {
        // Los tres intentos de la cola caen en la media hora siguiente al pitido. Si contaran
        // como fallos, un partido cuyas stats la API publica tarde quedaría marcado
        // "sin estadísticas" y ni la recogida diaria ni un backfill volverían a mirarlo.
        partido.setLiga(new Liga());
        partido.setIntentosIngesta(0);
        responderConFixture(List.of(), List.of());

        estadisticasIngestService.ingestarEstadisticasDePartido(1);

        assertEquals(0, partido.getIntentosIngesta());
        assertNotEquals(Boolean.TRUE, partido.getStatsNotFound());
        verify(partidoRepository, never()).save(any(Partido.class));
    }

    @Test
    void ingestarEstadisticasDePartido_PartidoDesconocido_NoEncontrado() {
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(List.of());

        assertEquals(ResultadoIngestaPartido.NO_ENCONTRADO,
                estadisticasIngestService.ingestarEstadisticasDePartido(999));
        verify(apiClient, never()).getFixturesByIds(any());
    }

    @Test
    void ingestarLoteDePartidos_VeintePartidos_UnaSolaPeticion() {
        // Lo que hace viable el rellenado: /fixtures?ids= admite 20 partidos por petición y
        // cada uno viene con sus estadísticas y sus eventos dentro. Antes eran 3 peticiones
        // por partido; aquí son 60 peticiones convertidas en 1.
        List<Integer> ids = IntStream.rangeClosed(1, 20).boxed().toList();
        List<Partido> partidos = ids.stream().map(this::crearPartidoConId).toList();
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(partidos);
        when(apiClient.getFixturesByIds(any())).thenReturn(new ApiFootballFixturesResponseDTO());

        Map<Integer, ResultadoIngestaPartido> resultados = estadisticasIngestService.ingestarLoteDePartidos(ids);

        verify(apiClient, times(1)).getFixturesByIds(any());
        assertEquals(20, resultados.size());
    }

    @Test
    void ingestarLoteDePartidos_MasDeVeinte_SeParteEnTandas() {
        List<Integer> ids = IntStream.rangeClosed(1, 45).boxed().toList();
        List<Partido> partidos = ids.stream().map(this::crearPartidoConId).toList();
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(partidos);
        when(apiClient.getFixturesByIds(any())).thenReturn(new ApiFootballFixturesResponseDTO());

        estadisticasIngestService.ingestarLoteDePartidos(ids);

        // 20 + 20 + 5: el tope de la API no se puede superar, la respuesta ignoraría el resto
        // sin avisar y esos partidos quedarían marcados como "sin datos" sin haberse pedido.
        verify(apiClient, times(3)).getFixturesByIds(any());
    }

    @Test
    void ingestarEstadisticasRecientes_SinPendientes_NoGastaCuota() {
        when(partidoRepository.findPartidosRecientesPendientesDeStats(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        assertEquals(0, estadisticasIngestService.ingestarEstadisticasRecientes(30, 1500));
        verify(apiClient, never()).getFixturesByIds(any());
    }


    // =================================================================================
    // Ayudas: ahora un partido se pide con /fixtures?ids=, y sus estadísticas de equipo,
    // de jugador y sus eventos vienen dentro de esa misma respuesta.
    // =================================================================================

    /** Deja preparado el partido de este test como si la API lo devolviera con estos datos. */
    private void responderConFixture(List<ApiTeamStatsResponseDTO.TeamStatsWrapper> statsEquipo,
                                     List<ApiPlayerStatsResponseDTO.TeamPlayersWrapper> statsJugadores) {
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(List.of(partido));

        FixtureItemDTO item = new FixtureItemDTO();
        FixtureDetailDTO detalle = new FixtureDetailDTO();
        detalle.setId(partido.getIdPartido());
        item.setFixture(detalle);
        item.setStatistics(statsEquipo);
        item.setPlayers(statsJugadores);

        ApiFootballFixturesResponseDTO respuesta = new ApiFootballFixturesResponseDTO();
        respuesta.setResponse(List.of(item));
        when(apiClient.getFixturesByIds(any())).thenReturn(respuesta);
    }

    @Test
    void ingestarLoteDePartidos_JugadorRepetidoEnLaRespuesta_NoDuplicaSuFila() {
        // API-Football lista a veces al mismo jugador dos veces en el mismo partido. Antes,
        // cada aparición creaba su propia EstadisticaJugadorPartido y el INSERT se caía con
        // "duplicate key ... estadisticas_jugador_partido_id_partido_id_jugador_key": el
        // partido entero se perdía y, a los tres intentos, quedaba marcado como "sin
        // estadísticas" para siempre aunque la API sí las tuviera.
        partido.setLiga(new Liga());

        ApiTeamStatsResponseDTO.TeamStatsWrapper tw = new ApiTeamStatsResponseDTO.TeamStatsWrapper();
        TeamInfoDTO ti = new TeamInfoDTO();
        ti.setId(10);
        tw.setTeam(ti);
        tw.setStatistics(List.of(crearStat("Total Shots", "5")));

        ApiPlayerStatsResponseDTO.TeamPlayersWrapper pw = new ApiPlayerStatsResponseDTO.TeamPlayersWrapper();
        pw.setTeam(ti);
        // El MISMO jugador (id 1) dos veces.
        pw.setPlayers(List.of(getPlayerStatsWrapper(), getPlayerStatsWrapper()));

        responderConFixture(List.of(tw), List.of(pw));
        when(estEquipoRepository.findByPartidoAndEquipo(any(), any()))
                .thenReturn(Optional.of(new EstadisticaEquipoPartido()));
        when(jugadorRepository.findAllById(any())).thenReturn(List.of());
        when(estJugadorRepository.findByPartidoIdPartido(any())).thenReturn(List.of());

        ResultadoIngestaPartido resultado = estadisticasIngestService.ingestarEstadisticasDePartido(1);

        assertEquals(ResultadoIngestaPartido.OK, resultado);

        ArgumentCaptor<List<EstadisticaJugadorPartido>> captor = ArgumentCaptor.forClass(List.class);
        verify(estJugadorRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size(),
                "Un jugador repetido en la respuesta debe guardarse una sola vez");
    }

    // =================================================================================
    // Partidos sin estadísticas: quedarse con lo que la respuesta SÍ trae
    // =================================================================================

    @Test
    void partidoAntiguoSinStats_GuardaElOnceAunqueNoHayaEstadisticas() {
        // Comprobado contra la API con partidos de 2015, 2018 y 2021: statistics y players
        // vienen vacíos, pero las dos alineaciones (11 titulares + banquillo) sí llegan.
        // Antes se salía antes de tiempo y se tiraban.
        partido.setLiga(new Liga());
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).minusYears(10));

        FixtureItemDTO item = fixtureConAlineacion(null, null, null, null);
        responderConItem(item);
        when(jugadorRepository.findAllById(any())).thenReturn(List.of());
        when(estJugadorRepository.findByPartidoIdPartido(any())).thenReturn(List.of());

        ResultadoIngestaPartido resultado = estadisticasIngestService.ingestarEstadisticasDePartido(1);

        assertEquals(ResultadoIngestaPartido.SIN_DATOS, resultado, "Sigue sin tener estadísticas");

        ArgumentCaptor<List<EstadisticaJugadorPartido>> captor = ArgumentCaptor.forClass(List.class);
        verify(estJugadorRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size(), "El titular y el suplente deben guardarse");
        assertTrue(captor.getValue().get(0).getTitular());
        assertFalse(captor.getValue().get(1).getTitular());
        // Un partido de 2015 no trae ni posición ni sitio en el campo: se quedan nulos y la
        // pantalla ya sabe caer a una lista en vez de dibujar un campo vacío.
        assertNull(captor.getValue().get(0).getPosicionCampo());
        assertNull(captor.getValue().get(0).getPosicionGrid());
        // Y sin formación ni entrenador no se crea fila de equipo: sería toda nulos y encima
        // haría parecer completo al partido en la consulta de pendientes.
        verify(estEquipoRepository, never()).save(any());
    }

    @Test
    void partidoDe2021SinStats_GuardaTambienDibujoEntrenadorYSitioEnElCampo() {
        partido.setLiga(new Liga());
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).minusYears(5));

        responderConItem(fixtureConAlineacion("4-2-3-1", 8188, "G", "1:1"));
        when(jugadorRepository.findAllById(any())).thenReturn(List.of());
        when(estJugadorRepository.findByPartidoIdPartido(any())).thenReturn(List.of());
        when(estEquipoRepository.findByPartidoAndEquipo(any(), any())).thenReturn(Optional.empty());

        estadisticasIngestService.ingestarEstadisticasDePartido(1);

        ArgumentCaptor<EstadisticaEquipoPartido> equipoCaptor = ArgumentCaptor.forClass(EstadisticaEquipoPartido.class);
        verify(estEquipoRepository).save(equipoCaptor.capture());
        assertEquals("4-2-3-1", equipoCaptor.getValue().getSistemaTactico());
        assertEquals(8188, equipoCaptor.getValue().getIdEntrenador());

        ArgumentCaptor<List<EstadisticaJugadorPartido>> captor = ArgumentCaptor.forClass(List.class);
        verify(estJugadorRepository).saveAll(captor.capture());
        assertEquals("G", captor.getValue().get(0).getPosicionCampo());
        assertEquals("1:1", captor.getValue().get(0).getPosicionGrid());
    }

    @Test
    void partidoRecienteSinStats_NoSeMontaElOnceTodavia() {
        // A un partido de hace dos horas la API todavía le va a publicar las estadísticas.
        // Crear ahora sus filas desde la alineación lo dejaría pareciendo completo en la
        // consulta de pendientes, y esas estadísticas no se pedirían nunca.
        partido.setLiga(new Liga());
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).minusHours(2));

        responderConItem(fixtureConAlineacion("4-3-3", 99, "G", "1:1"));

        estadisticasIngestService.ingestarEstadisticasDePartido(1);

        verify(estJugadorRepository, never()).saveAll(any());
        verify(estEquipoRepository, never()).save(any());
    }

    @Test
    void partidoSinStatsNiAlineacion_NoGuardaNada() {
        partido.setLiga(new Liga());
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).minusYears(10));
        responderConFixture(List.of(), List.of());

        assertEquals(ResultadoIngestaPartido.SIN_DATOS,
                estadisticasIngestService.ingestarEstadisticasDePartido(1));
        verify(estJugadorRepository, never()).saveAll(any());
    }

    /** Un fixture sin estadísticas pero con una alineación por equipo (1 titular + 1 suplente). */
    private FixtureItemDTO fixtureConAlineacion(String formacion, Integer idEntrenador, String pos, String grid) {
        LineupDTO alineacion = new LineupDTO();
        TeamInfoDTO equipo = new TeamInfoDTO();
        equipo.setId(10);
        alineacion.setTeam(equipo);
        alineacion.setFormation(formacion);
        if (idEntrenador != null) {
            LineupDTO.CoachDTO entrenador = new LineupDTO.CoachDTO();
            entrenador.setId(idEntrenador);
            entrenador.setName("Míster");
            alineacion.setCoach(entrenador);
        }
        alineacion.setStartXI(List.of(alineado(1, "Titular", 1, pos, grid)));
        alineacion.setSubstitutes(List.of(alineado(2, "Suplente", 12, null, null)));

        FixtureItemDTO item = new FixtureItemDTO();
        FixtureDetailDTO detalle = new FixtureDetailDTO();
        detalle.setId(partido.getIdPartido());
        item.setFixture(detalle);
        item.setLineups(List.of(alineacion));
        return item;
    }

    private LineupDTO.JugadorAlineadoDTO alineado(int id, String nombre, int dorsal, String pos, String grid) {
        LineupDTO.JugadorEnCampoDTO jugador = new LineupDTO.JugadorEnCampoDTO();
        jugador.setId(id);
        jugador.setName(nombre);
        jugador.setNumber(dorsal);
        jugador.setPos(pos);
        jugador.setGrid(grid);
        LineupDTO.JugadorAlineadoDTO alineado = new LineupDTO.JugadorAlineadoDTO();
        alineado.setPlayer(jugador);
        return alineado;
    }

    private void responderConItem(FixtureItemDTO item) {
        when(partidoRepository.findByIdsConLiga(any())).thenReturn(List.of(partido));
        ApiFootballFixturesResponseDTO respuesta = new ApiFootballFixturesResponseDTO();
        respuesta.setResponse(List.of(item));
        when(apiClient.getFixturesByIds(any())).thenReturn(respuesta);
    }

    private Partido crearPartidoConId(int id) {
        Partido p = new Partido();
        p.setIdPartido(id);
        Equipo local = new Equipo();
        local.setIdEquipo(10);
        Equipo visitante = new Equipo();
        visitante.setIdEquipo(20);
        p.setEquipoLocal(local);
        p.setEquipoVisitante(visitante);
        return p;
    }

    private static ApiPlayerStatsResponseDTO.@NonNull PlayerStatsWrapper getStatsWrapper() {
        ApiPlayerStatsResponseDTO.PlayerStatsWrapper p2 = new ApiPlayerStatsResponseDTO.PlayerStatsWrapper();
        ApiPlayerStatsResponseDTO.PlayerInfoDTO i2 = new ApiPlayerStatsResponseDTO.PlayerInfoDTO();
        i2.setId(2);
        p2.setPlayer(i2);
        ApiPlayerStatsResponseDTO.PlayerDetailStats s2 = new ApiPlayerStatsResponseDTO.PlayerDetailStats();
        ApiPlayerStatsResponseDTO.PlayerDetailStats.Games g2 = new ApiPlayerStatsResponseDTO.PlayerDetailStats.Games();
        g2.setRating("BadRating");
        s2.setGames(g2);
        p2.setStatistics(List.of(s2));
        return p2;
    }

    private static ApiPlayerStatsResponseDTO.@NonNull PlayerStatsWrapper getPlayerStatsWrapper() {
        ApiPlayerStatsResponseDTO.PlayerStatsWrapper p1 = new ApiPlayerStatsResponseDTO.PlayerStatsWrapper();
        ApiPlayerStatsResponseDTO.PlayerInfoDTO i1 = new ApiPlayerStatsResponseDTO.PlayerInfoDTO();
        i1.setId(1);
        i1.setName("P1");
        p1.setPlayer(i1);
        ApiPlayerStatsResponseDTO.PlayerDetailStats s1 = new ApiPlayerStatsResponseDTO.PlayerDetailStats();
        ApiPlayerStatsResponseDTO.PlayerDetailStats.Games g1 = new ApiPlayerStatsResponseDTO.PlayerDetailStats.Games();
        g1.setMinutes(90);
        g1.setRating("8.0");
        s1.setGames(g1);
        ApiPlayerStatsResponseDTO.PlayerDetailStats.Goals go1 = new ApiPlayerStatsResponseDTO.PlayerDetailStats.Goals();
        go1.setTotal(1);
        go1.setAssists(1);
        s1.setGoals(go1);
        ApiPlayerStatsResponseDTO.PlayerDetailStats.Cards c1 = new ApiPlayerStatsResponseDTO.PlayerDetailStats.Cards();
        c1.setYellow(1);
        c1.setRed(0);
        s1.setCards(c1);
        p1.setStatistics(List.of(s1));
        return p1;
    }

    private ApiTeamStatsResponseDTO.StatisticItem crearStat(String type, String value) {
        ApiTeamStatsResponseDTO.StatisticItem item = new ApiTeamStatsResponseDTO.StatisticItem();
        item.setType(type);
        item.setValue(value);
        return item;
    }
}