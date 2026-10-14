package estadisticas.services.cuotas;

import estadisticas.dto.external.ApiOddsResponseDTO;
import estadisticas.dto.partidos.CuotasPartidoDTO;
import estadisticas.entities.partidos.Partido;
import estadisticas.exception.NotFoundException;
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

/**
 * Lo que importa de las cuotas es cuándo NO se piden.
 *
 * <p>Cada consulta es una petición a API-Football, así que hay dos cosas que fijar: que no se
 * pregunte por partidos fuera de la ventana en la que la API tiene cuotas (14 días antes, 7
 * después), y que un fallo suyo no tumbe la ficha del partido entera.</p>
 */
@ExtendWith(MockitoExtension.class)
class CuotasServiceImplTest {

    @Mock private PartidoRepository partidoRepository;
    @Mock private ApiFootballClient apiFootballClient;

    @InjectMocks private CuotasServiceImpl servicio;

    private Partido partido;

    @BeforeEach
    void setUp() {
        // @Value no se inyecta en un test de Mockito puro.
        ReflectionTestUtils.setField(servicio, "casasPreferidas", "Bet365,Pinnacle");
        ReflectionTestUtils.setField(servicio, "diasHistoricoCuotas", 7);
        ReflectionTestUtils.setField(servicio, "diasAntelacionCuotas", 14);

        partido = new Partido();
        partido.setIdPartido(1);
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).plusDays(1));
    }

    @Test
    @DisplayName("Un partido de hace meses no gasta ni una petición")
    void partidoAntiguoNoPregunta() {
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).minusMonths(3));
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));

        CuotasPartidoDTO cuotas = servicio.getCuotasPartido(1);

        assertFalse(cuotas.isHayCuotas());
        // La API solo guarda 7 días de histórico de cuotas: preguntar es tirar la petición.
        verify(apiFootballClient, never()).getOddsByFixture(any());
    }

    @Test
    @DisplayName("Un partido dentro de un mes tampoco: las cuotas salen 14 días antes")
    void partidoMuyLejanoNoPregunta() {
        partido.setFecha(LocalDateTime.now(ZoneId.of("UTC")).plusDays(30));
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));

        assertFalse(servicio.getCuotasPartido(1).isHayCuotas());
        verify(apiFootballClient, never()).getOddsByFixture(any());
    }

    @Test
    @DisplayName("Se quedan los tres mercados que se enseñan, con la línea de 2.5 goles")
    void mapeaLosTresMercados() {
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));
        when(apiFootballClient.getOddsByFixture(1)).thenReturn(respuestaCompleta("Bet365"));

        CuotasPartidoDTO cuotas = servicio.getCuotasPartido(1);

        assertTrue(cuotas.isHayCuotas());
        assertEquals("Bet365", cuotas.getCasa());
        assertEquals(3, cuotas.getMercados().size());

        CuotasPartidoDTO.MercadoCuotasDTO ganador = cuotas.getMercados().get(0);
        assertEquals("GANADOR", ganador.getClave());
        assertEquals(List.of("Local", "Empate", "Visitante"),
                ganador.getOpciones().stream().map(CuotasPartidoDTO.OpcionCuotaDTO::getEtiqueta).toList());

        // El mercado de goles trae TODAS las líneas (0.5, 1.5, 2.5...): sin filtrar saldrían
        // veinte opciones en pantalla en vez de las dos que interesan.
        CuotasPartidoDTO.MercadoCuotasDTO goles = cuotas.getMercados().get(1);
        assertEquals("GOLES", goles.getClave());
        assertEquals(2, goles.getOpciones().size());
        assertTrue(goles.getOpciones().stream().allMatch(o -> o.getEtiqueta().contains("2.5")));
    }

    @Test
    @DisplayName("Se elige la casa preferida aunque venga la segunda en la respuesta")
    void eligeLaCasaPreferida() {
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));

        ApiOddsResponseDTO respuesta = respuestaCompleta("Otra Casa");
        ApiOddsResponseDTO.BookmakerDTO preferida = respuestaCompleta("Pinnacle")
                .getResponse().get(0).getBookmakers().get(0);
        respuesta.getResponse().get(0).setBookmakers(
                List.of(respuesta.getResponse().get(0).getBookmakers().get(0), preferida));
        when(apiFootballClient.getOddsByFixture(1)).thenReturn(respuesta);

        assertEquals("Pinnacle", servicio.getCuotasPartido(1).getCasa());
    }

    @Test
    @DisplayName("Si la API falla, la ficha del partido se pinta igual sin cuotas")
    void unFalloDeLaApiNoRompeLaFicha() {
        when(partidoRepository.findById(1)).thenReturn(Optional.of(partido));
        when(apiFootballClient.getOddsByFixture(1)).thenThrow(new IllegalStateException("API caída"));

        CuotasPartidoDTO cuotas = servicio.getCuotasPartido(1);

        assertFalse(cuotas.isHayCuotas());
        assertTrue(cuotas.getMercados().isEmpty());
    }

    @Test
    @DisplayName("Un partido que no existe es 404, no una respuesta vacía")
    void partidoInexistente() {
        when(partidoRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> servicio.getCuotasPartido(99));
    }

    // ------------------------------------------------------------------
    private ApiOddsResponseDTO respuestaCompleta(String casa) {
        ApiOddsResponseDTO.BetDTO ganador = bet("Match Winner",
                valor("Home", "1.95"), valor("Draw", "3.40"), valor("Away", "4.10"));
        ApiOddsResponseDTO.BetDTO goles = bet("Goals Over/Under",
                valor("Over 1.5", "1.30"), valor("Over 2.5", "1.90"),
                valor("Under 2.5", "1.85"), valor("Under 3.5", "1.40"));
        ApiOddsResponseDTO.BetDTO ambos = bet("Both Teams Score",
                valor("Yes", "1.72"), valor("No", "2.05"));

        ApiOddsResponseDTO.BookmakerDTO bookmaker = new ApiOddsResponseDTO.BookmakerDTO();
        bookmaker.setName(casa);
        bookmaker.setBets(List.of(ganador, goles, ambos));

        ApiOddsResponseDTO.OddsFixtureDTO entrada = new ApiOddsResponseDTO.OddsFixtureDTO();
        entrada.setUpdate("2026-09-05T09:00:00+00:00");
        entrada.setBookmakers(List.of(bookmaker));

        ApiOddsResponseDTO respuesta = new ApiOddsResponseDTO();
        respuesta.setResponse(List.of(entrada));
        return respuesta;
    }

    private ApiOddsResponseDTO.BetDTO bet(String nombre, ApiOddsResponseDTO.BetValueDTO... valores) {
        ApiOddsResponseDTO.BetDTO bet = new ApiOddsResponseDTO.BetDTO();
        bet.setName(nombre);
        bet.setValues(List.of(valores));
        return bet;
    }

    private ApiOddsResponseDTO.BetValueDTO valor(String valor, String cuota) {
        ApiOddsResponseDTO.BetValueDTO v = new ApiOddsResponseDTO.BetValueDTO();
        v.setValue(valor);
        v.setOdd(cuota);
        return v;
    }
}
