package estadisticas.dto.internal;

import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.dto.partidos.EstadisticaJugadorPartidoDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextoJugadorPrediccionDTO {
    private JugadorDTO jugador;
    private List<EstadisticaJugadorPartidoDTO> historialStatsTemporada;
}