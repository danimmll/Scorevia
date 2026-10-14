package estadisticas.controllers.clasificacion;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.ligas.ClasificacionDTO;
import estadisticas.services.clasificacion.ClasificacionService;
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
class ClasificacionControllerImplTest {

    @Mock
    private ClasificacionService clasificacionService;

    @InjectMocks
    private ClasificacionControllerImpl clasificacionController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // standaloneSetup aisla el controlador sin cargar la seguridad ni Spring
        mockMvc = MockMvcBuilders.standaloneSetup(clasificacionController).build();
    }

    @Test
    void obtenerTabla_DebeRetornarListaDeClasificacion() throws Exception {
        Integer idLiga = 140;
        Integer temporada = 2026;

        EquipoDTO equipoDTO = new EquipoDTO();
        equipoDTO.setNombre("Real Madrid");

        ClasificacionDTO dto = new ClasificacionDTO();
        dto.setEquipo(equipoDTO);
        dto.setPosicion(1);

        when(clasificacionService.obtenerClasificacion(idLiga, temporada)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/clasificaciones/liga/{idLiga}/temporada/{temporada}", "140", "2026")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipo.nombre").value("Real Madrid"))
                .andExpect(jsonPath("$[0].posicion").value(1));

        verify(clasificacionService).obtenerClasificacion(idLiga, temporada);
    }
}