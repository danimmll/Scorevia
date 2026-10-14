package estadisticas.dto.ligas;

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
@Schema(description = "Datos transferibles de una Liga o Competición")
public class LigaDTO {

    @NotNull(message = "El ID de la liga es obligatorio")
    @Schema(description = "ID único de la liga en la API externa", example = "140")
    private Integer idLiga;

    @NotBlank(message = "El nombre de la liga es obligatorio")
    @Schema(description = "Nombre de la liga", example = "La Liga")
    private String nombre;

    @Schema(description = "País donde se juega la liga", example = "Spain")
    private String pais;

    @Schema(description = "URL del logo de la liga")
    private String logo;

    @Schema(description = "URL de la bandera del país")
    private String bandera;

    @Schema(description = "Temporada actual", example = "2023")
    private Integer temporadaActual;

    @Schema(description = "Si la competición publica clasificación. Null = todavía no se sabe.")
    private Boolean coberturaClasificacion;
}