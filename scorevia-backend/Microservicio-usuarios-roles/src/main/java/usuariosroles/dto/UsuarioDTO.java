package usuariosroles.dto;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import usuariosroles.entities.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioDTO {

    @Schema(description = "Identificador único del usuario", example = "12")
    private Integer idUsuario;

    @Schema(description = "Nickname del usuario", example = "ElBicho7")
    @Pattern(regexp = "^(?!(?i)(admin|moderador|soporte|system|root)).{3,20}$",
            message = "El nickname debe tener entre 3 y 20 caracteres y no puede usar palabras reservadas")
    @NotEmpty(message = "El nickname no puede estar vacío")
    private String nickname;

    @Schema(description = "Nombre del usuario", example = "Juan")
    @NotEmpty(message = "El nombre del usuario no puede estar vacío")
    private String nombreUsuario;

    @Schema(description = "Apellido del usuario", example = "Pérez")
    @NotEmpty(message = "El apellido del usuario no puede estar vacío")
    private String apellidoUsuario;

    @Schema(description = "Correo electrónico del usuario", example = "juan.perez@example.com")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "El correo debe tener un formato válido")
    @NotEmpty(message = "El correo del usuario no puede estar vacío")
    private String correoUsuario;

    @Schema(description = "Contraseña del usuario (debe contener al menos 8 caracteres y un carácter especial)", example = "P@ssw0rd!")
    @Pattern(regexp = "^(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#$%^&*(),.?\":{}|<>]{8,}$", message = "La contraseña debe tener al menos 8 caracteres y un carácter especial")
    @NotEmpty(message = "La contraseña no puede estar vacía")
    private String contrasenhaUsuario;

    @Schema(description = "Indica si el usuario está activo", example = "true")
    private Boolean activo;

    @Schema(description = "URL de la foto de perfil del usuario", example = "https://ejemplo.com/foto.jpg")
    private String urlFoto;

    @JsonIgnore
    @Builder.Default
    @Schema(description = "Conjunto de roles asociados a este usuario (ignorado en JSON)", example = "[]")
    private Set<Rol> roles = new HashSet<>();

    private Boolean esPremium;
}