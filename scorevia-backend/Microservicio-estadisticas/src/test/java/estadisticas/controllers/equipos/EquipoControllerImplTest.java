package estadisticas.controllers.equipos;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.services.equipos.EquipoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EquipoControllerImplTest {

    @Mock
    private EquipoService equipoService;

    @InjectMocks
    private EquipoControllerImpl equipoController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(equipoController).build();
    }

    @Test
    void obtenerPorId_DebeRetornarEquipo() throws Exception {
        Integer idEquipo = 541;
        EquipoDTO dto = new EquipoDTO();
        dto.setNombre("Real Madrid");

        when(equipoService.obtenerEquipoPorId(idEquipo)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/equipos/{id}", "541")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Real Madrid"));

        verify(equipoService).obtenerEquipoPorId(idEquipo);
    }

    @Test
    void obtenerPorLiga_DebeRetornarListaDeEquipos() throws Exception {
        Integer idLiga = 140;
        Integer temporada = 2026;
        EquipoDTO dto = new EquipoDTO();
        dto.setNombre("Real Madrid");

        when(equipoService.obtenerEquiposPorLiga(idLiga, temporada)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/equipos/liga/{idLiga}", "140")
                        .param("temporada", "2026")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Real Madrid"));

        verify(equipoService).obtenerEquiposPorLiga(idLiga, temporada);
    }

    @Test
    void obtenerTraspasos_DebeRetornarListaDeTraspasos() throws Exception {
        Integer idEquipo = 541;
        TraspasoDTO traspaso = new TraspasoDTO();
        traspaso.setNombreJugador("Mbappé");
        traspaso.setTipo("Fichaje");

        when(equipoService.obtenerTraspasosPorEquipo(idEquipo)).thenReturn(List.of(traspaso));

        mockMvc.perform(get("/api/v1/equipos/{id}/traspasos", "541")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreJugador").value("Mbappé"));

        verify(equipoService).obtenerTraspasosPorEquipo(idEquipo);
    }
}