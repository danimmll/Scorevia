package estadisticas.controllers.jugadores;

import estadisticas.dto.jugadores.HistorialDorsalDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Tag(name = "Historial de Dorsales", description = "Trayectoria de un jugador por equipos, temporadas y dorsal")
public interface HistorialDorsalController {

    @Operation(summary = "Obtener el historial de dorsales de un jugador")
    ResponseEntity<List<HistorialDorsalDTO>> obtenerHistorial(@PathVariable("idJugador") String idJugadorString);
}