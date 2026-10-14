package estadisticas.services.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.equipos.Estadio;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.entities.ligas.Liga;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.equipos.EstadioRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.ligas.LigaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaestrosIngestServiceImplTest {

    @Mock private ApiFootballClient apiClient;
    @Mock private EquipoRepository equipoRepository;
    @Mock private EstadioRepository estadioRepository;
    @Mock private EntrenadorRepository entrenadorRepository;
    @Mock private estadisticas.repositories.entrenadores.HistorialEntrenadorRepository historialEntrenadorRepository;
    @Mock private ClasificacionRepository clasificacionRepository;
    @Mock private LigaRepository ligaRepository;
    @Mock private JugadorRepository jugadorRepository;

    @InjectMocks
    private MaestrosIngestServiceImpl service;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Inyectamos el @Value para que las validaciones de temporadaActual funcionen
        ReflectionTestUtils.setField(service, "temporadaActual", 2026);
    }

    // ==========================================
    // TEST LIGAS
    // ==========================================
    @Test
    void ingestarLigasMasivas_GuardaLigasCorrectamente() throws Exception {
        String jsonStr = "{ \"response\": [ { \"league\": {\"id\": 1, \"name\": \"La Liga\", \"logo\": \"url\"}, \"country\": {\"name\": \"Spain\", \"flag\": \"url\"} } ] }";
        JsonNode node = mapper.readTree(jsonStr);

        when(apiClient.getLeagues(2026)).thenReturn(node);
        when(ligaRepository.findById(1)).thenReturn(Optional.empty());

        service.ingestarLigasMasivas(2026);

        verify(ligaRepository).saveAll(anyList());
    }

    @Test
    void ingestarLigasMasivas_RespuestaInvalida_NoHaceNada() throws Exception {
        String jsonStr = "{ \"response\": [] }"; // Array vacío
        JsonNode node = mapper.readTree(jsonStr);

        when(apiClient.getLeagues(2026)).thenReturn(node);

        service.ingestarLigasMasivas(2026);

        verify(ligaRepository, never()).saveAll(anyList());
    }

    // ==========================================
    // TEST EQUIPOS Y ESTADIOS
    // ==========================================
    @Test
    void ingestarEquiposYEstadiosDeLiga_GuardaCorrectamente() throws Exception {
        String jsonStr = "{ \"response\": [ { \"team\": {\"id\": 1, \"name\": \"Madrid\", \"country\": \"Spain\", \"founded\": 1902}, \"venue\": {\"id\": 10, \"name\": \"Bernabeu\", \"capacity\": 80000} } ] }";
        JsonNode node = mapper.readTree(jsonStr);

        when(apiClient.getTeamsByLeagueAndSeason(140, 2026)).thenReturn(node);
        when(ligaRepository.findById(140)).thenReturn(Optional.of(new Liga()));
        when(estadioRepository.findById(10)).thenReturn(Optional.empty());
        when(equipoRepository.findById(1)).thenReturn(Optional.empty());
        when(estadioRepository.save(any(Estadio.class))).thenAnswer(i -> i.getArgument(0));

        service.ingestarEquiposYEstadiosDeLiga(140, 2026);

        verify(estadioRepository).save(any(Estadio.class));
        verify(equipoRepository).save(any(Equipo.class));
    }

    // ==========================================
    // TEST ENTRENADORES
    // ==========================================
    @Test
    void ingestarEntrenadoresPorEquipo_IteraYGuarda() throws Exception {
        Equipo eq = new Equipo();
        eq.setIdEquipo(1);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(eq)))
                .thenReturn(new PageImpl<>(List.of())); // Cierra el bucle

        // FIX: /coachs?team=X devuelve el historial completo de técnicos de ese equipo, no
        // solo el actual -- el nodo trae su "team" actual, y solo se guarda/enlaza si coincide
        // con el equipo que se está ingiriendo ahora mismo.
        String jsonStr = "{ \"response\": [ {\"id\": 1, \"name\": \"Ancelotti\", \"age\": 64, \"nationality\": \"Italy\", \"team\": {\"id\": 1}} ] }";
        JsonNode node = mapper.readTree(jsonStr);

        when(apiClient.getCoachsByTeam(1)).thenReturn(node);
        when(entrenadorRepository.findById(1)).thenReturn(Optional.empty());

        service.ingestarEntrenadoresPorEquipo();

        verify(entrenadorRepository).save(any(Entrenador.class));
    }

    @Test
    void ingestarEntrenadoresPorEquipo_EntrenadorYaNoEstaEnElEquipo_NoLoGuardaYLimpiaElEnlaceObsoleto() throws Exception {
        Equipo eq = new Equipo();
        eq.setIdEquipo(1);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(eq)))
                .thenReturn(new PageImpl<>(List.of()));

        // Zidane aparece en el historial de este equipo, pero su "team" actual es OTRO (99) --
        // ya no está aquí.
        String jsonStr = "{ \"response\": [ {\"id\": 2, \"name\": \"Zidane\", \"team\": {\"id\": 99}} ] }";
        JsonNode node = mapper.readTree(jsonStr);
        when(apiClient.getCoachsByTeam(1)).thenReturn(node);

        Entrenador zidaneObsoleto = new Entrenador();
        zidaneObsoleto.setIdEntrenador(2);
        zidaneObsoleto.setEquipo(eq); // enlace obsoleto: seguía apuntando a este equipo
        when(entrenadorRepository.findById(2)).thenReturn(Optional.of(zidaneObsoleto));

        service.ingestarEntrenadoresPorEquipo();

        assertNull(zidaneObsoleto.getEquipo());
        verify(entrenadorRepository).save(zidaneObsoleto);
    }

    @Test
    void ingestarEntrenadoresPorEquipo_CuotaAgotada_RompeBucle() {
        Equipo eq = new Equipo();
        eq.setIdEquipo(1);

        when(equipoRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(eq)));
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(false); // Simula cuota agotada

        service.ingestarEntrenadoresPorEquipo();

        verify(apiClient, never()).getCoachsByTeam(anyInt());
    }

    @Test
    void ingestarEntrenadoresPorEquipo_CapturaExcepcion() {
        Equipo eq = new Equipo();
        eq.setIdEquipo(1);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(equipoRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(eq)))
                .thenReturn(new PageImpl<>(List.of()));

        when(apiClient.getCoachsByTeam(1)).thenThrow(new RuntimeException("Error API"));

        assertDoesNotThrow(() -> service.ingestarEntrenadoresPorEquipo()); // El try-catch debe absorberlo
    }

    // ==========================================
    // TEST CLASIFICACIONES
    // ==========================================
    @Test
    void ingestarClasificaciones_GuardaCorrectamente() throws Exception {
        String jsonStr = "{ \"response\": [ { \"league\": { \"standings\": [ [ { \"rank\": 1, \"points\": 90, \"team\": {\"id\": 541}, \"all\": {\"played\": 38, \"win\": 30, \"draw\": 5, \"lose\": 3, \"goals\": {\"for\": 80, \"against\": 20}} } ] ] } } ] }";
        JsonNode node = mapper.readTree(jsonStr);

        when(apiClient.getStandings(140, 2026)).thenReturn(node);
        when(ligaRepository.findById(140)).thenReturn(Optional.of(new Liga()));
        when(equipoRepository.findById(541)).thenReturn(Optional.of(new Equipo()));
        when(clasificacionRepository.findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(140, 541, 2026))
                .thenReturn(Optional.empty());

        service.ingestarClasificaciones(140, 2026);

        ArgumentCaptor<Clasificacion> captor = ArgumentCaptor.forClass(Clasificacion.class);
        verify(clasificacionRepository).save(captor.capture());
        // Regresión: la API-Football no manda la diferencia de goles ya calculada (solo
        // "for"/"against" por separado) -- si no se deriva aquí, la columna se queda null.
        assertEquals(60, captor.getValue().getDiferenciaGoles());
    }

    @Test
    void ingestarClasificaciones_VariosGrupos_ProcesaTodosNoSoloElPrimero() throws Exception {
        // Regresión: competiciones con fase de grupos/conferencias devuelven varios arrays
        // dentro de "standings" (uno por grupo), no solo uno.
        String jsonStr = "{ \"response\": [ { \"league\": { \"standings\": ["
                + "[ { \"rank\": 1, \"points\": 90, \"team\": {\"id\": 541}, \"all\": {\"played\": 38, \"win\": 30, \"draw\": 5, \"lose\": 3, \"goals\": {\"for\": 80, \"against\": 20}} } ],"
                + "[ { \"rank\": 1, \"points\": 88, \"team\": {\"id\": 542}, \"all\": {\"played\": 38, \"win\": 29, \"draw\": 4, \"lose\": 5, \"goals\": {\"for\": 75, \"against\": 25}} } ]"
                + "] } } ] }";
        JsonNode node = mapper.readTree(jsonStr);

        when(apiClient.getStandings(140, 2026)).thenReturn(node);
        when(ligaRepository.findById(140)).thenReturn(Optional.of(new Liga()));
        when(equipoRepository.findById(541)).thenReturn(Optional.of(new Equipo()));
        when(equipoRepository.findById(542)).thenReturn(Optional.of(new Equipo()));
        when(clasificacionRepository.findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        service.ingestarClasificaciones(140, 2026);

        verify(clasificacionRepository, times(2)).save(any(Clasificacion.class));
    }

    // ==========================================
    // TEST JUGADORES (PLANTILLAS)
    // ==========================================
    @Test
    void ingestarJugadoresCompletosPorEquipo_IteraPaginacionYGuarda() throws Exception {
        Equipo eq = new Equipo();
        eq.setIdEquipo(541);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        // La sincronización pide lotes de pendientes (y ya no pagina): al marcar cada equipo
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), anyInt(), anyInt()))
                .thenReturn(List.of(eq))
                .thenReturn(List.of());

        // JSON con 2 páginas simuladas y una fecha malformada para probar el catch de DateTimeParseException
        String jsonStrPage1 = "{ \"paging\": {\"total\": 2}, \"response\": [ { \"player\": {\"id\": 1, \"birth\": {\"date\": \"fecha-invalida\"}}, \"statistics\": [ {\"games\": {\"position\": \"Attacker\", \"number\": 7}} ] } ] }";
        String jsonStrPage2 = "{ \"paging\": {\"total\": 2}, \"response\": [ { \"player\": {\"id\": 2, \"name\": \"Courtois\"} } ] }";

        JsonNode nodePage1 = mapper.readTree(jsonStrPage1);
        JsonNode nodePage2 = mapper.readTree(jsonStrPage2);

        when(apiClient.getPlayersByTeamAndSeason(541, 2026, 1)).thenReturn(nodePage1);
        when(apiClient.getPlayersByTeamAndSeason(541, 2026, 2)).thenReturn(nodePage2);

        when(jugadorRepository.findById(1)).thenReturn(Optional.empty());
        when(jugadorRepository.findById(2)).thenReturn(Optional.empty());

        service.ingestarJugadoresCompletosPorEquipo(2026);

        // Se guarda la lista 2 veces (una por cada página)
        verify(jugadorRepository, times(2)).saveAll(anyList());
    }

    // ==========================================
    // TEST PERFILES PENDIENTES
    // ==========================================
    @Test
    void ingestarPerfilesPendientes_VerificaYActualiza() throws Exception {
        Jugador jug = new Jugador();
        jug.setIdJugador(100);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(jugadorRepository.findJugadoresPendientesDePerfil(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(jug)))
                .thenReturn(new PageImpl<>(List.of()));

        String jsonStr = "{ \"response\": [ {\"player\": {\"birth\": {\"place\": \"Madrid\"}}} ] }";
        JsonNode node = mapper.readTree(jsonStr);

        when(apiClient.getPlayerProfile(100)).thenReturn(node);

        service.ingestarPerfilesPendientes();

        assertTrue(jug.getPerfilVerificado());
        assertEquals("Madrid", jug.getLugarNacimiento());
        verify(jugadorRepository, times(1)).saveAll(anyList());
    }

    @Test
    void ingestarPerfilesPendientes_MultiplesRondas_SiempreConsultaPagina0YProcesaTodos() throws Exception {
        // Regresión: el bucle solía avanzar el número de página (PageRequest.of(pagina++, 50))
        // sobre un filtro (perfilVerificado=false) que el propio bucle iba reduciendo en cada
        // vuelta, saltándose jugadores pendientes reales en páginas posteriores. El fix consulta
        // siempre la página 0, ya que los jugadores procesados caen solos del filtro. Aquí lo
        // probamos con 2 "rondas" de jugadores realmente distintos (no la misma página repetida)
        // y comprobamos que el repositorio se consulta con PageRequest.of(0, 50) en las 3 llamadas.
        Jugador jugadorRonda1 = new Jugador();
        jugadorRonda1.setIdJugador(100);
        Jugador jugadorRonda2 = new Jugador();
        jugadorRonda2.setIdJugador(200);

        PageRequest pagina0 = PageRequest.of(0, 50);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(jugadorRepository.findJugadoresPendientesDePerfil(pagina0))
                // Page.hasNext() compara totalElements contra el tamaño de página (50), no
                // contra el tamaño real del contenido devuelto: para forzar hasNext()=true
                // en la página 0 hace falta un total mayor que el tamaño de página.
                .thenReturn(new PageImpl<>(List.of(jugadorRonda1), pagina0, 51))
                // Una vez marcado el primero, "cae" del filtro y aparece el siguiente pendiente
                .thenReturn(new PageImpl<>(List.of(jugadorRonda2), pagina0, 51))
                .thenReturn(new PageImpl<>(List.of()));

        String jsonStr = "{ \"response\": [ {\"player\": {\"birth\": {\"place\": \"Madrid\"}}} ] }";
        JsonNode node = mapper.readTree(jsonStr);
        when(apiClient.getPlayerProfile(anyInt())).thenReturn(node);

        service.ingestarPerfilesPendientes();

        assertTrue(jugadorRonda1.getPerfilVerificado());
        assertTrue(jugadorRonda2.getPerfilVerificado());
        // Las 3 llamadas (2 rondas con datos + la final vacía) deben pedir SIEMPRE la página 0,
        // nunca 1 ni 2: eso es justo lo que el bug rompía.
        verify(jugadorRepository, times(3)).findJugadoresPendientesDePerfil(pagina0);
        verify(jugadorRepository, never()).findJugadoresPendientesDePerfil(PageRequest.of(1, 50));
    }

    @Test
    void ingestarPerfilesPendientes_CapturaExcepcionEnMetodoPrivado() {
        Jugador jug = new Jugador();
        jug.setIdJugador(100);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(jugadorRepository.findJugadoresPendientesDePerfil(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(jug)))
                .thenReturn(new PageImpl<>(List.of()));

        // Simulamos que la API lanza error al pedir el perfil
        when(apiClient.getPlayerProfile(100)).thenThrow(new RuntimeException("API caida"));

        assertDoesNotThrow(() -> service.ingestarPerfilesPendientes());

        // Verifica que aunque falló, intentó guardar la página (para no dejar en bucle infinito si falla el guardado)
        verify(jugadorRepository).saveAll(anyList());
    }

    // ==========================================================================
    // CATALOGO DE JUGADORES (/players/profiles paginado)
    // ==========================================================================
    // La ficha del catalogo trae el perfil completo. Antes se leian solo firstname y
    // lastname y los otros once campos se tiraban, mientras ingestarPerfilesPendientes()
    // volvia a pedir esos mismos datos a una peticion por jugador. Estos tests fijan que
    // la pagina rellene el perfil entero.

    /** Una pagina del catalogo tal y como la manda la API, con una sola ficha. */
    private JsonNode paginaCatalogo(String fichaPlayer, int totalPaginas) throws Exception {
        return mapper.readTree("{\"response\":[{\"player\":" + fichaPlayer + "}],"
                + "\"paging\":{\"current\":1,\"total\":" + totalPaginas + "}}");
    }

    @Test
    void catalogo_RellenaElPerfilEnteroYNoSoloElNombre() throws Exception {
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayerProfilesPagina(1)).thenReturn(paginaCatalogo(
                "{\"id\":1,\"firstname\":\"Roman\",\"lastname\":\"Burki\","
                + "\"nationality\":\"Switzerland\",\"height\":\"187\",\"weight\":\"85\","
                + "\"number\":1,\"position\":\"Goalkeeper\",\"photo\":\"http://foto\","
                + "\"birth\":{\"date\":\"1990-11-14\",\"place\":\"Munsingen\"}}", 1));
        when(jugadorRepository.findAllById(any())).thenReturn(List.of(jugador));

        service.ingestarNombresCompletos(1, 1);

        assertEquals("Roman Burki", jugador.getNombreCompleto());
        assertEquals("Switzerland", jugador.getNacionalidad());
        assertEquals("Goalkeeper", jugador.getPosicion());
        assertEquals(1, jugador.getDorsal());
        assertEquals("http://foto", jugador.getFotoUrl());
        assertEquals("Munsingen", jugador.getLugarNacimiento());
        assertEquals(java.time.LocalDate.of(1990, 11, 14), jugador.getFechaNacimiento());
        // Es el mismo endpoint que /players/profiles?player=X, asi que la ficha vale como
        // perfil verificado: es lo que ahorra las 200.509 peticiones sueltas.
        assertTrue(jugador.getPerfilVerificado());
    }

    @Test
    void catalogo_PoneLaUnidadAAlturaYPeso() throws Exception {
        // El catalogo manda "187"/"85" pelados, pero el endpoint por jugador manda
        // "187 cm"/"85 kg", que es como estan guardados los 324.852 ya verificados. Sin
        // normalizar, la columna acabaria con las dos formas mezcladas.
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayerProfilesPagina(1)).thenReturn(paginaCatalogo(
                "{\"id\":1,\"height\":\"187\",\"weight\":\"85\"}", 1));
        when(jugadorRepository.findAllById(any())).thenReturn(List.of(jugador));

        service.ingestarNombresCompletos(1, 1);

        assertEquals("187 cm", jugador.getAltura());
        assertEquals("85 kg", jugador.getPeso());
    }

    @Test
    void catalogo_NoPisaElDorsalNiLaPosicionQueYaTeniamos() throws Exception {
        // asignarEstadisticasJugador() saca dorsal y posicion de las estadisticas del equipo
        // en la temporada en curso: son mas frescos que los genericos del catalogo.
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);
        jugador.setDorsal(9);
        jugador.setPosicion("Attacker");
        jugador.setAltura("180 cm");

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayerProfilesPagina(1)).thenReturn(paginaCatalogo(
                "{\"id\":1,\"number\":1,\"position\":\"Goalkeeper\",\"height\":\"187\"}", 1));
        when(jugadorRepository.findAllById(any())).thenReturn(List.of(jugador));

        service.ingestarNombresCompletos(1, 1);

        assertEquals(9, jugador.getDorsal());
        assertEquals("Attacker", jugador.getPosicion());
        assertEquals("180 cm", jugador.getAltura());
        // Aun sin cambiar ningun campo, la ficha lo deja verificado.
        assertTrue(jugador.getPerfilVerificado());
    }

    @Test
    void catalogo_NoGuardaNadaSiLaFichaNoAportaYYaEstabaVerificado() throws Exception {
        Jugador jugador = new Jugador();
        jugador.setIdJugador(1);
        jugador.setNombreCompleto("Roman Burki");
        jugador.setPerfilVerificado(true);

        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayerProfilesPagina(1)).thenReturn(paginaCatalogo(
                "{\"id\":1,\"firstname\":\"Roman\",\"lastname\":\"Burki\"}", 1));
        when(jugadorRepository.findAllById(any())).thenReturn(List.of(jugador));

        service.ingestarNombresCompletos(1, 1);

        // 250 fichas por pagina que no cambian nada no deben generar 250 UPDATE.
        verify(jugadorRepository, never()).saveAll(anyList());
    }

    // ==========================================================================
    // PLANTILLAS: quien se ha ido, se va
    // ==========================================================================
    // `jugador.equipo` se usa como "equipo actual" pero solo se ASIGNABA: nada lo quitaba
    // nunca, asi que la plantilla de un club acababa siendo todo el que hubiera pasado por
    // el. Medido el 2026-09-08: Toni Kroos seguia en el Real Madrid (ultimo partido suyo,
    // 2024-05-08) y el Madrid figuraba con 46 jugadores.

    private JsonNode paginaPlantilla(Integer... idsJugador) throws Exception {
        StringBuilder sb = new StringBuilder("{\"response\":[");
        for (int i = 0; i < idsJugador.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"player\":{\"id\":").append(idsJugador[i])
              .append(",\"name\":\"J").append(idsJugador[i]).append("\"},\"statistics\":[]}");
        }
        sb.append("],\"paging\":{\"current\":1,\"total\":1}}");
        return mapper.readTree(sb.toString());
    }

    @Test
    void plantilla_DesvinculaAQuienYaNoEsta() throws Exception {
        Equipo madrid = new Equipo();
        madrid.setIdEquipo(541);
        madrid.setNombre("Real Madrid");

        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), anyInt(), anyInt()))
                .thenReturn(List.of(madrid)).thenReturn(List.of());
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayersByTeamAndSeason(eq(541), eq(2026), eq(1)))
                .thenReturn(paginaPlantilla(1, 2, 3));
        when(jugadorRepository.findById(any())).thenReturn(Optional.empty());

        service.ingestarJugadoresCompletosPorEquipo(2026);

        // Los tres que vienen en la respuesta son los que siguen; a los demas se les quita
        // el equipo. Sin esta llamada, Kroos no sale nunca del Madrid.
        ArgumentCaptor<List<Integer>> ids = ArgumentCaptor.forClass(List.class);
        verify(jugadorRepository).desvincularLosQueYaNoEstan(eq(541), ids.capture());
        assertEquals(Set.of(1, 2, 3), new java.util.HashSet<>(ids.getValue()));
    }

    @Test
    void plantilla_UnaRespuestaVaciaNoVaciaElClub() throws Exception {
        // "No lo se" no es "no queda nadie". Si la API falla o devuelve vacio y aqui se
        // desvinculara igual, un error suyo borraria la plantilla entera del club.
        Equipo madrid = new Equipo();
        madrid.setIdEquipo(541);
        madrid.setNombre("Real Madrid");

        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), anyInt(), anyInt()))
                .thenReturn(List.of(madrid)).thenReturn(List.of());
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayersByTeamAndSeason(eq(541), eq(2026), eq(1)))
                .thenReturn(mapper.readTree("{\"response\":[]}"));

        service.ingestarJugadoresCompletosPorEquipo(2026);

        verify(jugadorRepository, never()).desvincularLosQueYaNoEstan(any(), any());
    }
}
