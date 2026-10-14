package estadisticas.dto.entrenadores;

import com.fasterxml.jackson.annotation.JsonInclude;
import estadisticas.dto.equipos.EquipoDTO;
import lombok.*;

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

    /**
     * Si ya se ha preguntado por su palmares.
     *
     * <p>Con false, un palmares vacio significa "no lo hemos mirado" y no "no ha ganado
     * nada", que es una diferencia que la ficha no puede permitirse borrar. Solo se pregunta
     * por los entrenadores que no se han preguntado nunca, asi que hay muchos a medias.</p>
     */
    private Boolean palmaresConsultado;
}