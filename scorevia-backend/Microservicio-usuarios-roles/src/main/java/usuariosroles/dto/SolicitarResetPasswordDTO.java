package usuariosroles.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo esperado por {@code POST /auth/solicitar-reset-password}: solo el correo del que se
 * quiere recuperar el acceso.
 *
 * @author Daniel
 * @version 1.0
 */
@Getter
@Setter
@NoArgsConstructor
public class SolicitarResetPasswordDTO {

    @NotEmpty(message = "El correo no puede estar vacío")
    @Email(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "El correo debe tener un formato válido")
    private String correoUsuario;
}
