package estadisticas.dto.partidos;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Evento ocurrido durante un partido (Gol, Tarjeta, Cambio)")
public class EventoPartidoDTO {

    private Long idEvento;
    private Integer tiempoMinuto;
    private Integer tiempoExtra;
    private String tipoEvento;
    private String detalle;
    private String nombreEquipo;
    private String logoEquipo;
    private String nombreJugadorPrincipal;
    private String nombreJugadorSecundario;
}