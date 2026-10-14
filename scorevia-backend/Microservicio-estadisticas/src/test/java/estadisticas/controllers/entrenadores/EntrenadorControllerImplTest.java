package estadisticas.controllers.entrenadores;

import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.services.entrenadores.EntrenadorService;
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
class EntrenadorControllerImplTest {

    @Mock
    private EntrenadorService entrenadorService;

    @InjectMocks
    private EntrenadorControllerImpl entrenadorController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(entrenadorController).build();
    }

    @Test
    void obtenerPorId_DebeRetornarEntrenador() throws Exception {
        Integer idEntrenador = 1;
        EntrenadorDTO dto = new EntrenadorDTO();
        dto.setNombre("Carlo Ancelotti");

        when(entrenadorService.obtenerEntrenadorPorId(idEntrenador)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/entrenadores/{id}", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Carlo Ancelotti"));

        verify(entrenadorService).obtenerEntrenadorPorId(idEntrenador);
    }

    @Test
    void obtenerPorEquipo_DebeRetornarEntrenador() throws Exception {
        Integer idEquipo = 541;
        EntrenadorDTO dto = new EntrenadorDTO();
        dto.setNombre("Carlo Ancelotti");

        when(entrenadorService.obtenerEntrenadorPorEquipo(idEquipo)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/entrenadores/equipo/{idEquipo}", "541")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Carlo Ancelotti"));

        verify(entrenadorService).obtenerEntrenadorPorEquipo(idEquipo);
    }

    @Test
    void obtenerTrofeos_DebeRetornarListaTrofeos() throws Exception {
        Integer idEntrenador = 1;
        TrofeoDTO trofeo = new TrofeoDTO();
        trofeo.setCompeticion("Champions League");

        when(entrenadorService.obtenerTrofeosEntrenador(idEntrenador)).thenReturn(List.of(trofeo));

        mockMvc.perform(get("/api/v1/entrenadores/{id}/trofeos", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].competicion").value("Champions League"));

        verify(entrenadorService).obtenerTrofeosEntrenador(idEntrenador);
    }
}