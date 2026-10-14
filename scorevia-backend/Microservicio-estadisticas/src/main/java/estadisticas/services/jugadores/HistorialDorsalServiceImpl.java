package estadisticas.services.jugadores;

import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.mappers.jugadores.HistorialDorsalMapper;
import estadisticas.repositories.jugadores.HistorialDorsalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HistorialDorsalServiceImpl implements HistorialDorsalService {

    private final HistorialDorsalRepository historialDorsalRepository;
    private final HistorialDorsalMapper historialDorsalMapper;

    @Override
    @Transactional(readOnly = true)
    public List<HistorialDorsalDTO> obtenerHistorialPorJugador(Integer idJugador) {
        return historialDorsalRepository.findByJugador_IdJugadorOrderByTemporadaDesc(idJugador).stream()
                .map(historialDorsalMapper::toDto)
                .toList();
    }
}