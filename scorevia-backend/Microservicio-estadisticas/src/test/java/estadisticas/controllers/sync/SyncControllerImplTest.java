package estadisticas.controllers.sync;

import estadisticas.exception.BadRequestException;
import estadisticas.services.external.ApiFootballClient;
import estadisticas.services.external.ColaPostPartido;
import estadisticas.services.external.EntidadesSecundariasIngestService;
import estadisticas.services.external.EstadisticasIngestService;
import estadisticas.services.external.EventoPartidoIngestService;
import estadisticas.services.external.MaestrosIngestService;
import estadisticas.services.external.PartidoIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SyncControllerImplTest {

    @Mock private PartidoIngestService partidoIngestService;
    @Mock private EstadisticasIngestService estadisticasIngestService;
    @Mock private EntidadesSecundariasIngestService entidadesSecundariasIngestService;
    @Mock private MaestrosIngestService maestrosIngestService;
    @Mock private EventoPartidoIngestService eventoPartidoIngestService;
    @Mock private CacheManager cacheManager;
    @Mock private Cache contextoPrediccionCache;

    @Mock private Executor ingestaExecutor;
    @Mock private ColaPostPartido colaPostPartido;
    @Mock private ApiFootballClient apiFootballClient;

    @InjectMocks
    private SyncControllerImpl syncController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(syncController).build();

        // TRUCO MÁGICO: Hacemos que el Executor ejecute los hilos asíncronos instantáneamente en el mismo hilo del test.
        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(ingestaExecutor).execute(any(Runnable.class));

        lenient().when(cacheManager.getCache("contextoPrediccion")).thenReturn(contextoPrediccionCache);
    }

    @Test
    void sincronizarPorFecha_FechaValida_DebeRetornarOk() throws Exception {
        String fecha = "2026-10-10";
        when(partidoIngestService.ingestarPorFecha(fecha)).thenReturn(5);

        mockMvc.perform(post("/api/v1/partidos/sync")
                        .param("fecha", fecha)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("Partidos procesados: 5"));

        verify(partidoIngestService).ingestarPorFecha(fecha);
    }

    @Test
    void sincronizarPorFecha_FechaInvalida_DebeLanzarBadRequestException() {
        // Al probar la excepción directamente invocando el controlador nos saltamos que MockMvc envuelva el error
        assertThrows(BadRequestException.class, () -> syncController.sincronizarPorFecha("FECHA-MALA"));
    }

    @Test
    void sincronizarEstadisticasPendientes_DebeRetornarAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/stats-sync"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Sincronización iniciada."));

        verify(estadisticasIngestService).ingestarEstadisticasPendientes();
    }

    @Test
    void sincronizarEstadisticasPendientes_DebeEvictarCacheDespuesDeLaIngestaAsincrona() throws Exception {
        // Regresión Fix 2: la evicción de "contextoPrediccion" debe ocurrir DESPUÉS
        // de que termine la ingesta asíncrona, nunca antes de lanzarla.
        InOrder inOrder = inOrder(estadisticasIngestService, contextoPrediccionCache);

        mockMvc.perform(post("/api/v1/partidos/stats-sync"))
                .andExpect(status().isAccepted());

        inOrder.verify(estadisticasIngestService).ingestarEstadisticasPendientes();
        inOrder.verify(contextoPrediccionCache).clear();
    }

    @Test
    void sincronizarTrofeosYBajas_DebeRetornarOk() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/sync-trofeos-bajas"))
                .andExpect(status().isOk());

        verify(entidadesSecundariasIngestService).ingestarTrofeosYBajasMasivo();
        verify(contextoPrediccionCache).clear();
    }

    @Test
    void sincronizarTraspasos_DebeRetornarOk() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/sync-traspasos"))
                .andExpect(status().isOk());

        verify(entidadesSecundariasIngestService).ingestarTraspasosPorEquipo();
        verify(contextoPrediccionCache).clear();
    }

    @Test
    void syncEquiposYEstadios_DebeRetornarOk() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/equipos-estadios")
                        .param("idLiga", "140")
                        .param("temporada", "2026"))
                .andExpect(status().isOk());

        verify(maestrosIngestService).ingestarEquiposYEstadiosDeLiga(140, 2026);
    }

    @Test
    void syncEntrenadores_PorDefectoSoloLasDestacadas() throws Exception {
        // El defecto es el acotado A PROPOSITO: la version completa recorre los 28.025
        // equipos de la base, y con ese coste no se lanza nunca -- que es justo por lo que
        // el dato se pudria (Mourinho en el Benfica cuando ya estaba en el Madrid).
        mockMvc.perform(post("/api/v1/partidos/entrenadores"))
                .andExpect(status().isOk());

        verify(maestrosIngestService).refrescarEntrenadoresDestacados();
        verify(maestrosIngestService, never()).ingestarEntrenadoresPorEquipo();
        verify(contextoPrediccionCache).clear();
    }

    @Test
    void syncEntrenadores_ConSoloDestacadasFalse_RecorreTodo() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/entrenadores").param("soloDestacadas", "false"))
                .andExpect(status().isOk());

        verify(maestrosIngestService).ingestarEntrenadoresPorEquipo();
        verify(maestrosIngestService, never()).refrescarEntrenadoresDestacados();
    }

    @Test
    void syncClasificaciones_DebeRetornarOk() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/clasificaciones")
                        .param("idLiga", "140")
                        .param("temporada", "2026"))
                .andExpect(status().isOk());

        verify(maestrosIngestService).ingestarClasificaciones(140, 2026);
    }

    @Test
    void syncLigasMaestras_DebeRetornarOk() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/ligas/{temporada}", "2026"))
                .andExpect(status().isOk());

        verify(maestrosIngestService).ingestarLigasMasivas(2026);
    }

    @Test
    void syncJugadoresCompletos_DebeRetornarAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/jugadores/completos/{temporada}", "2026"))
                .andExpect(status().isAccepted());

        verify(maestrosIngestService).ingestarJugadoresCompletosPorEquipo(2026, false, 0);
        verify(contextoPrediccionCache).clear();
    }

    @Test
    void sincronizarPerfilesJugadores_DebeRetornarAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/perfiles-jugadores"))
                .andExpect(status().isAccepted());

        verify(maestrosIngestService).ingestarPerfilesPendientes();
        verify(contextoPrediccionCache).clear();
    }

    @Test
    void sincronizarEnVivo_DebeRetornarOk() throws Exception {
        when(partidoIngestService.ingestarEnVivo()).thenReturn(3);

        mockMvc.perform(post("/api/v1/partidos/sync-en-vivo"))
                .andExpect(status().isOk())
                .andExpect(content().string("Partidos en vivo procesados: 3"));

        verify(partidoIngestService).ingestarEnVivo();
    }

    @Test
    void syncEventosHistoricos_DebeRetornarAccepted() throws Exception {
        when(eventoPartidoIngestService.ingestarEventosFaltantesHistoricos()).thenReturn(7);

        mockMvc.perform(post("/api/v1/partidos/eventos-historicos"))
                .andExpect(status().isAccepted());

        verify(eventoPartidoIngestService).ingestarEventosFaltantesHistoricos();
        verify(contextoPrediccionCache).clear();
    }

    @Test
    void estadoIngesta_DevuelveColaYCuota() throws Exception {
        when(colaPostPartido.tamano()).thenReturn(7);
        when(apiFootballClient.getPeticionesRestantes()).thenReturn(142310);
        // /status no cuenta para la cuota diaria, así que aquí se puede preguntar el consumo
        // real en vez de conformarse con la cabecera de la última petición hecha.
        when(apiFootballClient.getStatus()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"response":{"subscription":{"plan":"Mega"},"requests":{"current":7690,"limit_day":150000}}}"""));

        mockMvc.perform(get("/api/v1/partidos/estado-ingesta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosEnCola").value(7))
                .andExpect(jsonPath("$.peticionesRestantes").value(142310))
                .andExpect(jsonPath("$.peticionesGastadasHoy").value(7690))
                .andExpect(jsonPath("$.limiteDiario").value(150000))
                .andExpect(jsonPath("$.plan").value("Mega"));
    }

    @Test
    void estadoIngesta_SiStatusFalla_DevuelveLoQueYaSabe() throws Exception {
        when(colaPostPartido.tamano()).thenReturn(3);
        when(apiFootballClient.getStatus()).thenThrow(new IllegalStateException("API caída"));

        mockMvc.perform(get("/api/v1/partidos/estado-ingesta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosEnCola").value(3));
    }

    @Test
    void recogidaDelDia_LanzaLaRepescaConSusParametros() throws Exception {
        mockMvc.perform(post("/api/v1/partidos/recogida-dia")
                        .param("horas", "12")
                        .param("max", "300"))
                .andExpect(status().isAccepted());

        verify(estadisticasIngestService).ingestarEstadisticasRecientes(12, 300);
    }
}
