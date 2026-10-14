package estadisticas.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Estado del partido segun API-Football (codigo corto: NS, 1H, HT, FT...).
 * "short" es palabra reservada de estilo pero valida como nombre de propiedad
 * JSON, por eso el mapeo explicito con {@link JsonProperty}.
 *
 * @author Daniel
 * @version 1.1
 */
@Data
public class FixtureStatusDTO {

    @JsonProperty("short")
    private String shortStatus;

    /**
     * Minuto de juego. Lo manda la API en todas las respuestas de {@code /fixtures}, incluida
     * la del directo, asi que el marcador en vivo puede ensenar "45'" sin pedir nada extra.
     *
     * <p>Viene a null en los partidos que no han empezado y en los ya terminados. En el
     * descanso se queda clavado en 45 con {@code short = "HT"}, que es la senal de que hay
     * que pintar "Descanso" y no "45'".</p>
     */
    private Integer elapsed;
}
