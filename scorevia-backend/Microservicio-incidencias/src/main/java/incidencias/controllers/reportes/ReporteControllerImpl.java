package incidencias.controllers.reportes;

import incidencias.dto.EstadoReporte;
import incidencias.dto.ReporteDTO;
import incidencias.exception.BadRequestException;
import incidencias.services.reportes.ReporteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
public class ReporteControllerImpl implements ReporteController {

    private final ReporteService reporteService;

    private static final int TAMANIO_PAGINA_MAXIMO = 100;
    private static final String ATRIBUTO_ID_USUARIO = "idUsuario";

    @Override
    public ResponseEntity<ReporteDTO> crearReporte(ReporteDTO dto, HttpServletRequest request) {
        Integer idDenunciante = (Integer) request.getAttribute(ATRIBUTO_ID_USUARIO);
        if (idDenunciante == null) {
            throw new BadRequestException("No se pudo identificar al usuario que reporta.");
        }
        dto.setIdDenunciante(idDenunciante);

        if (idDenunciante.equals(dto.getIdDenunciado())) {
            throw new BadRequestException("No puedes reportarte a ti mismo.");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(reporteService.crearReporte(dto));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReporteDTO>> obtenerPendientes(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, TAMANIO_PAGINA_MAXIMO));
        Page<ReporteDTO> pendientes = reporteService.obtenerReportesPendientes(pageable);
        return ResponseEntity.ok(pendientes);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReporteDTO> resolverReporte(Integer idReporte, EstadoReporte estado, String resolucion, HttpServletRequest request) {
        Integer idAdmin = (Integer) request.getAttribute(ATRIBUTO_ID_USUARIO);
        return ResponseEntity.ok(reporteService.resolverReporte(idReporte, estado, resolucion, idAdmin));
    }

    @Override
    public ResponseEntity<Void> retirarReporte(Integer idReporte, HttpServletRequest request) {
        Integer idUsuario = (Integer) request.getAttribute(ATRIBUTO_ID_USUARIO);
        if (idUsuario == null) {
            throw new BadRequestException("No se pudo identificar al usuario.");
        }
        reporteService.retirarReporte(idReporte, idUsuario);
        return ResponseEntity.noContent().build();
    }
}
