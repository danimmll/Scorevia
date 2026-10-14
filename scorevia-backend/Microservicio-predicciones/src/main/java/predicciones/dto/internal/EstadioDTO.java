package predicciones.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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