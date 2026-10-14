package predicciones.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import predicciones.dto.internal.EstadisticaJugadorPartidoDTO;
import predicciones.dto.internal.JugadorDTO;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContextoJugadorPrediccionDTO {
    private JugadorDTO jugador;
    private List<EstadisticaJugadorPartidoDTO> historialStatsTemporada; // Todas sus faltas, tarjetas y goles partido a partido
}