package estadisticas.services.external;

import estadisticas.dto.external.ApiFootballEventsResponseDTO;
import estadisticas.dto.external.TeamInfoDTO;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.partidos.EventoPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

/**
 * Cubre específicamente el contador de reintentos de {@code ingestarEventosDePartido}: antes de
 * este fix, una única respuesta vacía de la API de eventos bloqueaba el partido para siempre
 * (sin reintentos), a diferencia del mismo patrón ya probado para {@code statsNotFound} en
 * {@code EstadisticasIngestServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class EventoPartidoIngestServiceImplIngestaEventosTest {

    @Mock private ApiFootballClient apiClient;
    @Mock private EventoPartidoRepository eventoPartidoRepository;
    @Mock private JugadorRepository jugadorRepository;
    @Mock private PartidoRepository partidoRepository;
    @Mock private EventoPartidoIngestService self;

    @InjectMocks
    private EventoPartidoIngestServiceImpl service;

    private Partido crearPartido() {
        Partido p = new Partido();
        p.setIdPartido(1);
        Equipo local = new Equipo();
        local.setIdEquipo(10);
        Equipo visitante = new Equipo();
        visitante.setIdEquipo(20);
        p.setEquipoLocal(local);
        p.setEquipoVisitante(visitante);
        return p;
    }

    private ApiFootballEventsResponseDTO respuestaVacia() {
        ApiFootballEventsResponseDTO dto = new ApiFootballEventsResponseDTO();
        dto.setResponse(Collections.emptyList());
        return dto;
    }

    @Test
    void respuestaVacia_PrimerIntento_NoBloqueaTodavia() {
        Partido partido = crearPartido();
        when(apiClient.getEventosFixture(1)).thenReturn(respuestaVacia());

        service.ingestarEventosDePartido(partido);

        assertEquals(1, partido.getIntentosEventos());
        assertNotEquals(Boolean.TRUE, partido.getEventosNotFound());
    }

    @Test
    void respuestaVacia_TercerIntentoConsecutivo_BloqueaDefinitivamente() {
        Partido partido = crearPartido();
        partido.setIntentosEventos(2);
        when(apiClient.getEventosFixture(1)).thenReturn(respuestaVacia());

        service.ingestarEventosDePartido(partido);

        assertEquals(3, partido.getIntentosEventos());
        assertEquals(Boolean.TRUE, partido.getEventosNotFound());
    }

    @Test
    void respuestaConEventos_ResetaContadorYDesbloqueaPartido() {
        Partido partido = crearPartido();
        partido.setIntentosEventos(2);
        partido.setEventosNotFound(true);

        ApiFootballEventsResponseDTO.EventItemDTO item = new ApiFootballEventsResponseDTO.EventItemDTO();
        TeamInfoDTO team = new TeamInfoDTO();
        team.setId(10);
        item.setTeam(team);
        ApiFootballEventsResponseDTO.EventTimeDTO time = new ApiFootballEventsResponseDTO.EventTimeDTO();
        time.setElapsed(10);
        item.setTime(time);
        item.setType("Goal");

        ApiFootballEventsResponseDTO respuesta = new ApiFootballEventsResponseDTO();
        respuesta.setResponse(List.of(item));
        when(apiClient.getEventosFixture(1)).thenReturn(respuesta);

        service.ingestarEventosDePartido(partido);

        assertEquals(0, partido.getIntentosEventos());
        assertNotEquals(Boolean.TRUE, partido.getEventosNotFound());
    }
}
