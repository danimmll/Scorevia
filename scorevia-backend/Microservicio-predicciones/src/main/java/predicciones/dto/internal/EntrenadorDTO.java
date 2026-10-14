package predicciones.dto.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntrenadorDTO {
    private Integer idEntrenador;
    private String nombre;
    private Integer edad;
    private String nacionalidad;
    private String foto;
    private EquipoDTO equipo;
}