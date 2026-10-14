package predicciones.dto.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClasificacionDTO {
    private Integer posicion;
    private Integer puntos;
    private Integer partidosJugados;
    private Integer partidosGanados;
    private Integer partidosEmpatados;
    private Integer partidosPerdidos;
    private Integer golesFavor;
    private Integer golesContra;
    private Integer diferenciaGoles;
    private String estadoCompeticion;
    private EquipoDTO equipo;
    private String logoEquipo;
}