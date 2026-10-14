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
@Schema(description = "Estadísticas detalladas de un jugador en un partido específico")
public class EstadisticaJugadorPartidoDTO {

    @Schema(description = "Identificador de la estadística", example = "1024")
    private Integer id;

    @NotNull(message = "El ID del equipo es obligatorio")
    @Schema(description = "Identificador del equipo al que pertenece en este partido", example = "541")
    private Integer idEquipo;

    @NotBlank(message = "El nombre del jugador no puede estar vacío")
    @Schema(description = "Nombre completo o dorsal del jugador", example = "Lamine Yamal")
    private String nombreJugador;

    @NotBlank(message = "La posición táctica es obligatoria")
    @Schema(description = "Posición táctica (G, D, M, F)", example = "F")
    private String posicion;

    @NotNull(message = "Se debe indicar si fue titular o no")
    @Schema(description = "Indica si el jugador saltó al campo como titular", example = "true")
    private Boolean titular;

    @Schema(description = "Cantidad de minutos jugados", example = "85")
    private Integer minutosJugados;

    @Schema(description = "Goles anotados por el jugador", example = "1")
    private Integer goles;

    @Schema(description = "Asistencias dadas por el jugador", example = "2")
    private Integer asistencias;

    @Schema(description = "Tarjetas amarillas recibidas", example = "0")
    private Integer tarjetasAmarillas;

    @Schema(description = "Tarjetas rojas recibidas", example = "0")
    private Integer tarjetasRojas;

    @Schema(description = "Nota de rendimiento del jugador basada en la API", example = "8.5")
    private String puntuacion;

    @Schema(description = "Posicion que ocupa el jugador en el campo", example = "Delantero centro")
    private String posicionCampo;
}