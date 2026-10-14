package estadisticas.dto.ligas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una temporada disponible de una liga.
 *
 * <p>{@code conDatos} distingue una temporada jugable de un esqueleto: la API publica la
 * clasificación de la temporada que viene en cuanto se sortea el calendario, con todos los
 * equipos a cero partidos. Abrir la web en una de esas es enseñar una tabla vacía, así que el
 * cliente usa esta bandera para elegir por defecto la última temporada con partidos jugados.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporadaLigaDTO {
    private Integer temporada;
    private boolean conDatos;
}
