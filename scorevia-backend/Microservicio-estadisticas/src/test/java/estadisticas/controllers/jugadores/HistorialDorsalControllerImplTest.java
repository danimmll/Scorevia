package estadisticas.controllers.jugadores;

import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.exception.BadRequestException;
import estadisticas.services.jugadores.HistorialDorsalService;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HistorialDorsalControllerImplTest {

    @Mock
    private HistorialDorsalService historialDorsalService;

    @InjectMocks
    private HistorialDorsalControllerImpl historialDorsalController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(historialDorsalController).build();
    }

    @Test
    void obtenerHistorial_DebeRetornarListaDeHistorial() throws Exception {
        Integer idJugador = 7;
        HistorialDorsalDTO dto = HistorialDorsalDTO.builder()
                .idEquipo(1).nombreEquipo("Real Madrid").temporada(2025).dorsal(7).posicion("Delantero").build();

        when(historialDorsalService.obtenerHistorialPorJugador(idJugador)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/jugadores/{idJugador}/historial-dorsales", "7")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreEquipo").value("Real Madrid"))
                .andExpect(jsonPath("$[0].temporada").value(2025));

        verify(historialDorsalService).obtenerHistorialPorJugador(idJugador);
    }

    @Test
    void obtenerHistorial_IdInvalido_DebeLanzarBadRequestException() {
        // Probamos la excepción directamente invocando el controlador para saltarnos que MockMvc envuelva el error
        assertThrows(BadRequestException.class, () -> historialDorsalController.obtenerHistorial("no-es-un-id"));
    }
}
