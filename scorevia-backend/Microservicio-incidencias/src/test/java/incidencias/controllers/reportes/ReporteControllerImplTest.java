package incidencias.controllers.reportes;

import incidencias.dto.EstadoReporte;
import incidencias.dto.ReporteDTO;
import incidencias.exception.BadRequestException;
import incidencias.services.reportes.ReporteService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReporteControllerImplTest {

    @Mock
    private ReporteService reporteService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ReporteControllerImpl reporteController;

    private ReporteDTO reporteDTO;

    @BeforeEach
    void setUp() {
        reporteDTO = new ReporteDTO();
        reporteDTO.setIdReporte(1);
    }

    @Test
    void crearReporte_DeberiaDevolverCreatedEInyectarUsuario() {
        when(request.getAttribute("idUsuario")).thenReturn(99);
        when(reporteService.crearReporte(any(ReporteDTO.class))).thenReturn(reporteDTO);

        ResponseEntity<ReporteDTO> response = reporteController.crearReporte(reporteDTO, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(99, reporteDTO.getIdDenunciante());
    }

    @Test
    void obtenerPendientes_DeberiaDevolverOk() {
        Page<ReporteDTO> page = new PageImpl<>(List.of(reporteDTO));
        when(reporteService.obtenerReportesPendientes(any(Pageable.class))).thenReturn(page);

        ResponseEntity<Page<ReporteDTO>> response = reporteController.obtenerPendientes(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody(), "El cuerpo de la respuesta no debería ser nulo");
        assertFalse(response.getBody().isEmpty(), "La lista de reportes no debería estar vacía");
    }

    @Test
    void resolverReporte_DeberiaDevolverOk() {
        when(request.getAttribute("idUsuario")).thenReturn(7);
        when(reporteService.resolverReporte(1, EstadoReporte.RESUELTO, "Castigado", 7)).thenReturn(reporteDTO);

        ResponseEntity<ReporteDTO> response = reporteController.resolverReporte(1, EstadoReporte.RESUELTO, "Castigado", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void crearReporte_DeberiaLanzarBadRequest_CuandoSeAutoReporta() {
        when(request.getAttribute("idUsuario")).thenReturn(99);
        reporteDTO.setIdDenunciado(99);

        assertThrows(BadRequestException.class, () -> reporteController.crearReporte(reporteDTO, request));

        verify(reporteService, never()).crearReporte(any(ReporteDTO.class));
    }

    @Test
    void crearReporte_DeberiaPermitir_CuandoDenuncianteYDenunciadoDifieren() {
        when(request.getAttribute("idUsuario")).thenReturn(99);
        reporteDTO.setIdDenunciado(100);
        when(reporteService.crearReporte(any(ReporteDTO.class))).thenReturn(reporteDTO);

        ResponseEntity<ReporteDTO> response = reporteController.crearReporte(reporteDTO, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void crearReporte_ClaimIdUsuarioAusente_LanzaBadRequest() {
        // Si el JWT no llevara el claim idUsuario (no debería pasar nunca en producción,
        // JwtValidationFilter siempre lo fija en una autenticación válida), el request
        // attribute es null: se rechaza explícitamente en vez de dejar pasar un reporte
        // con idDenunciante nulo (dato sin auditoría, imposible de rastrear).
        when(request.getAttribute("idUsuario")).thenReturn(null);
        reporteDTO.setIdDenunciado(100);

        assertThrows(BadRequestException.class, () -> reporteController.crearReporte(reporteDTO, request));

        verify(reporteService, never()).crearReporte(any(ReporteDTO.class));
    }

    @Test
    void retirarReporte_DeberiaDevolverNoContent() {
        when(request.getAttribute("idUsuario")).thenReturn(5);

        ResponseEntity<Void> response = reporteController.retirarReporte(1, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(reporteService, times(1)).retirarReporte(1, 5);
    }

    @Test
    void retirarReporte_ClaimIdUsuarioAusente_LanzaBadRequest() {
        when(request.getAttribute("idUsuario")).thenReturn(null);

        assertThrows(BadRequestException.class, () -> reporteController.retirarReporte(1, request));

        verify(reporteService, never()).retirarReporte(any(), any());
    }
}