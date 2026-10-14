package estadisticas.dto.incidencias;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidenciaDTO {

    @Schema(description = "Identificador único de la incidencia", example = "101")
    private Integer idIncidencia;

    @Schema(description = "Endpoint donde ocurrió la incidencia", example = "/usuarios/5")
    private String endpoint;

    @Schema(description = "Tipo de incidencia o excepción", example = "NullPointerException")
    private String tipo;

    @Schema(description = "Clase donde ocurrió la incidencia", example = "UsuarioServiceImpl")
    private String clase;

    @Schema(description = "Método donde ocurrió la incidencia", example = "getUsuarioById")
    private String metodo;

    @Schema(description = "Traza completa del error o incidencia", example = "java.lang.NullPointerException: Cannot invoke ...")
    private String traza;

    @Schema(description = "Fecha y hora en que ocurrió la incidencia", example = "2024-05-15 14:35:22")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fecha;

    @Schema(description = "Contexto o correo del usuario relacionado con la incidencia", example = "admin@futbolstats.com")
    private String usuarioContexto;
}
