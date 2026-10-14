package predicciones.controllers.prediction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import predicciones.dto.LivePredictionDTO;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "Predicciones", description = "Motor de predicción Dixon-Coles pre-partido y en vivo")
public interface PredictionController {

    @Operation(summary = "Predicción pre-partido", description = "Porcentajes Local/Empate/Visitante calculados con el modelo Dixon-Coles")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Predicción calculada correctamente"),
            @ApiResponse(responseCode = "403", description = "Requiere rol Premium"),
            @ApiResponse(responseCode = "404", description = "Partido no encontrado")
    })
    ResponseEntity<Map<String, BigDecimal>> predecirPartido(
            @PathVariable("idPartido") String idPartidoString,
            HttpServletRequest request);

    @Operation(summary = "Predicción en vivo", description = "Recalcula las probabilidades restantes aplicando decaimiento temporal sobre el marcador real")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Predicción en vivo calculada correctamente"),
            @ApiResponse(responseCode = "403", description = "Requiere rol Premium"),
            @ApiResponse(responseCode = "404", description = "Partido no encontrado")
    })
    ResponseEntity<LivePredictionDTO> predecirPartidoEnVivo(
            @PathVariable("idPartido") String idPartidoString,
            @Parameter(description = "Minuto actual del partido") @RequestParam("minuto") String minutoString,
            HttpServletRequest request);
}