package predicciones.dto.internal;

import lombok.Data;

@Data
public class EventoPartidoDTO {
    private Integer tiempoMinuto;
    private Integer tiempoExtra;
    private String tipoEvento;
    private String detalle;
    private EquipoDTO equipo;
}