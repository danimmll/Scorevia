package estadisticas.dto.equipos;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadioDTO {
    private Integer idEstadio;
    private String nombre;
    private String ciudad;
    private Integer capacidad;
    private String superficie;
    private String fotoUrl;
}