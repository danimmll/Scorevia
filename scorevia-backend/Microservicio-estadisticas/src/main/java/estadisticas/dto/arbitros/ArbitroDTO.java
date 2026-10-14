package estadisticas.dto.arbitros;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArbitroDTO {
    private Integer idArbitro;
    private String nombre;
    private String pais;
}