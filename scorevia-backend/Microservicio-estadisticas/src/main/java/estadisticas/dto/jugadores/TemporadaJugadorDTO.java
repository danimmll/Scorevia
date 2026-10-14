package estadisticas.dto.jugadores;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lo que hizo un jugador en una temporada y una competicion concretas.
 *
 * <p>Sale de agregar {@code estadisticas_jugador_partido} (7 millones de filas) por temporada,
 * competicion y equipo. No se guarda en ninguna tabla propia: se calcula al vuelo y se cachea,
 * porque es un resumen de datos que ya estan y mantener una tabla paralela solo anadiria una
 * copia que se puede quedar desfasada.</p>
 *
 * <p>Cuesta <b>cero</b> peticiones a API-Football.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resumen de un jugador en una temporada y competicion")
public class TemporadaJugadorDTO {

    @Schema(description = "Temporada segun API-Football", example = "2024")
    private Integer temporada;

    @Schema(description = "Identificador de la competicion", example = "140")
    private Integer idLiga;

    @Schema(description = "Nombre de la competicion", example = "La Liga")
    private String liga;

    @Schema(description = "Escudo de la competicion")
    private String logoLiga;

    @Schema(description = "Bandera del pais de la competicion")
    private String banderaLiga;

    @Schema(description = "Equipo en el que jugo esos partidos", example = "541")
    private Integer idEquipo;

    @Schema(description = "Nombre del equipo", example = "Real Madrid")
    private String equipo;

    @Schema(description = "Escudo del equipo")
    private String escudoEquipo;

    @Schema(description = "Partidos en los que aparecio", example = "34")
    private Integer partidos;

    @Schema(description = "De cuantos fue titular", example = "31")
    private Integer titularidades;

    @Schema(description = "Minutos jugados", example = "2810")
    private Integer minutos;

    @Schema(description = "Goles", example = "17")
    private Integer goles;

    @Schema(description = "Asistencias", example = "9")
    private Integer asistencias;

    @Schema(description = "Tarjetas amarillas", example = "4")
    private Integer amarillas;

    @Schema(description = "Tarjetas rojas", example = "0")
    private Integer rojas;

    /** Media de las valoraciones de la API. Null en las competiciones que no las publican. */
    @Schema(description = "Valoracion media, si la competicion la publica", example = "7.21")
    private Double valoracionMedia;
}
