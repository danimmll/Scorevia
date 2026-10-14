package estadisticas.config;

import estadisticas.repositories.partidos.PartidoRepository;
import estadisticas.services.external.ApiFootballClient;
import estadisticas.services.external.ColaPostPartido;
import estadisticas.services.external.EstadisticasIngestService;
import estadisticas.services.external.MaestrosIngestService;
import estadisticas.services.external.PartidoIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Los trabajos automáticos, sobre todo por lo que NO deben hacer.
 *
 * <p>Todo lo que hay aquí gasta cuota de API-Football sin que nadie lo esté mirando, así que
 * lo importante es que respeten los interruptores y que paren solos cuando la cuota baja.</p>
 */
@ExtendWith(MockitoExtension.class)
class IngestaSchedulerTest {

    @Mock private PartidoIngestService partidoIngestService;
    @Mock private EstadisticasIngestService estadisticasIngestService;
    @Mock private ColaPostPartido colaPostPartido;
    @Mock private MaestrosIngestService maestrosIngestService;
    @Mock private PartidoRepository partidoRepository;
    @Mock private ApiFootballClient apiFootballClient;

    @InjectMocks private IngestaScheduler scheduler;

    @BeforeEach
    void setUp() {
        // @Value no se inyecta en un test de Mockito puro: sin esto los boolean caen a false
        // y todos los trabajos parecerían apagados.
        ReflectionTestUtils.setField(scheduler, "vivoHabilitada", true);
        ReflectionTestUtils.setField(scheduler, "postPartidoHabilitada", true);
        ReflectionTestUtils.setField(scheduler, "recogidaHabilitada", true);
        ReflectionTestUtils.setField(scheduler, "recogidaHoras", 30);
        ReflectionTestUtils.setField(scheduler, "recogidaMaxPartidos", 1500);
        ReflectionTestUtils.setField(scheduler, "calendarioHabilitado", true);
        ReflectionTestUtils.setField(scheduler, "calendarioDias", 3);
        ReflectionTestUtils.setField(scheduler, "clasificacionesHabilitadas", true);
        ReflectionTestUtils.setField(scheduler, "clasificacionesHoras", 24);
        ReflectionTestUtils.setField(scheduler, "plantillasHabilitada", true);
        ReflectionTestUtils.setField(scheduler, "plantillasMaxHoras", 20);
        ReflectionTestUtils.setField(scheduler, "temporadaActual", 2026);
        ReflectionTestUtils.setField(scheduler, "reconciliacionMaxPartidos", 600);
        ReflectionTestUtils.setField(scheduler, "horasPartidoColgado", 6);
        ReflectionTestUtils.setField(scheduler, "puestaAlDiaHabilitada", true);
        ReflectionTestUtils.setField(scheduler, "puestaAlDiaDias", 7);
        ReflectionTestUtils.setField(scheduler, "puestaAlDiaMaxFechas", 7);
        ReflectionTestUtils.setField(scheduler, "puestaAlDiaMaxTablas", 500);
    }

    // =====================================================================================
    // PLANTILLAS
    // =====================================================================================
    // Sin este cron, jugadores.id_equipo solo se actualizaba cuando alguien lanzaba a mano la
    // pasada completa (11.508 equipos), o sea casi nunca: el 2026-09-08 el Real Madrid
    // figuraba con 45 jugadores y Kroos entre ellos, retirado hacia dos anos.

    @Test
    @DisplayName("El refresco diario de plantillas va acotado a las destacadas y caduca la marca")
    void plantillasDiariasAcotadasYConCaducidad() {
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(true);

        scheduler.refrescarPlantillasDestacadas();

        // true = solo las competiciones grandes (~400 peticiones, no 11.508).
        // 20 h = la marca de "ya sincronizado" caduca; con 0 se congelarian otra vez.
        verify(maestrosIngestService).ingestarJugadoresCompletosPorEquipo(2026, true, 20);
    }

    @Test
    @DisplayName("Sin cuota, el refresco de plantillas no pide nada")
    void plantillasRespetanLaCuota() {
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(false);

        scheduler.refrescarPlantillasDestacadas();

        verify(maestrosIngestService, never()).ingestarJugadoresCompletosPorEquipo(
                anyInt(), anyBoolean(), anyInt());
    }

    @Test
    @DisplayName("Apagado por configuracion, no corre")
    void plantillasApagadas() {
        ReflectionTestUtils.setField(scheduler, "plantillasHabilitada", false);

        scheduler.refrescarPlantillasDestacadas();

        verifyNoInteractions(maestrosIngestService);
    }

    @Test
    @DisplayName("Apagar el directo no apaga los demás trabajos")
    void apagarElDirectoNoApagaLoDemas() {
        ReflectionTestUtils.setField(scheduler, "vivoHabilitada", false);

        scheduler.sincronizarPartidosEnVivo();
        scheduler.atenderPartidosTerminados();

        verify(partidoIngestService, never()).ingestarEnVivo();
        // Antes toda la clase colgaba de la propiedad del directo, así que apagarlo se llevaba
        // por delante cualquier otra cosa programada aquí.
        verify(colaPostPartido).procesarRonda();
    }

    @Test
    @DisplayName("El calendario trae ayer, hoy y los días configurados hacia delante")
    void calendarioCubreAyerHoyYLosSiguientes() {
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(true);

        scheduler.sincronizarCalendario();

        ArgumentCaptor<String> fechas = ArgumentCaptor.forClass(String.class);
        verify(partidoIngestService, times(5)).ingestarPorFecha(fechas.capture());

        LocalDate hoy = LocalDate.now(ZoneId.of("UTC"));
        assertEquals(hoy.minusDays(1).toString(), fechas.getAllValues().get(0));
        assertEquals(hoy.plusDays(3).toString(), fechas.getAllValues().get(4));
    }

    @Test
    @DisplayName("Sin cuota, el calendario no pide ni una fecha")
    void calendarioRespetaLaCuota() {
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(false);

        scheduler.sincronizarCalendario();

        verify(partidoIngestService, never()).ingestarPorFecha(any());
    }

    @Test
    @DisplayName("Solo se refresca la clasificación de las competiciones donde se ha jugado")
    void clasificacionesSoloDondeSeHaJugado() {
        when(apiFootballClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findLigasYTemporadasConPartidosDesde(any()))
                .thenReturn(List.<Object[]>of(new Object[]{140, 2026}, new Object[]{39, 2026}));

        scheduler.refrescarClasificaciones();

        verify(maestrosIngestService).ingestarClasificaciones(140, 2026);
        verify(maestrosIngestService).ingestarClasificaciones(39, 2026);
        verifyNoMoreInteractions(maestrosIngestService);
    }

    @Test
    @DisplayName("Una competición que falla no corta el refresco de las demás")
    void unaClasificacionRotaNoCortaElResto() {
        when(apiFootballClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findLigasYTemporadasConPartidosDesde(any()))
                .thenReturn(List.<Object[]>of(new Object[]{140, 2026}, new Object[]{39, 2026}));
        doThrow(new IllegalStateException("boom")).when(maestrosIngestService).ingestarClasificaciones(140, 2026);

        scheduler.refrescarClasificaciones();

        verify(maestrosIngestService).ingestarClasificaciones(39, 2026);
    }

    @Test
    @DisplayName("Sin cuota de mantenimiento no se refresca ninguna clasificación")
    void clasificacionesRespetanElColchonDeCuota() {
        when(apiFootballClient.hayCuotaDisponibleParaBackfill()).thenReturn(false);
        when(partidoRepository.findLigasYTemporadasConPartidosDesde(any()))
                .thenReturn(List.<Object[]>of(new Object[]{140, 2026}));

        scheduler.refrescarClasificaciones();

        verify(maestrosIngestService, never()).ingestarClasificaciones(anyInt(), anyInt());
    }

    @Test
    @DisplayName("La recogida diaria usa la ventana y el tope configurados")
    void recogidaUsaSusParametros() {
        scheduler.recogerPartidosSinEstadisticas();

        verify(estadisticasIngestService).ingestarEstadisticasRecientes(30, 1500);
    }

    @Test
    @DisplayName("Un fallo del directo no tumba el scheduler")
    void elDirectoTragaSusExcepciones() {
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(true);
        when(partidoIngestService.ingestarEnVivo()).thenThrow(new IllegalStateException("API caída"));

        scheduler.sincronizarPartidosEnVivo();

        // Si la excepción se escapara, Spring cancelaría la tarea programada y el directo se
        // quedaría muerto hasta el siguiente reinicio.
        assertTrue(true);
    }

    // =====================================================================================
    // PUESTA AL DIA DESPUES DE UN PARON
    // =====================================================================================
    // La reconciliacion de mediodia solo corrige lo que se rompio con el servicio vivo. Los
    // dias en los que estuvo apagado no los mira nadie: su cron no dispara, y al dia siguiente
    // la ventana "ayer y hoy" ya ha pasado de largo. El 2026-09-10 eso dejaba 233 de los 336
    // partidos del dia anterior clavados en PROGRAMADO, con el resultado publicado hacia horas.

    @Test
    @DisplayName("Relee las fechas que quedaron sin cerrar y encola desde la mas antigua")
    void puestaAlDiaReleeLasFechasRotas() {
        LocalDate hoy = LocalDate.now(ZoneId.of("UTC"));
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(true);
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any())).thenReturn(
                List.of(hoy.minusDays(2).toString(), hoy.minusDays(1).toString()));
        when(partidoRepository.findPartidosRecientesPendientesDeStats(any(), any(), any()))
                .thenReturn(Page.empty());
        when(partidoRepository.findPartidosRecientesSinEventos(any(), any(), any()))
                .thenReturn(Page.empty());

        scheduler.ponerseAlDiaTrasUnParon();

        // Una peticion por dia, no una por partido: fixtures?date= trae la jornada entera.
        verify(partidoIngestService).ingestarPorFecha(hoy.minusDays(2).toString());
        verify(partidoIngestService).ingestarPorFecha(hoy.minusDays(1).toString());

        // Y lo recuperado se encola desde el dia mas antiguo, no desde ayer: si no, los
        // partidos del dia perdido volverian a quedarse sin estadisticas ni timeline.
        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(partidoRepository).findPartidosRecientesPendientesDeStats(
                desde.capture(), any(), any());
        assertEquals(hoy.minusDays(2).atStartOfDay(), desde.getValue());
    }

    @Test
    @DisplayName("Una fecha ya releida no se vuelve a pedir en el mismo arranque")
    void puestaAlDiaNoRepiteFecha() {
        LocalDate ayer = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(true);
        // La consulta sigue devolviendo la fecha porque siempre quedan partidos que la propia
        // API deja en NS para siempre (competiciones menores que no publican el resultado).
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any()))
                .thenReturn(List.of(ayer.toString()));
        when(partidoRepository.findPartidosRecientesPendientesDeStats(any(), any(), any()))
                .thenReturn(Page.empty());
        when(partidoRepository.findPartidosRecientesSinEventos(any(), any(), any()))
                .thenReturn(Page.empty());

        scheduler.ponerseAlDiaTrasUnParon();
        scheduler.ponerseAlDiaTrasUnParon();

        // Sin la marca, esos fantasmas costarian una peticion cada seis horas y para siempre.
        verify(partidoIngestService, times(1)).ingestarPorFecha(ayer.toString());
    }

    @Test
    @DisplayName("Una fecha que falla se reintenta en el siguiente ciclo")
    void puestaAlDiaReintentaLaFechaQueFalla() {
        LocalDate ayer = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(true);
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any()))
                .thenReturn(List.of(ayer.toString()));
        when(partidoIngestService.ingestarPorFecha(ayer.toString()))
                .thenThrow(new IllegalStateException("API caida"));

        scheduler.ponerseAlDiaTrasUnParon();
        scheduler.ponerseAlDiaTrasUnParon();

        // Darla por recuperada sin serlo la dejaria rota hasta el siguiente reinicio.
        verify(partidoIngestService, times(2)).ingestarPorFecha(ayer.toString());
        verify(partidoRepository, never()).findPartidosRecientesPendientesDeStats(
                any(), any(), any());
    }

    @Test
    @DisplayName("Sin cuota, la puesta al dia no relee ninguna fecha")
    void puestaAlDiaRespetaLaCuota() {
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(false);
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any()))
                .thenReturn(List.of(LocalDate.now(ZoneId.of("UTC")).minusDays(1).toString()));

        scheduler.ponerseAlDiaTrasUnParon();

        verify(partidoIngestService, never()).ingestarPorFecha(any());
    }

    @Test
    @DisplayName("Apagada por configuracion, ni siquiera consulta la base")
    void puestaAlDiaApagada() {
        ReflectionTestUtils.setField(scheduler, "puestaAlDiaHabilitada", false);

        scheduler.ponerseAlDiaTrasUnParon();

        verify(partidoRepository, never()).findFechasConPartidosSinCerrar(any(), any());
        verifyNoInteractions(partidoIngestService);
    }

    @Test
    @DisplayName("Rehace las tablas atrasadas aunque no falte ningun resultado")
    void puestaAlDiaRehaceTablasSinFaltarResultados() {
        // El caso real del 2026-09-07: los partidos estaban todos cerrados y aun asi La Liga
        // y la Serie A daban un partido de menos a ocho equipos, porque el refresco de
        // clasificaciones (ventana de 24 h) no llego a dispararse ese dia.
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any())).thenReturn(List.of());
        when(apiFootballClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findLigasYTemporadasConPartidosDesde(any()))
                .thenReturn(List.<Object[]>of(new Object[]{140, 2026}, new Object[]{135, 2026}));

        scheduler.ponerseAlDiaTrasUnParon();

        verify(maestrosIngestService).ingestarClasificaciones(140, 2026);
        verify(maestrosIngestService).ingestarClasificaciones(135, 2026);
    }

    @Test
    @DisplayName("Una tabla ya rehecha no se vuelve a pedir en el mismo arranque")
    void puestaAlDiaNoRepiteTabla() {
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any())).thenReturn(List.of());
        when(apiFootballClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findLigasYTemporadasConPartidosDesde(any()))
                .thenReturn(List.<Object[]>of(new Object[]{140, 2026}));

        scheduler.ponerseAlDiaTrasUnParon();
        scheduler.ponerseAlDiaTrasUnParon();

        // Sin la marca, cada ciclo de seis horas volveria a pedir las quinientas.
        verify(maestrosIngestService, times(1)).ingestarClasificaciones(140, 2026);
    }

    @Test
    @DisplayName("El tope de tablas corta la pasada y deja el resto para el ciclo siguiente")
    void puestaAlDiaRespetaElTopeDeTablas() {
        ReflectionTestUtils.setField(scheduler, "puestaAlDiaMaxTablas", 1);
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any())).thenReturn(List.of());
        when(apiFootballClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(partidoRepository.findLigasYTemporadasConPartidosDesde(any()))
                .thenReturn(List.<Object[]>of(new Object[]{140, 2026}, new Object[]{135, 2026}));

        scheduler.ponerseAlDiaTrasUnParon();

        verify(maestrosIngestService).ingestarClasificaciones(140, 2026);
        verify(maestrosIngestService, never()).ingestarClasificaciones(135, 2026);
    }

    @Test
    @DisplayName("Con las clasificaciones apagadas, la puesta al dia no toca ninguna tabla")
    void puestaAlDiaRespetaElInterruptorDeClasificaciones() {
        ReflectionTestUtils.setField(scheduler, "clasificacionesHabilitadas", false);
        when(partidoRepository.findFechasConPartidosSinCerrar(any(), any())).thenReturn(List.of());

        scheduler.ponerseAlDiaTrasUnParon();

        verify(partidoRepository, never()).findLigasYTemporadasConPartidosDesde(any());
        verifyNoInteractions(maestrosIngestService);
    }
}
