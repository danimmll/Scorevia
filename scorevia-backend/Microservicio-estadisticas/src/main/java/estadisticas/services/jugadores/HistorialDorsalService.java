package estadisticas.services.jugadores;

import estadisticas.dto.jugadores.HistorialDorsalDTO;
import java.util.List;

public interface HistorialDorsalService {
    List<HistorialDorsalDTO> obtenerHistorialPorJugador(Integer idJugador);
}