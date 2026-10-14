package incidencias.controllers.reportes;

import incidencias.dto.EstadoReporte;
import incidencias.dto.ReporteDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reportes y Moderación", description = "Gestión de reportes de usuarios")
public interface ReporteController {

    @Operation(summary = "Crear un reporte sobre un usuario")
    @PostMapping("/crear")
    ResponseEntity<ReporteDTO> crearReporte(@Valid @RequestBody ReporteDTO dto, HttpServletRequest request);

    @Operation(summary = "Obtener reportes pendientes (Solo ADMIN)", description = "Listado paginado de reportes en estado PENDIENTE, ordenados por fecha descendente.")
    @GetMapping("/pendientes")
    ResponseEntity<Page<ReporteDTO>> obtenerPendientes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "Resolver un reporte (Solo ADMIN)")
    @PutMapping("/{idReporte}/resolver")
    ResponseEntity<ReporteDTO> resolverReporte(
            @PathVariable Integer idReporte,
            @RequestParam EstadoReporte estado,
            @RequestParam String resolucion,
            HttpServletRequest request);

    @Operation(summary = "Retirar tu propio reporte", description = "Solo si el reporte todavía está PENDIENTE de revisión.")
    @DeleteMapping("/{idReporte}")
    ResponseEntity<Void> retirarReporte(@PathVariable Integer idReporte, HttpServletRequest request);
}
