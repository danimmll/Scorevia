package incidencias.controllers.incidencias;

import incidencias.dto.IncidenciaDTO;
import incidencias.exception.BadRequestException;
import incidencias.exception.NoContentException;
import incidencias.services.incidencias.IncidenciaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidenciaControllerImplTest {

    @Mock
    private IncidenciaServiceImpl incidenciaService;

    @InjectMocks
    private IncidenciaControllerImpl incidenciaController;

    private IncidenciaDTO incidenciaDTO;

    @BeforeEach
    void setUp() {
        incidenciaDTO = new IncidenciaDTO();
        incidenciaDTO.setIdIncidencia(1);
    }

    @Test
    void getAll_DeberiaDevolverOk_ConDatosYOrdenDesc() {
        Page<IncidenciaDTO> page = new PageImpl<>(List.of(incidenciaDTO));
        when(incidenciaService.getIncidenciasDTO(any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<IncidenciaDTO>> response = incidenciaController.getAll(0, 10, "idIncidencia", "desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody(), "El cuerpo de la respuesta no debería ser nulo");
        assertFalse(response.getBody().isEmpty(), "La lista paginada no debería estar vacía");
    }

    @Test
    void getAll_DeberiaLanzarNoContent_CuandoVacio() {
        Page<IncidenciaDTO> page = new PageImpl<>(new ArrayList<>());
        when(incidenciaService.getIncidenciasDTO(any(Pageable.class))).thenReturn(page);

        assertThrows(NoContentException.class, () -> incidenciaController.getAll(0, 10, "idIncidencia", "asc"));
    }

    @Test
    void getAll_DeberiaLanzarBadRequest_CuandoSortNoEsValido() {
        assertThrows(BadRequestException.class, () -> incidenciaController.getAll(0, 10, "foo", "asc"));

        verify(incidenciaService, never()).getIncidenciasDTO(any(Pageable.class));
    }

    @Test
    void getAll_DeberiaAceptar_CuandoSortEsValido() {
        Page<IncidenciaDTO> page = new PageImpl<>(List.of(incidenciaDTO));
        when(incidenciaService.getIncidenciasDTO(any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<IncidenciaDTO>> response = incidenciaController.getAll(0, 10, "fecha", "desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getById_DeberiaDevolverOk() {
        when(incidenciaService.getIncidenciaByIdDTO(1)).thenReturn(incidenciaDTO);

        ResponseEntity<IncidenciaDTO> response = incidenciaController.getById("1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(incidenciaService, times(1)).getIncidenciaByIdDTO(1);
    }

    @Test
    void getById_DeberiaLanzarBadRequest_CuandoNoEsNumero() {
        assertThrows(BadRequestException.class, () -> incidenciaController.getById("abc"));
    }

    @Test
    void getById_DeberiaLanzarBadRequest_CuandoEsNegativo() {
        assertThrows(BadRequestException.class, () -> incidenciaController.getById("-5"));
    }

    @Test
    void getByUsuario_DeberiaDevolverOk() {
        Page<IncidenciaDTO> page = new PageImpl<>(List.of(incidenciaDTO));
        when(incidenciaService.getIncidenciasByUsuarioDTO(eq("admin"), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<IncidenciaDTO>> response = incidenciaController.getByUsuario("admin", 0, 5, "fecha", "desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(incidenciaService, times(1)).getIncidenciasByUsuarioDTO(eq("admin"), any(Pageable.class));
    }

    @Test
    void getByUsuario_DeberiaLanzarNoContent_CuandoNoHayIncidencias() {
        when(incidenciaService.getIncidenciasByUsuarioDTO(eq("admin"), any(Pageable.class)))
                .thenThrow(new NoContentException("No hay incidencias para este usuario"));

        assertThrows(NoContentException.class, () -> incidenciaController.getByUsuario("admin", 0, 5, "fecha", "desc"));
    }

    @Test
    void getByUsuario_DeberiaLanzarBadRequest_CuandoSortNoEsValido() {
        assertThrows(BadRequestException.class, () -> incidenciaController.getByUsuario("admin", 0, 5, "foo", "desc"));

        verify(incidenciaService, never()).getIncidenciasByUsuarioDTO(any(), any());
    }

    @Test
    void getByClase_DeberiaDevolverOk() {
        Page<IncidenciaDTO> page = new PageImpl<>(List.of(incidenciaDTO));
        when(incidenciaService.getIncidenciasByClaseDTO(eq("ClaseX"), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<IncidenciaDTO>> response = incidenciaController.getByClase("ClaseX", 0, 5, "fecha", "desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(incidenciaService, times(1)).getIncidenciasByClaseDTO(eq("ClaseX"), any(Pageable.class));
    }

    @Test
    void getByClase_DeberiaLanzarNoContent_CuandoNoHayIncidencias() {
        when(incidenciaService.getIncidenciasByClaseDTO(eq("ClaseX"), any(Pageable.class)))
                .thenThrow(new NoContentException("No hay incidencias para esta clase"));

        assertThrows(NoContentException.class, () -> incidenciaController.getByClase("ClaseX", 0, 5, "fecha", "desc"));
    }

    @Test
    void getByMetodo_DeberiaDevolverOk() {
        Page<IncidenciaDTO> page = new PageImpl<>(List.of(incidenciaDTO));
        when(incidenciaService.getIncidenciasByMetodoDTO(eq("MetodoX"), any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<IncidenciaDTO>> response = incidenciaController.getByMetodo("MetodoX", 0, 5, "fecha", "desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(incidenciaService, times(1)).getIncidenciasByMetodoDTO(eq("MetodoX"), any(Pageable.class));
    }

    @Test
    void getByMetodo_DeberiaLanzarNoContent_CuandoNoHayIncidencias() {
        when(incidenciaService.getIncidenciasByMetodoDTO(eq("MetodoX"), any(Pageable.class)))
                .thenThrow(new NoContentException("No hay incidencias para este método"));

        assertThrows(NoContentException.class, () -> incidenciaController.getByMetodo("MetodoX", 0, 5, "fecha", "desc"));
    }

    @Test
    void crearIncidencia_DeberiaDevolverCreated() {
        when(incidenciaService.crearIncidenciaDTO(any(IncidenciaDTO.class))).thenReturn(incidenciaDTO);

        ResponseEntity<IncidenciaDTO> response = incidenciaController.crearIncidencia(new IncidenciaDTO());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}