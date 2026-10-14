package estadisticas.controllers.ligas;

import estadisticas.dto.ligas.LigaDTO;
import estadisticas.services.ligas.LigaService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LigaControllerImplTest {

    @Mock
    private LigaService ligaService;

    @InjectMocks
    private LigaControllerImpl ligaController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ligaController).build();
    }

    @Test
    void obtenerTodas_DebeRetornarListaDeLigas() throws Exception {
        LigaDTO dto = new LigaDTO();
        dto.setNombre("La Liga");

        when(ligaService.obtenerTodasLigas()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/ligas")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("La Liga"));

        verify(ligaService).obtenerTodasLigas();
    }

    @Test
    void obtenerPorId_DebeRetornarLiga() throws Exception {
        Integer idLiga = 140;
        LigaDTO dto = new LigaDTO();
        dto.setNombre("La Liga");

        when(ligaService.obtenerLigaPorId(idLiga)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/ligas/{id}", "140")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("La Liga"));

        verify(ligaService).obtenerLigaPorId(idLiga);
    }
}