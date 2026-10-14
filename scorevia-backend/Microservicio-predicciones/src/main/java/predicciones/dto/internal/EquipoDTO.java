package predicciones.dto.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Datos transferibles de un Equipo de fútbol")
public class EquipoDTO {

    @NotNull(message = "El ID del equipo es obligatorio")
    @Schema(description = "ID único del equipo en la API externa", example = "541")
    private Integer idEquipo;

    @NotBlank(message = "El nombre del equipo es obligatorio")
    @Schema(description = "Nombre oficial del equipo", example = "Real Madrid")
    private String nombre;

    @Schema(description = "Código de tres letras del equipo", example = "RMA")
    private String codigo;

    @Schema(description = "País del equipo", example = "Spain")
    private String pais;

    @Schema(description = "Año de fundación", example = "1902")
    private Integer fundacion;

    @Schema(description = "URL del escudo del equipo")
    private String logo;

    @Schema(description = "Estadio del equipo", example = "Santiago Bernabéu")
    private EstadioDTO estadio;
}