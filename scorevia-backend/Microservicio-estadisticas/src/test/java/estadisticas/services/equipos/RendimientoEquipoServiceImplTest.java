package estadisticas.services.equipos;

import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.dto.equipos.RendimientoEquipoDTO;
import estadisticas.services.external.ApiFootballClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * El mapeo del rendimiento de equipo, sobre la forma real de la respuesta.
 *
 * <p>El JSON de abajo está recortado de una llamada de verdad a
 * {@code /teams/statistics?league=140&team=529&season=2025}.</p>
 */
@ExtendWith(MockitoExtension.class)
class RendimientoEquipoServiceImplTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private ApiFootballClient apiFootballClient;

    @InjectMocks private RendimientoEquipoServiceImpl servicio;

    private static final String RESPUESTA_REAL = """
            {"response":{
              "form":"WWDWWWWLWLWWWW",
              "fixtures":{"played":{"home":19,"away":19,"total":38},
                          "wins":{"home":19,"away":12,"total":31},
                          "draws":{"home":0,"away":1,"total":1},
                          "loses":{"home":0,"away":6,"total":6}},
              "goals":{"for":{"total":{"home":60,"away":42,"total":102},
                              "average":{"total":"2.7"},
                              "minute":{"0-15":{"total":16,"percentage":"17.02%"},
                                        "16-30":{"total":null,"percentage":null}}},
                       "against":{"total":{"home":15,"away":24,"total":39},
                                  "average":{"total":"1.0"},
                                  "minute":{"0-15":{"total":4,"percentage":"10.25%"}}}},
              "clean_sheet":{"home":10,"away":5,"total":15},
              "failed_to_score":{"home":1,"away":3,"total":4},
              "biggest":{"wins":{"home":"6-0","away":"0-3"},"loses":{"home":null,"away":"5-1"}},
              "cards":{"yellow":{"0-15":{"total":6},"16-30":{"total":9},"31-45":{"total":null}},
                       "red":{"0-15":{"total":1},"16-30":{"total":null}}}
            }}""";

    @Test
    @DisplayName("Mapea el rendimiento de una respuesta real")
    void mapeaLaRespuestaReal() throws Exception {
        when(apiFootballClient.getEstadisticasEquipoTemporada(140, 529, 2025))
                .thenReturn(JSON.readTree(RESPUESTA_REAL));

        RendimientoEquipoDTO r = servicio.getRendimiento(529, 140, 2025);

        assertTrue(r.isHayDatos());
        assertEquals("WWDWWWWLWLWWWW", r.getForma());
        assertEquals(38, r.getBalance().getJugados());
        assertEquals(31, r.getBalance().getGanados());
        assertEquals(19, r.getBalance().getGanadosCasa());
        assertEquals(102, r.getGoles().getAFavor());
        assertEquals(39, r.getGoles().getEnContra());
        assertEquals("2.7", r.getGoles().getMediaAFavor());
        assertEquals(15, r.getPorteriasACero());
        assertEquals(4, r.getPartidosSinMarcar());
        assertEquals("6-0", r.getMayorVictoriaCasa());
        assertNull(r.getMayorDerrotaCasa(), "Un equipo que no perdió en casa no tiene mayor derrota");
    }

    @Test
    @DisplayName("Una franja sin goles llega como null y se enseña como 0, no se pierde")
    void franjaSinGoles() throws Exception {
        when(apiFootballClient.getEstadisticasEquipoTemporada(140, 529, 2025))
                .thenReturn(JSON.readTree(RESPUESTA_REAL));

        var franjas = servicio.getRendimiento(529, 140, 2025).getGoles().getMarcadosPorMinuto();

        assertEquals(2, franjas.size());
        assertEquals("0-15", franjas.get(0).getFranja());
        assertEquals(16, franjas.get(0).getGoles());
        // Si se saltara, el gráfico tendría un hueco en vez de una barra a cero.
        assertEquals(0, franjas.get(1).getGoles());
    }

    @Test
    @DisplayName("Las tarjetas se suman de todas las franjas, saltándose los nulos")
    void sumaTarjetas() throws Exception {
        when(apiFootballClient.getEstadisticasEquipoTemporada(140, 529, 2025))
                .thenReturn(JSON.readTree(RESPUESTA_REAL));

        RendimientoEquipoDTO r = servicio.getRendimiento(529, 140, 2025);

        assertEquals(15, r.getTarjetasAmarillas());   // 6 + 9, y el null se ignora
        assertEquals(1, r.getTarjetasRojas());
    }

    @Test
    @DisplayName("Un equipo sin partidos en esa competición devuelve hayDatos=false")
    void sinPartidos() throws Exception {
        // La API no devuelve un array vacío: devuelve el objeto entero con todo a cero/null.
        when(apiFootballClient.getEstadisticasEquipoTemporada(140, 999, 2025))
                .thenReturn(JSON.readTree("""
                        {"response":{"fixtures":{"played":{"total":0}}}}"""));

        assertFalse(servicio.getRendimiento(999, 140, 2025).isHayDatos());
    }

    @Test
    @DisplayName("Si la API falla, la ficha del equipo se pinta igual")
    void falloDeApi() {
        when(apiFootballClient.getEstadisticasEquipoTemporada(140, 529, 2025))
                .thenThrow(new IllegalStateException("boom"));

        assertFalse(servicio.getRendimiento(529, 140, 2025).isHayDatos());
    }
}
