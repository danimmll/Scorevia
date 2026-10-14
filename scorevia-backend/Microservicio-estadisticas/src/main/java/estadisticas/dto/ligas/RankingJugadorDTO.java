package estadisticas.dto.ligas;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Una fila del ranking de una competición: el jugador, su equipo y el número que lo ordena.
 *
 * <p>Sirve igual para goleadores, asistentes o tarjetas: lo que cambia es qué mide
 * {@code valor}, y eso lo dice el tipo que se pidió.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RankingJugadorDTO {

    @Schema(description = "Puesto en el ranking, empezando en 1", example = "1")
    private int puesto;

    @Schema(description = "Id del jugador, para enlazar con su ficha", example = "154")
    private Integer idJugador;

    @Schema(description = "Nombre del jugador", example = "R. Lewandowski")
    private String nombre;

    @Schema(description = "Foto del jugador")
    private String foto;

    @Schema(description = "Id del equipo", example = "529")
    private Integer idEquipo;

    @Schema(description = "Nombre del equipo", example = "Barcelona")
    private String nombreEquipo;

    @Schema(description = "Escudo del equipo")
    private String escudoEquipo;

    @Schema(description = "Lo que se está midiendo: goles, asistencias o tarjetas", example = "23")
    private Integer valor;

    @Schema(description = "Partidos jugados en la competición", example = "31")
    private Integer partidos;

    @Schema(description = "Minutos jugados", example = "2650")
    private Integer minutos;
}
