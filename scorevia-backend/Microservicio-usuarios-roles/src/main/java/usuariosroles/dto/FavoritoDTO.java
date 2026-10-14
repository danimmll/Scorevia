package usuariosroles.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "DTO para gestionar los favoritos del usuario")
public class FavoritoDTO {

    @NotNull(message = "El ID del usuario es obligatorio")
    private Integer idUsuario;

    @NotNull(message = "El ID de referencia de la API es obligatorio")
    @Schema(description = "ID del equipo, partido o liga en la API-Football")
    private Integer idReferenciaApi;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaAgregado;
}