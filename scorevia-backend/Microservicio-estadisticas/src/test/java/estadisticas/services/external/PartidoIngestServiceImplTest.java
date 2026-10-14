package estadisticas.services.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.dto.external.*;
import estadisticas.entities.arbitros.Arbitro;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.ligas.Liga;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.arbitros.ArbitroRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.ligas.LigaRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartidoIngestServiceImplTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private LigaRepository ligaRepository;
    @Mock private EquipoRepository equipoRepository;
    @Mock private ArbitroRepository arbitroRepository;
    @Mock private PartidoRepository partidoRepository;
    @Mock
    private EventoPartidoIngestService eventoPartidoIngestService;

    @Mock private ColaPostPartido colaPostPartido;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private PartidoIngestServiceImpl partidoIngestService;

    private ApiFootballFixturesResponseDTO responseDTO;

    @BeforeEach
    void setUp() {
        responseDTO = new ApiFootballFixturesResponseDTO();
        responseDTO.setResults(1);
    }

    @Test
    void ingestarPorFecha_Exito_CubreRamasPrincipales() {
        FixtureItemDTO fixtureItem = crearFixtureCompleto("FT", "Regular Season - 12", "Mateu Lahoz, Spain");
        responseDTO.setResponse(List.of(fixtureItem));

        when(apiFootballClient.getFixturesByDate("2026-01-01")).thenReturn(responseDTO);

        // AQUI ESTÁ EL FIX: Le decimos a Mockito que al hacer un save, devuelva lo que le hemos pasado
        when(ligaRepository.findById(5)).thenReturn(Optional.empty());
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(equipoRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(arbitroRepository.findByNombreAndPais("Mateu Lahoz", "Spain"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Arbitro.builder().nombre("Mateu Lahoz").pais("Spain").build()));

        when(partidoRepository.findById(100)).thenReturn(Optional.empty());

        int result = partidoIngestService.ingestarPorFecha("2026-01-01");

        assertEquals(1, result);
        verify(partidoRepository).save(any(Partido.class));
    }

    @Test
    void ingestarEnVivo_VariantesDeDatos_CubreOtrasRamas() {
        // Sin ronda numerica, sin pais en arbitro, estado en vivo
        FixtureItemDTO fixtureItem = crearFixtureCompleto("LIVE", "Quarter-finals", "Mateu Lahoz");
        fixtureItem.getFixture().setDate(null); // Forzamos nulos
        fixtureItem.getFixture().setStatus(new FixtureStatusDTO());

        responseDTO.setResponse(List.of(fixtureItem));
        when(apiFootballClient.getLiveFixtures()).thenReturn(responseDTO);

        // Liga existente con temporada menor (debe actualizarla al valor nuevo)
        Liga ligaExistente = new Liga(); ligaExistente.setTemporadaActual(2025);
        when(ligaRepository.findById(5)).thenReturn(Optional.of(ligaExistente));
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Equipo eqLocal = new Equipo(); eqLocal.setPais("Spain");
        when(equipoRepository.findById(10)).thenReturn(Optional.of(eqLocal));
        when(equipoRepository.findById(20)).thenReturn(Optional.of(new Equipo()));
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(arbitroRepository.findByNombreAndPais("Mateu Lahoz", "")).thenReturn(Optional.of(new Arbitro()));
        when(partidoRepository.findById(100)).thenReturn(Optional.of(new Partido()));
        when(partidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int result = partidoIngestService.ingestarEnVivo();

        assertEquals(1, result);
        // Lo que distingue a ingestarEnVivo de ingestarPorFecha: el post-procesado extra
        // que descarga la timeline de eventos de cada fixture en directo.
        verify(eventoPartidoIngestService).guardarEventos(any(Partido.class), any());
    }

    @Test
    void ingestarEnVivo_FallaPostProcesadoDeEventos_NoRompeElConteoDeFixturesProcesados() {
        FixtureItemDTO fixtureItem = crearFixtureCompleto("LIVE", "12", "Mateu Lahoz, Spain");
        responseDTO.setResponse(List.of(fixtureItem));
        when(apiFootballClient.getLiveFixtures()).thenReturn(responseDTO);

        when(ligaRepository.findById(5)).thenReturn(Optional.empty());
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(equipoRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(arbitroRepository.findByNombreAndPais("Mateu Lahoz", "Spain"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Arbitro.builder().nombre("Mateu Lahoz").pais("Spain").build()));
        when(partidoRepository.findById(100)).thenReturn(Optional.empty());
        when(partidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // El fixture se guarda correctamente, pero descargar sus eventos en vivo falla.
        doThrow(new RuntimeException("API-Football caída")).when(eventoPartidoIngestService)
                .guardarEventos(any(Partido.class), any());

        int result = partidoIngestService.ingestarEnVivo();

        // El fallo del post-procesado está dentro del mismo try/catch que el resto del
        // procesamiento del fixture: no debe tirar el conteo a 0 ni propagar la excepción.
        assertEquals(0, result);
    }

    @Test
    void ingestarEnVivo_PartidoEnDescanso_NoRepitePeticionDeEventosHastaQueExpiraElFreno() {
        FixtureItemDTO fixtureItem = crearFixtureCompleto("HT", "12", "Mateu Lahoz, Spain");
        responseDTO.setResponse(List.of(fixtureItem));
        when(apiFootballClient.getLiveFixtures()).thenReturn(responseDTO);

        when(ligaRepository.findById(5)).thenReturn(Optional.empty());
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(equipoRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(arbitroRepository.findByNombreAndPais("Mateu Lahoz", "Spain"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Arbitro.builder().nombre("Mateu Lahoz").pais("Spain").build()));
        when(partidoRepository.findById(100)).thenReturn(Optional.empty());
        when(partidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Primer tick en descanso: pide eventos y arranca el freno de 16 min.
        partidoIngestService.ingestarEnVivo();
        // Segundo tick, todavía en descanso y dentro de la ventana: no repite la petición.
        partidoIngestService.ingestarEnVivo();

        verify(eventoPartidoIngestService, times(1)).guardarEventos(any(Partido.class), any());
    }

    @Test
    void ingestarEnVivo_PartidoSaleDeDescanso_VuelveAPedirEventosDeInmediato() {
        FixtureItemDTO fixtureItem = crearFixtureCompleto("HT", "12", "Mateu Lahoz, Spain");
        responseDTO.setResponse(List.of(fixtureItem));
        when(apiFootballClient.getLiveFixtures()).thenReturn(responseDTO);

        when(ligaRepository.findById(5)).thenReturn(Optional.empty());
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(equipoRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(arbitroRepository.findByNombreAndPais("Mateu Lahoz", "Spain"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Arbitro.builder().nombre("Mateu Lahoz").pais("Spain").build()));
        when(partidoRepository.findById(100)).thenReturn(Optional.empty());
        when(partidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        partidoIngestService.ingestarEnVivo(); // en descanso: pide eventos y arranca el freno

        fixtureItem.getFixture().getStatus().setShortStatus("2H"); // empieza la 2ª parte
        partidoIngestService.ingestarEnVivo(); // ya no está en descanso: vuelve a pedir de inmediato

        verify(eventoPartidoIngestService, times(2)).guardarEventos(any(Partido.class), any());
    }

    @Test
    void ingestarPorFecha_FallaSerializacion_Continua() throws JsonProcessingException {
        FixtureItemDTO fixtureItem = crearFixtureCompleto("FT", "12", null); // Sin arbitro
        responseDTO.setResponse(List.of(fixtureItem));

        when(apiFootballClient.getFixturesByDate("2026-01-01")).thenReturn(responseDTO);

        when(ligaRepository.findById(anyInt())).thenReturn(Optional.of(new Liga()));
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0)); // Fix añadido
        when(equipoRepository.findById(anyInt())).thenReturn(Optional.of(new Equipo()));
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0)); // Fix añadido
        when(partidoRepository.findById(100)).thenReturn(Optional.of(new Partido()));

        // Forzamos el fallo del ObjectMapper para cubrir el catch
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON Error"));

        int result = partidoIngestService.ingestarPorFecha("2026-01-01");

        assertEquals(1, result);
    }

    @Test
    void ingestarEnVivo_ConflictoDeVersionAlGuardar_SeDescartaSinRomperElResto() {
        FixtureItemDTO fixtureItem = crearFixtureCompleto("LIVE", "12", "Mateu Lahoz, Spain");
        responseDTO.setResponse(List.of(fixtureItem));
        when(apiFootballClient.getLiveFixtures()).thenReturn(responseDTO);

        when(ligaRepository.findById(5)).thenReturn(Optional.empty());
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(equipoRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(arbitroRepository.findByNombreAndPais("Mateu Lahoz", "Spain"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Arbitro.builder().nombre("Mateu Lahoz").pais("Spain").build()));
        when(partidoRepository.findById(100)).thenReturn(Optional.empty());

        // Otra sincronización solapada (scheduler + manual) escribió el mismo partido
        // primero: la versión ya no coincide.
        when(partidoRepository.save(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Partido.class, 100));

        int result = partidoIngestService.ingestarEnVivo();

        // Se descarta esta ronda para ese fixture, no rompe el conteo ni propaga la excepción.
        assertEquals(0, result);
        verifyNoInteractions(eventoPartidoIngestService);
    }

    @Test
    void ingestarPorFecha_ExcepcionTotal_AtrapaYContinua() {
        FixtureItemDTO fixtureItem = crearFixtureCompleto("FT", "12", null);
        responseDTO.setResponse(List.of(fixtureItem));

        when(apiFootballClient.getFixturesByDate("2026-01-01")).thenReturn(responseDTO);
        // Simulamos un error grave de la base de datos
        when(ligaRepository.findById(anyInt())).thenThrow(new RuntimeException("DB Caida"));

        int result = partidoIngestService.ingestarPorFecha("2026-01-01");

        // 0 procesados, pero no peta la aplicación entera
        assertEquals(0, result);
    }

    @Test
    void ingestarPorFecha_PartidoDeEliminatoria_GuardaLaFaseAunqueNoHayaJornada() {
        // "Quarter-finals" no tiene número que sacar, así que jornada se queda a null. Antes
        // el texto se perdía ahí mismo (solo quedaba dentro del JSONB de datos_api) y la
        // ficha del partido acababa pintando "Mundial · Jornada " con el hueco vacío.
        responseDTO.setResponse(List.of(crearFixtureCompleto("NS", "Quarter-finals", null)));
        when(apiFootballClient.getFixturesByDate("2026-06-01")).thenReturn(responseDTO);

        when(ligaRepository.findById(5)).thenReturn(Optional.empty());
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(equipoRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(partidoRepository.findById(100)).thenReturn(Optional.empty());
        when(partidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        partidoIngestService.ingestarPorFecha("2026-06-01");

        ArgumentCaptor<Partido> guardado = ArgumentCaptor.forClass(Partido.class);
        verify(partidoRepository).save(guardado.capture());
        assertNull(guardado.getValue().getJornada(), "Una eliminatoria no tiene jornada");
        assertEquals("Quarter-finals", guardado.getValue().getRonda());
    }

    @Test
    void ingestarPorFecha_PartidoDeLiga_GuardaJornadaYTambienElTextoDeLaRonda() {
        responseDTO.setResponse(List.of(crearFixtureCompleto("NS", "Regular Season - 12", null)));
        when(apiFootballClient.getFixturesByDate("2026-06-01")).thenReturn(responseDTO);

        when(ligaRepository.findById(5)).thenReturn(Optional.empty());
        when(ligaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(equipoRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(equipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(partidoRepository.findById(100)).thenReturn(Optional.empty());
        when(partidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        partidoIngestService.ingestarPorFecha("2026-06-01");

        ArgumentCaptor<Partido> guardado = ArgumentCaptor.forClass(Partido.class);
        verify(partidoRepository).save(guardado.capture());
        assertEquals(12, guardado.getValue().getJornada());
        assertEquals("Regular Season - 12", guardado.getValue().getRonda());
    }

    private FixtureItemDTO crearFixtureCompleto(String statusStr, String round, String ref) {
        FixtureItemDTO fixtureItem = new FixtureItemDTO();
        FixtureDetailDTO detail = new FixtureDetailDTO();
        detail.setId(100);
        detail.setDate("2026-01-01T15:00:00+00:00");
        detail.setReferee(ref);
        FixtureStatusDTO status = new FixtureStatusDTO();
        status.setShortStatus(statusStr);
        detail.setStatus(status);
        fixtureItem.setFixture(detail);

        LeagueInfoDTO league = new LeagueInfoDTO();
        league.setId(5);
        league.setSeason(2026);
        league.setRound(round);
        fixtureItem.setLeague(league);

        TeamsInfoDTO teams = new TeamsInfoDTO();
        TeamInfoDTO home = new TeamInfoDTO(); home.setId(10);
        TeamInfoDTO away = new TeamInfoDTO(); away.setId(20);
        teams.setHome(home);
        teams.setAway(away);
        fixtureItem.setTeams(teams);

        GoalsInfoDTO goals = new GoalsInfoDTO();
        goals.setHome(2); goals.setAway(1);
        fixtureItem.setGoals(goals);

        return fixtureItem;
    }
}