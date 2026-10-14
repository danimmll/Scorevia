package usuariosroles.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioRolDTO {

    @Schema(description = "Identificador único del usuario", example = "12")
    private Integer idUsuario;
    
    @Schema(description = "Identificador único del rol", example = "5")
    private Integer idRol;

    @Schema(description = "Nombre completo del usuario", example = "Juan Pérez")
    private String nombreUsuario;
    
    @Schema(description = "Indica si el usuario está activo", example = "true")
    private Boolean activoUsuario;
    
    @Schema(description = "Nombre del rol", example = "ADMIN")
    private String nombreRol;
    
    @Schema(description = "Indica si el rol está activo", example = "true")
    private Boolean activoRol;
}
