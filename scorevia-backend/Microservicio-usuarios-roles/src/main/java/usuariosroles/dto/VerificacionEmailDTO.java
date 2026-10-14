package usuariosroles.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuerpo esperado por {@code POST /auth/verificar-email}: {@code correoUsuario} y
 * {@code codigo} en camelCase, sin necesidad de anotar nada aquí.
 *
 * @author Daniel
 * @version 1.0
 */
@Getter
@Setter
@NoArgsConstructor
public class VerificacionEmailDTO {

    @NotEmpty(message = "El correo no puede estar vacío")
    private String correoUsuario;

    @NotEmpty(message = "El código no puede estar vacío")
    private String codigo;
}