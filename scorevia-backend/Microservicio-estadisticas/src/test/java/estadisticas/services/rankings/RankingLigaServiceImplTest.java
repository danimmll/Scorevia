package estadisticas.services.rankings;

import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.dto.ligas.RankingJugadorDTO;
import estadisticas.services.external.ApiFootballClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingLigaServiceImplTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private ApiFootballClient apiFootballClient;

    @InjectMocks private RankingLigaServiceImpl servicio;

    @Test
    @DisplayName("Los goleadores salen numerados y con su equipo")
    void mapeaGoleadores() throws Exception {
        when(apiFootballClient.getTopScorers(140, 2026)).thenReturn(JSON.readTree("""
                {"response":[
                  {"player":{"id":1,"name":"Lewandowski","photo":"foto1"},
                   "statistics":[{"team":{"id":529,"name":"Barcelona","logo":"escudo"},
                                  "games":{"appearences":30,"minutes":2600},
                                  "goals":{"total":23,"assists":4},
                                  "cards":{"yellow":2,"red":0}}]},
                  {"player":{"id":2,"name":"Mbappé","photo":"foto2"},
                   "statistics":[{"team":{"id":541,"name":"Real Madrid","logo":"escudo2"},
                                  "games":{"appearences":29,"minutes":2500},
                                  "goals":{"total":21,"assists":6},
                                  "cards":{"yellow":1,"red":0}}]}
                ]}"""));

        List<RankingJugadorDTO> ranking = servicio.getRanking(140, 2026, RankingLigaService.Tipo.GOLES);

        assertEquals(2, ranking.size());
        assertEquals(1, ranking.get(0).getPuesto());
        assertEquals(23, ranking.get(0).getValor());
        assertEquals("Barcelona", ranking.get(0).getNombreEquipo());
        assertEquals(2, ranking.get(1).getPuesto());
    }

    @Test
    @DisplayName("El mismo mapeo sirve para asistencias y tarjetas, cambiando el número")
    void elValorDependeDelTipo() throws Exception {
        String json = """
                {"response":[{"player":{"id":1,"name":"X"},
                  "statistics":[{"team":{"id":9,"name":"Equipo"},
                                 "games":{"appearences":10,"minutes":900},
                                 "goals":{"total":3,"assists":7},
                                 "cards":{"yellow":11,"red":2}}]}]}""";
        when(apiFootballClient.getTopAssists(140, 2026)).thenReturn(JSON.readTree(json));
        when(apiFootballClient.getTopYellowCards(140, 2026)).thenReturn(JSON.readTree(json));
        when(apiFootballClient.getTopRedCards(140, 2026)).thenReturn(JSON.readTree(json));

        assertEquals(7, servicio.getRanking(140, 2026, RankingLigaService.Tipo.ASISTENCIAS).get(0).getValor());
        assertEquals(11, servicio.getRanking(140, 2026, RankingLigaService.Tipo.AMARILLAS).get(0).getValor());
        assertEquals(2, servicio.getRanking(140, 2026, RankingLigaService.Tipo.ROJAS).get(0).getValor());
    }

    @Test
    @DisplayName("Una competición que no cubre esta estadística devuelve lista vacía, no un error")
    void sinCoberturaListaVacia() throws Exception {
        when(apiFootballClient.getTopScorers(1, 2026)).thenReturn(JSON.readTree("""
                {"response":[]}"""));

        assertTrue(servicio.getRanking(1, 2026, RankingLigaService.Tipo.GOLES).isEmpty());
    }

    @Test
    @DisplayName("Si la API falla, la pantalla de la liga se pinta igual")
    void falloDeApiListaVacia() {
        when(apiFootballClient.getTopScorers(1, 2026)).thenThrow(new IllegalStateException("boom"));

        assertTrue(servicio.getRanking(1, 2026, RankingLigaService.Tipo.GOLES).isEmpty());
    }
}
