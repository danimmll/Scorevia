package estadisticas.dto.ligas;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrofeoDTO {
    private Long idTrofeo;
    private Integer idJugador;     // Puede ser null si es de entrenador
    private Integer idEntrenador;  // Puede ser null si es de jugador
    private String competicion;
    private String pais;
    private String temporada;
    private String resultado;
    /** Bandera del pais del trofeo. "Super Cup" a secas no dice de donde es. */
    private String banderaPais;
}