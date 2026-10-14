package estadisticas.dto.alineaciones;

import com.fasterxml.jackson.annotation.JsonInclude;
import estadisticas.dto.entrenadores.EntrenadorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/** La alineación de uno de los dos equipos en un partido. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipoAlineacionDTO {

    @Schema(description = "Id del equipo", example = "541")
    private Integer idEquipo;

    @Schema(description = "Nombre del equipo", example = "Real Madrid")
    private String nombre;

    @Schema(description = "Escudo del equipo")
    private String escudo;

    /**
     * Dibujo táctico ("4-3-3"), contando los titulares por línea. Null si de este partido no
     * hay titulares registrados (partido sin jugar, o sin estadísticas de jugadores todavía).
     */
    @Schema(description = "Formación en formato 4-3-3, deducida de los titulares", example = "4-3-3")
    private String formacion;

    /**
     * Siempre {@code true} hoy: la formación se calcula contando cuántos titulares jugaron en
     * cada línea, no viene dada por API-Football (que sí la publica en /fixtures/lineups, un
     * endpoint que este proyecto no consume para no gastar una petición por partido).
     *
     * <p>El campo existe para que el cliente pueda matizarlo en pantalla y para no tener que
     * cambiar el contrato el día que sí se ingiera la formación oficial.</p>
     */
    @Schema(description = "true si la formación está deducida y no es la oficial de la API", example = "true")
    private Boolean formacionDeducida;

    @Schema(description = "Entrenador del equipo en este partido")
    private EntrenadorDTO entrenador;

    /**
     * Cierto cuando el entrenador es simplemente el que dirige al equipo HOY, porque no consta
     * quién lo dirigía en la temporada del partido. Importa en partidos antiguos: sin este
     * aviso, la ficha afirmaría que un entrenador dirigió un partido de hace diez años.
     */
    @Schema(description = "true si el entrenador mostrado es el actual del club, no el de esa temporada")
    private Boolean entrenadorEsElActual;

    @Schema(description = "Once titular, ordenado de portero a delanteros")
    private List<JugadorAlineacionDTO> titulares;

    @Schema(description = "Suplentes que llegaron a jugar y los que se quedaron en el banquillo")
    private List<JugadorAlineacionDTO> suplentes;
}
