package usuariosroles.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * DTO exclusivo para {@code PUT /usuarios/{id}}, separado de {@link UsuarioDTO} (usado en
 * creación y lectura).
 * <p>
 * A diferencia de {@link UsuarioDTO}, {@code contrasenhaUsuario} NO es obligatoria aquí:
 * antes, al reutilizar el mismo DTO para crear y editar, cualquier actualización de perfil
 * (aunque fuera solo para cambiar el nombre) exigía reenviar una contraseña -- nueva o la
 * misma -- solo para satisfacer la validación, aumentando innecesariamente la exposición de
 * ese dato en la red. El servicio ({@code UsuarioServiceImpl.actualizarDatosSensiblesUsuario})
 * ya trataba un valor nulo como "no cambiar la contraseña"; el DTO compartido era lo único
 * que lo impedía.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioUpdateDTO {

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

    @Schema(description = "Nueva contraseña del usuario (opcional: solo si se quiere cambiar; debe contener al menos 8 caracteres y un carácter especial)", example = "P@ssw0rd!")
    @Pattern(regexp = "^(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#$%^&*(),.?\":{}|<>]{8,}$", message = "La contraseña debe tener al menos 8 caracteres y un carácter especial")
    private String contrasenhaUsuario;

    @Schema(description = "URL de la foto de perfil del usuario", example = "https://ejemplo.com/foto.jpg")
    private String urlFoto;
}
