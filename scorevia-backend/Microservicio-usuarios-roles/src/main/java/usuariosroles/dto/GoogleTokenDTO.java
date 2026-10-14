package usuariosroles.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenDTO(
        @NotBlank(message = "El token de Google es obligatorio")
        String token
) {}