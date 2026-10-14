package estadisticas.controllers.clasificacion;

import estadisticas.dto.ligas.ClasificacionDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Clasificaciones", description = "Endpoints para generar tablas de ligas")
public interface ClasificacionController {

    @Operation(summary = "Tabla de clasificación", description = "Devuelve la tabla completa ordenada por puntos para una liga y temporada (ej: 2026)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Clasificación obtenida correctamente")
    })
    ResponseEntity<List<ClasificacionDTO>> obtenerTabla(
            @PathVariable("idLiga") String idLigaString,
            @PathVariable("temporada") String temporadaString);
}