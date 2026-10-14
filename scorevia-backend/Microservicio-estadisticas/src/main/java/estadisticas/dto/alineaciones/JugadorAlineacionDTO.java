package estadisticas.dto.alineaciones;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

/**
 * Un jugador dentro de la alineación de un partido concreto.
 *
 * <p>Lleva lo justo para pintarlo sobre el campo y poder saltar a su ficha: quién es, su
 * foto, su dorsal, en qué línea jugó y lo que hizo en el partido.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JugadorAlineacionDTO {

    @Schema(description = "Id del jugador, para enlazar con su ficha", example = "154")
    private Integer idJugador;

    @Schema(description = "Nombre del jugador", example = "L. Messi")
    private String nombre;

    @Schema(description = "Foto del jugador")
    private String foto;

    @Schema(description = "Dorsal", example = "10")
    private Integer dorsal;

    /**
     * Línea en la que jugó ESTE partido, con el código de API-Football: G portero, D defensa,
     * M centrocampista, F delantero. No es lo mismo que la posición natural de su ficha —
     * un lateral escorado a la banda sale como M en un partido y como D en otro.
     */
    @Schema(description = "Línea en este partido: G, D, M o F", example = "F")
    private String linea;

    /**
     * Sitio exacto en el campo dentro de la alineación oficial, "fila:columna": la fila 1 es la
     * portería y la columna va de izquierda a derecha. Null cuando la competición no publica
     * alineaciones, y entonces el cliente reparte por línea como hasta ahora.
     */
    @Schema(description = "Sitio en el campo como fila:columna", example = "2:4")
    private String grid;

    @Schema(description = "Posición natural según su ficha", example = "Attacker")
    private String posicionNatural;

    @Schema(description = "Minutos jugados", example = "90")
    private Integer minutosJugados;

    @Schema(description = "Goles marcados en el partido", example = "2")
    private Integer goles;

    @Schema(description = "Asistencias en el partido", example = "1")
    private Integer asistencias;

    @Schema(description = "Tarjetas amarillas en el partido", example = "1")
    private Integer tarjetasAmarillas;

    @Schema(description = "Tarjetas rojas en el partido", example = "0")
    private Integer tarjetasRojas;

    @Schema(description = "Valoración del partido según la API (0-10)", example = "8.4")
    private BigDecimal valoracion;
}
