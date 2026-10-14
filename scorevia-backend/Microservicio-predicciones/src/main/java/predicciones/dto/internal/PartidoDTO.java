package predicciones.dto.internal;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartidoDTO {

    @NotNull(message = "El ID del partido de la API es obligatorio")
    @Schema(description = "Identificador del partido (coincide con el id de la API externa)", example = "1208021")
    private Integer idPartido;

    @NotBlank(message = "La liga es obligatoria")
    @Schema(description = "Nombre de la liga", example = "La Liga")
    private String liga;

    // --- AÑADIDOS LOS IDs ---
    @Schema(description = "ID del equipo local", example = "541")
    private Integer idEquipoLocal;

    @NotBlank(message = "El equipo local es obligatorio")
    @Schema(description = "Nombre del equipo local", example = "Real Madrid")
    private String equipoLocal;

    @Schema(description = "ID del equipo visitante", example = "529")
    private Integer idEquipoVisitante;

    @NotBlank(message = "El equipo visitante es obligatorio")
    @Schema(description = "Nombre del equipo visitante", example = "FC Barcelona")
    private String equipoVisitante;
    // ------------------------

    @Schema(description = "Nombre del arbitro, si esta disponible", example = "Mateu Lahoz")
    private String arbitro;

    @NotNull(message = "La fecha del partido es obligatoria")
    @Schema(description = "Fecha y hora del partido")
    private LocalDateTime fecha;

    @Schema(description = "Jornada de liga", example = "12")
    private Integer jornada;

    @Schema(description = "Goles del equipo local", example = "2")
    private Integer resultadoLocal;

    @Schema(description = "Goles del equipo visitante", example = "1")
    private Integer resultadoVisitante;

    @NotBlank(message = "El estado del partido es obligatorio")
    @Schema(description = "Estado del partido", example = "FINALIZADO")
    private String estado;

    @Schema(description = "Temporada/edición a la que pertenece el partido (según API-Football)", example = "2026")
    private Integer temporada;
}