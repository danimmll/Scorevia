package estadisticas.dto.entrenadores;

import lombok.*;

/**
 * Una fila del historial de banquillos: sirve para las dos direcciones --
 * "clubes que ha entrenado este técnico" (se usan los campos de equipo) y
 * "quién ha entrenado a este equipo" (se usan los campos de entrenador).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEntrenadorDTO {
    private Integer idEntrenador;
    private String nombreEntrenador;
    private String fotoEntrenador;
    private Integer idEquipo;
    private String nombreEquipo;
    private String escudoEquipo;
    private Integer temporada;
}
