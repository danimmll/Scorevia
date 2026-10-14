package estadisticas.controllers.partidos;

import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import estadisticas.dto.partidos.PartidoDTO;
import estadisticas.services.partidos.PartidoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PartidoControllerImplTest {

    @Mock
    private PartidoServiceImpl partidoService;

    @InjectMocks
    private PartidoControllerImpl partidoController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(partidoController).build();
    }

    @Test
    void getPartidos_DebeRetornarPaginaDePartidos() throws Exception {
        PartidoDTO dto = new PartidoDTO();
        dto.setEstado("FINALIZADO");

        // FIX: Evitamos el UnsupportedOperationException al serializar a JSON
        Page<PartidoDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(partidoService.getPartidos(any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/partidos")
                        .param("page", "0")
                        .param("size", "20")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].estado").value("FINALIZADO"));

        verify(partidoService).getPartidos(any(PageRequest.class));
    }

    @Test
    void getPartidosEnVivo_DebeRetornarLista() throws Exception {
        PartidoDTO dto = new PartidoDTO();
        dto.setEstado("EN_JUEGO");

        when(partidoService.getPartidosEnVivo()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/partidos/en-vivo")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("EN_JUEGO"));

        verify(partidoService).getPartidosEnVivo();
    }

    @Test
    void getPartidoById_DebeRetornarPartido() throws Exception {
        Integer idPartido = 123;
        PartidoDTO dto = new PartidoDTO();
        dto.setIdPartido(123);

        when(partidoService.getPartidoById(idPartido)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/partidos/{id_partido}", "123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPartido").value(123));

        verify(partidoService).getPartidoById(idPartido);
    }

    @Test
    void getPartidosByLiga_DebeRetornarPagina() throws Exception {
        Integer idLiga = 140;
        PartidoDTO dto = new PartidoDTO();

        // FIX: Evitamos el UnsupportedOperationException al serializar a JSON
        Page<PartidoDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(partidoService.getPartidosByLiga(eq(idLiga), isNull(), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/partidos/liga/{id_liga}", "140")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(partidoService).getPartidosByLiga(eq(idLiga), isNull(), any(PageRequest.class));
    }

    @Test
    void getPartidosByEquipo_DebeRetornarPagina() throws Exception {
        Integer idEquipo = 541;
        PartidoDTO dto = new PartidoDTO();

        // FIX: Evitamos el UnsupportedOperationException al serializar a JSON
        Page<PartidoDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(partidoService.getPartidosByEquipo(eq(idEquipo), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/partidos/equipo/{id_equipo}", "541")
                        .param("page", "0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(partidoService).getPartidosByEquipo(eq(idEquipo), any(PageRequest.class));
    }

    @Test
    void getEnfrentamientosDirectos_DebeRetornarPagina() throws Exception {
        Integer idEquipoA = 541;
        Integer idEquipoB = 529;
        PartidoDTO dto = new PartidoDTO();

        // FIX: Evitamos el UnsupportedOperationException al serializar a JSON
        Page<PartidoDTO> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(partidoService.getEnfrentamientosDirectos(eq(idEquipoA), eq(idEquipoB), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/partidos/enfrentamientos")
                        .param("equipoA", "541")
                        .param("equipoB", "529")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(partidoService).getEnfrentamientosDirectos(eq(idEquipoA), eq(idEquipoB), any(PageRequest.class));
    }

    @Test
    void getEstadisticasPartido_DebeRetornarLista() throws Exception {
        Integer idPartido = 123;
        EstadisticaEquipoDTO dto = new EstadisticaEquipoDTO();
        dto.setNombreEquipo("Real Madrid");

        when(partidoService.getEstadisticasPartido(idPartido)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/partidos/{id_partido}/estadisticas", "123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreEquipo").value("Real Madrid"));

        verify(partidoService).getEstadisticasPartido(idPartido);
    }

    @Test
    void getEstadisticasJugadoresPartido_DebeRetornarLista() throws Exception {
        Integer idPartido = 123;
        EstadisticaJugadorPartidoDTO dto = new EstadisticaJugadorPartidoDTO();

        when(partidoService.getEstadisticasJugadoresPartido(idPartido)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/partidos/{id_partido}/jugadores", "123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(partidoService).getEstadisticasJugadoresPartido(idPartido);
    }
}