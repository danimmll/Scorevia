package estadisticas.dto.sync;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Cómo va la ingesta automática ahora mismo.
 *
 * <p>Existe para poder responder de un vistazo a las dos preguntas que uno se hace cuando ya
 * no lanza los scripts a mano: ¿se está quedando algo atascado, y cuánta cuota queda?</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstadoIngestaDTO {

    @Schema(description = "Partidos recién terminados esperando sus estadísticas", example = "12")
    private int partidosEnCola;

    /**
     * Null mientras no se haya hecho ninguna petición desde que arrancó el servicio: el dato
     * sale de la cabecera que devuelve API-Football, no de una cuenta propia.
     */
    @Schema(description = "Peticiones que quedan hoy según la última respuesta de API-Football", example = "142310")
    private Integer peticionesRestantes;

    /**
     * Lo mismo pero preguntado a la propia API en este momento, no deducido de la última
     * cabecera vista. La llamada a {@code /status} <b>no cuenta para la cuota diaria</b>, así
     * que es gratis saberlo de verdad. Null si esa consulta falla.
     */
    @Schema(description = "Peticiones gastadas hoy según /status (consulta gratuita)", example = "7690")
    private Integer peticionesGastadasHoy;

    @Schema(description = "Tope diario del plan contratado", example = "150000")
    private Integer limiteDiario;

    @Schema(description = "Plan de la cuenta", example = "Mega")
    private String plan;
}
