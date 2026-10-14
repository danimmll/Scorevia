package usuariosroles.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo esperado por {@code POST /auth/reset-password}: el código de 6 dígitos recibido por
 * correo y la nueva contraseña. Mismo patrón de validación de contraseña que {@link UsuarioDTO}.
 *
 * @author Daniel
 * @version 1.0
 */
@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordDTO {

    @NotEmpty(message = "El correo no puede estar vacío")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "El correo debe tener un formato válido")
    private String correoUsuario;

    @NotEmpty(message = "El código no puede estar vacío")
    private String codigo;

    @NotEmpty(message = "La nueva contraseña no puede estar vacía")
    @Pattern(regexp = "^(?=.*[!@#$%^&*(),.?\":{}|<>])[A-Za-z\\d!@#$%^&*(),.?\":{}|<>]{8,}$", message = "La contraseña debe tener al menos 8 caracteres y un carácter especial")
    private String nuevaContrasenha;
}
