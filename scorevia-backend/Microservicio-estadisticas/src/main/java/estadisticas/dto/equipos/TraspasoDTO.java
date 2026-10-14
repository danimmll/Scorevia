package estadisticas.dto.equipos;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraspasoDTO {
    // El cliente no tenía ninguna clave estable con la que identificar un traspaso: las listas
    // se recorrían con la fecha (o nombre+fecha) como clave de seguimiento, y dos movimientos
    // del mismo día -- que los hay -- provocaban claves duplicadas en el @for de Angular.
    private Long idTraspaso;
    private Integer idJugador;
    private String nombreJugador;
    private LocalDate fecha;
    private Integer idEquipoOrigen;
    private String equipoOrigen;
    private String escudoOrigen;
    private Integer idEquipoDestino;
    private String equipoDestino;
    private String escudoDestino;
    private String tipo;
    private String coste;
}