package estadisticas.controllers.jugadores;

import estadisticas.config.ValidationUtils;
import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.services.jugadores.HistorialDorsalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jugadores")
@RequiredArgsConstructor
public class HistorialDorsalControllerImpl implements HistorialDorsalController {

    private final HistorialDorsalService historialDorsalService;

    @Override
    @GetMapping("/{idJugador}/historial-dorsales")
    public ResponseEntity<List<HistorialDorsalDTO>> obtenerHistorial(@PathVariable("idJugador") String idJugadorString) {
        Integer idJugador = ValidationUtils.validarId(idJugadorString);
        return ResponseEntity.ok(historialDorsalService.obtenerHistorialPorJugador(idJugador));
    }
}