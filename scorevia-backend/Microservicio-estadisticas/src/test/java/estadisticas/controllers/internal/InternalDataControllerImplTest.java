package estadisticas.controllers.internal;

import estadisticas.dto.internal.EventoPartidoInternoDTO;
import estadisticas.dto.internal.FormaPrediccionDTO;
import estadisticas.services.internal.InternalDataService;
import estadisticas.services.partidos.PartidoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalDataControllerImplTest {

    @Mock
    private InternalDataService internalDataService;

    @Mock
    private PartidoService partidoService;

    @InjectMocks
    private InternalDataControllerImpl internalDataController;

    @Test
    void getContextoPrediccion_DebeRetornarContexto() {
        Integer idPartido = 1234;
        FormaPrediccionDTO mockDto = new FormaPrediccionDTO();

        when(internalDataService.generarContextoPorPartido(idPartido)).thenReturn(mockDto);

        ResponseEntity<FormaPrediccionDTO> response = internalDataController.getContextoPrediccion(idPartido);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockDto, response.getBody());

        verify(internalDataService).generarContextoPorPartido(idPartido);
    }

    @Test
    void getEventosPartido_DebeRetornarEventosDelServicio() {
        Integer idPartido = 1234;
        List<EventoPartidoInternoDTO> mockEventos = List.of(EventoPartidoInternoDTO.builder()
                .tiempoMinuto(75)
                .tipoEvento("Card")
                .detalle("Red Card")
                .equipo(EventoPartidoInternoDTO.EquipoInternoDTO.builder().idEquipo(541).build())
                .build());

        when(partidoService.getEventosPartidoInterno(idPartido)).thenReturn(mockEventos);

        ResponseEntity<List<EventoPartidoInternoDTO>> response = internalDataController.getEventosPartido(idPartido);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockEventos, response.getBody());

        verify(partidoService).getEventosPartidoInterno(idPartido);
    }

}