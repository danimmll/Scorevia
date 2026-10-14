package estadisticas.services.external;

import estadisticas.entities.equipos.Equipo;
import estadisticas.repositories.equipos.EquipoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La sincronización de plantillas tiene que poder retomarse.
 *
 * <p>Son ~11.500 equipos y unas 17.000 peticiones: cualquier corte a media ingesta —una
 * cuota agotada, o un despliegue— obligaba antes a repetirlo entero desde el primer equipo.
 * Pasó dos veces el 2026-09-04.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlantillasReanudablesTest {

    @Mock private ApiFootballClient apiClient;
    @Mock private EquipoRepository equipoRepository;
    @Mock private estadisticas.repositories.jugadores.JugadorRepository jugadorRepository;
    @Mock private estadisticas.services.ligas.LigasDestacadas ligasDestacadas;
    @Mock private estadisticas.services.ligas.ClubesDestacados clubesDestacados;
    @InjectMocks private MaestrosIngestServiceImpl servicio;

    /**
     * Una respuesta de plantilla con un jugador dentro.
     *
     * <p>Hace falta desde que un equipo solo se marca como sincronizado si la API devolvió
     * algo: sin plantilla, la pasada considera —con razón— que ese equipo sigue pendiente.</p>
     */
    private com.fasterxml.jackson.databind.JsonNode plantillaConUnJugador() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("{\"paging\": {\"total\": 1}, \"response\": [{\"player\": {\"id\": 99}}]}");
    }

    private Equipo equipo(int id) {
        Equipo e = new Equipo();
        e.setIdEquipo(id);
        e.setNombre("Equipo " + id);
        return e;
    }

    @Test
    @DisplayName("Cada equipo terminado se anota, para que el siguiente arranque no lo repita")
    void marcaCadaEquipoProcesado() throws Exception {
        ReflectionTestUtils.setField(servicio, "temporadaActual", 2026);
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayersByTeamAndSeason(anyInt(), anyInt(), anyInt()))
                .thenReturn(plantillaConUnJugador());
        when(jugadorRepository.findById(anyInt())).thenReturn(java.util.Optional.empty());
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), anyInt(), anyInt()))
                .thenReturn(List.of(equipo(1), equipo(2), equipo(3)))
                .thenReturn(List.of());

        servicio.ingestarJugadoresCompletosPorEquipo(2026);

        verify(equipoRepository).marcarPlantillaSincronizada(1, 2026);
        verify(equipoRepository).marcarPlantillaSincronizada(2, 2026);
        verify(equipoRepository).marcarPlantillaSincronizada(3, 2026);
    }

    @Test
    @DisplayName("Al quedarse sin cuota para de golpe y no marca el equipo que no llegó a procesar")
    void paraSinMarcarLoQueNoHizo() throws Exception {
        ReflectionTestUtils.setField(servicio, "temporadaActual", 2026);
        // Cuota para el primero y se acaba antes del segundo.
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true, false);
        when(apiClient.getPlayersByTeamAndSeason(anyInt(), anyInt(), anyInt()))
                .thenReturn(plantillaConUnJugador());
        when(jugadorRepository.findById(anyInt())).thenReturn(java.util.Optional.empty());
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), anyInt(), anyInt()))
                .thenReturn(List.of(equipo(1), equipo(2)));

        servicio.ingestarJugadoresCompletosPorEquipo(2026);

        verify(equipoRepository).marcarPlantillaSincronizada(1, 2026);
        // El 2 no se procesó, así que debe seguir pendiente para la próxima pasada.
        verify(equipoRepository, never()).marcarPlantillaSincronizada(2, 2026);
    }

    @Test
    @DisplayName("Sin equipos pendientes no gasta ni una petición")
    void sinPendientesNoHaceNada() {
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), anyInt(), anyInt()))
                .thenReturn(List.of());

        servicio.ingestarJugadoresCompletosPorEquipo(2026);

        verifyNoInteractions(apiClient);
        verify(equipoRepository, never()).marcarPlantillaSincronizada(anyInt(), anyInt());
    }

    /**
     * La regresión del 2026-09-09: la marca de "ya sincronizado" no caducaba, así que un
     * equipo mirado una vez no se volvía a mirar nunca. La sincronización dijo "0 pendientes"
     * y no pidió nada, con Kroos todavía en el Madrid y el arreglo que lo suelta ya desplegado.
     */
    @Test
    @DisplayName("La antigüedad con la que refrescar llega hasta la consulta")
    void pasaLaAntiguedadALaConsulta() {
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), eq(7), anyInt()))
                .thenReturn(List.of());

        servicio.ingestarJugadoresCompletosPorEquipo(2026, false, 7);

        verify(equipoRepository).findEquiposPendientesDePlantilla(eq(2026), eq(7), anyInt());
        verify(equipoRepository, never()).findEquiposPendientesDePlantillaEnClubes(
                anyInt(), anyList(), anyInt(), anyInt());
    }

    /**
     * El 2026-09-09, tras arreglar el filtro, la transacción y el bucle, Kroos SEGUÍA en el
     * Real Madrid: la pasada recorrió el club, la petición se perdió, y el equipo quedó
     * anotado como hecho igualmente. Con la marca puesta ya no se volvía a intentar.
     */
    @Test
    @DisplayName("Si la API no devuelve plantilla, el equipo NO se marca como hecho")
    void sinRespuestaDeLaApiNoSeMarca() throws Exception {
        ReflectionTestUtils.setField(servicio, "temporadaActual", 2026);
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), anyInt(), anyInt()))
                .thenReturn(List.of(equipo(1)))
                .thenReturn(List.of());
        // Respuesta vacía: "no lo sé", no "no queda nadie".
        when(apiClient.getPlayersByTeamAndSeason(eq(1), eq(2026), anyInt()))
                .thenReturn(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree("{\"response\": []}"));

        servicio.ingestarJugadoresCompletosPorEquipo(2026);

        verify(equipoRepository, never()).marcarPlantillaSincronizada(1, 2026);
    }

    @Test
    @DisplayName("Con antigüedad negativa se repasan todos, tengan la marca que tengan")
    void repasoForzadoDeTodos() {
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), eq(-1), anyInt()))
                .thenReturn(List.of());

        servicio.ingestarJugadoresCompletosPorEquipo(2026, false, -1);

        verify(equipoRepository).findEquiposPendientesDePlantilla(eq(2026), eq(-1), anyInt());
    }

    /**
     * El bucle se apoyaba en que marcar el equipo lo sacaba del lote siguiente, y con
     * "repásalos todos" la consulta ignora la marca a proposito: el mismo lote volvia una y
     * otra vez y la ingesta se quedo dando vueltas gastando peticiones hasta que se paro a
     * mano. Aqui la consulta devuelve SIEMPRE el mismo equipo, que es el caso peor.
     */
    @Test
    @DisplayName("Aunque la consulta devuelva siempre lo mismo, cada equipo se pide una vez")
    void noRepiteEquipoAunqueElLoteSeRepita() throws Exception {
        ReflectionTestUtils.setField(servicio, "temporadaActual", 2026);
        when(apiClient.hayCuotaDisponibleParaBackfill()).thenReturn(true);
        when(apiClient.getPlayersByTeamAndSeason(anyInt(), anyInt(), anyInt()))
                .thenReturn(plantillaConUnJugador());
        when(jugadorRepository.findById(anyInt())).thenReturn(java.util.Optional.empty());
        when(equipoRepository.findEquiposPendientesDePlantilla(eq(2026), eq(-1), anyInt()))
                .thenReturn(List.of(equipo(1)));

        servicio.ingestarJugadoresCompletosPorEquipo(2026, false, -1);

        verify(equipoRepository, times(1)).marcarPlantillaSincronizada(1, 2026);
    }

    @Test
    @DisplayName("Acotado a las destacadas pregunta por CLUBES, no por competiciones")
    void acotadoALosClubesDestacados() {
        // Por club y no por liga: equipos.id_liga es la FK que la ultima ingesta pisa, y el
        // 2026-09-09 dejaba fuera al Real Madrid (figuraba en "International Champions Cup"),
        // asi que esta sincronizacion recorria 218 equipos que no eran los grandes.
        when(clubesDestacados.ids()).thenReturn(List.of(541, 33));
        when(equipoRepository.findEquiposPendientesDePlantillaEnClubes(eq(2026), anyList(), eq(1), anyInt()))
                .thenReturn(List.of());

        servicio.ingestarJugadoresCompletosPorEquipo(2026, true, 1);

        verify(equipoRepository).findEquiposPendientesDePlantillaEnClubes(
                eq(2026), eq(List.of(541, 33)), eq(1), anyInt());
        verify(equipoRepository, never()).findEquiposPendientesDePlantilla(anyInt(), anyInt(), anyInt());
    }
}
