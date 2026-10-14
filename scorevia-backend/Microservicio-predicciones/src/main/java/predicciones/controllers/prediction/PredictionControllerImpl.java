package predicciones.controllers.prediction;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import predicciones.config.ValidationUtils;
import predicciones.dto.LivePredictionDTO;
import predicciones.services.prediction.PredictionService;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/predicciones")
@RequiredArgsConstructor
public class PredictionControllerImpl implements PredictionController {

    private final PredictionService predictionService;

    @Override
    @GetMapping("/{idPartido}")
    public ResponseEntity<Map<String, BigDecimal>> predecirPartido(@PathVariable("idPartido") String idPartidoString,
                                                                    HttpServletRequest request) {
        Integer idPartido = ValidationUtils.validarId(idPartidoString);
        Integer idUsuario = (Integer) request.getAttribute("idUsuario");
        return ResponseEntity.ok(predictionService.predecirPartido(idPartido, idUsuario));
    }

    @Override
    @GetMapping("/{idPartido}/en-vivo")
    public ResponseEntity<LivePredictionDTO> predecirPartidoEnVivo(
            @PathVariable("idPartido") String idPartidoString,
            @RequestParam("minuto") String minutoString,
            HttpServletRequest request) {

        Integer idPartido = ValidationUtils.validarId(idPartidoString);
        int minuto = ValidationUtils.validarEntero(minutoString, "minuto");
        Integer idUsuario = (Integer) request.getAttribute("idUsuario");

        return ResponseEntity.ok(predictionService.predecirPartidoEnVivo(idPartido, minuto, idUsuario));
    }
}