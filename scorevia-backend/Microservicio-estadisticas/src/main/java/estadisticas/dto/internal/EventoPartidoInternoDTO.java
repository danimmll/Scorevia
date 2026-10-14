package estadisticas.dto.internal;

import lombok.Builder;
import lombok.Data;

/**
 * Forma "interna" de un evento de partido para el motor de predicciones (distinta de
 * {@code estadisticas.dto.partidos.EventoPartidoDTO}, que es la que consume el frontend
 * y va orientada a mostrar nombres, no ids). El motor de predicciones necesita el id del
 * equipo para saber a quién penaliza una tarjeta roja, no su nombre.
 */
@Data
@Builder
public class EventoPartidoInternoDTO {

    private Integer tiempoMinuto;
    private Integer tiempoExtra;
    private String tipoEvento;
    private String detalle;
    private EquipoInternoDTO equipo;

    @Data
    @Builder
    public static class EquipoInternoDTO {
        private Integer idEquipo;
    }
}
