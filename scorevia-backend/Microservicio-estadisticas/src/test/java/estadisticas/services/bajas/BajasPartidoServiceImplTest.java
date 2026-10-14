package estadisticas.services.bajas;

import estadisticas.dto.external.ApiInjuriesResponseDTO;
import estadisticas.dto.external.TeamInfoDTO;
import estadisticas.dto.partidos.BajaPartidoDTO;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.partidos.PartidoRepository;
import estadisticas.services.external.ApiFootballClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BajasPartidoServiceImplTest {

    @Mock private PartidoRepository partidoRepository;
    @Mock private ApiFootballClient apiFootballClient;

    @InjectMocks private BajasPartidoServiceImpl servicio;

    private Partido partido;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(servicio, "diasVentanaBajas", 30);
        partido = new Partido();
        partido.setIdPartido(1);
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).plusDays(1));
    }

    @Test
    @DisplayName("Distingue la baja segura de la duda")
    void separaBajasDeDudas() {
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));
        when(apiFootballClient.getInjuriesByFixtures(List.of(1))).thenReturn(
                respuesta(item(10, "Missing Fixture", "Muscle Injury"),
                          item(11, "Questionable", "Knock")));

        List<BajaPartidoDTO> bajas = servicio.getBajasPartido(1);

        assertEquals(2, bajas.size());
        assertTrue(bajas.get(0).isBaja());
        assertFalse(bajas.get(1).isBaja(), "Questionable es duda, no baja segura");
        assertEquals("Muscle Injury", bajas.get(0).getMotivo());
    }

    @Test
    @DisplayName("De un partido de hace años no se preguntan las bajas")
    void partidoViejoNoPregunta() {
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).minusYears(2));
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));

        assertTrue(servicio.getBajasPartido(1).isEmpty());
        verify(apiFootballClient, never()).getInjuriesByFixtures(any());
    }

    @Test
    @DisplayName("Si la API falla, la ficha se pinta sin bajas en vez de romperse")
    void falloDeApiDevuelveVacio() {
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));
        when(apiFootballClient.getInjuriesByFixtures(any())).thenThrow(new IllegalStateException("boom"));

        assertTrue(servicio.getBajasPartido(1).isEmpty());
    }

    // ------------------------------------------------------------------
    private ApiInjuriesResponseDTO respuesta(ApiInjuriesResponseDTO.InjuryItemDTO... items) {
        ApiInjuriesResponseDTO respuesta = new ApiInjuriesResponseDTO();
        respuesta.setResponse(List.of(items));
        return respuesta;
    }

    private ApiInjuriesResponseDTO.InjuryItemDTO item(int idJugador, String tipo, String motivo) {
        ApiInjuriesResponseDTO.InjuryPlayerDTO jugador = new ApiInjuriesResponseDTO.InjuryPlayerDTO();
        jugador.setId(idJugador);
        jugador.setName("Jugador " + idJugador);
        jugador.setType(tipo);
        jugador.setReason(motivo);

        TeamInfoDTO equipo = new TeamInfoDTO();
        equipo.setId(100);
        equipo.setName("Equipo");

        ApiInjuriesResponseDTO.InjuryItemDTO item = new ApiInjuriesResponseDTO.InjuryItemDTO();
        item.setPlayer(jugador);
        item.setTeam(equipo);
        return item;
    }
}
