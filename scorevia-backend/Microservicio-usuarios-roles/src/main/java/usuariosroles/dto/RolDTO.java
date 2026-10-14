package usuariosroles.dto;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import usuariosroles.entities.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolDTO {

    @Schema(description = "Identificador único del rol", example = "5")
    private Integer idRol;

    @Schema(description = "Nombre del rol", example = "ADMIN")
    @NotEmpty(message = "El nombre del rol no puede estar vacío")
    private String nombreRol;

    @Schema(description = "Indica si el rol está activo", example = "true")
    private Boolean activo;

    @JsonIgnore
    @Builder.Default
    @Schema(description = "Conjunto de usuarios asociados a este rol (ignorado en JSON)", example = "[]")
    private Set<Usuario> usuarios = new HashSet<>();
}
