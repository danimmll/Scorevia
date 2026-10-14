package estadisticas.dto.jugadores;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialDorsalDTO {
    private Integer idEquipo;
    private String nombreEquipo;
    private String escudoEquipo;
    private Integer temporada;
    private Integer dorsal;
    private String posicion;
}