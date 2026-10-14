package estadisticas.dto.jugadores;

import com.fasterxml.jackson.annotation.JsonInclude;
import estadisticas.dto.equipos.EquipoDTO;
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
@Schema(description = "Datos transferibles de un Jugador")
public class JugadorDTO {

    @NotNull(message = "El ID del jugador es obligatorio")
    @Schema(description = "ID único del jugador", example = "274")
    private Integer idJugador;

    @NotBlank(message = "El nombre es obligatorio")
    @Schema(description = "Nombre conocido del jugador", example = "L. Messi")
    private String nombre;

    @Schema(description = "Nombre completo", example = "Lionel Andrés Messi")
    private String nombreCompleto;

    @Schema(description = "Edad del jugador", example = "36")
    private Integer edad;

    @Schema(description = "Fecha de nacimiento", example = "1987-06-24")
    private String fechaNacimiento;

    @Schema(description = "Nacionalidad principal", example = "Argentina")
    private String nacionalidad;

    @Schema(description = "Altura en cm o formato texto", example = "170 cm")
    private String altura;

    @Schema(description = "Peso en kg o formato texto", example = "72 kg")
    private String peso;

    @Schema(description = "URL de la fotografía del jugador")
    private String foto;

    @Schema(description = "Lugar de nacimiento del jugador", example = "Rosario")
    private String lugarNacimiento;

    @Schema(description = "Posición habitual en el campo", example = "Delantero")
    private String posicion;

    @Schema(description = "Dorsal actual", example = "10")
    private Integer dorsal;

    @Schema(description = "Equipo actual del jugador")
    private EquipoDTO equipo;

    // ------------------------------------------------- qué sabemos y qué no sabemos ----
    // Una sección vacía puede significar dos cosas muy distintas: "no tiene" o "no lo hemos
    // mirado". Sin distinguirlas, la ficha afirma cosas que no sabe.

    @Schema(description = "Si la ficha se ha contrastado con el catálogo de la API. "
            + "Con false pueden faltar altura, peso o pie bueno.")
    private Boolean perfilVerificado;

    @Schema(description = "Si ya se ha preguntado por sus lesiones y sanciones. "
            + "Con false, la lista vacía es 'no lo hemos mirado', no 'no ha tenido ninguna'.")
    private Boolean bajasConsultadas;
}