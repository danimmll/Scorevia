package estadisticas.dto.partidos;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartidoDTO {

    @NotNull(message = "El ID del partido de la API es obligatorio")
    @Schema(description = "Identificador del partido (coincide con el id de la API externa)", example = "1208021")
    private Integer idPartido;

    @NotBlank(message = "La liga es obligatoria")
    @Schema(description = "Nombre de la liga", example = "La Liga")
    private String liga;

    // El nombre de la liga no basta para ordenar por importancia: el frontend agrupa los
    // partidos del día por competición y necesita saber CUÁL es, no solo cómo se llama.
    // Sin el id, un día normal encabeza la lista lo que más volumen tiene -- el 2026-09-04
    // eran "Paulista - U20" (17 partidos) y el juvenil ruso (8), con la FA Cup enterrada.
    @Schema(description = "Identificador de la liga, para ordenar y agrupar", example = "140")
    private Integer idLiga;

    @Schema(description = "País de la liga, para agrupar", example = "Spain")
    private String paisLiga;

    @Schema(description = "Escudo de la liga", example = "https://media.api-sports.io/football/leagues/140.png")
    private String logoLiga;

    // Hay muchisimas competiciones que comparten nombre entre paises ("Primera Division",
    // "Copa del Rey", "Premier League"...), asi que el escudo por si solo no basta para saber
    // que se esta mirando. La bandera al lado lo resuelve de un vistazo. La tienen 1.232 de
    // las 1.244 ligas, asi que la interfaz tiene que aguantar que falte.
    @Schema(description = "Bandera del pais de la liga",
            example = "https://media.api-sports.io/flags/es.svg")
    private String banderaLiga;

    // --- AÑADIDOS LOS IDs ---
    @Schema(description = "ID del equipo local", example = "541")
    private Integer idEquipoLocal;

    @NotBlank(message = "El equipo local es obligatorio")
    @Schema(description = "Nombre del equipo local", example = "Real Madrid")
    private String equipoLocal;

    @Schema(description = "Escudo del equipo local")
    private String escudoLocal;

    @Schema(description = "ID del equipo visitante", example = "529")
    private Integer idEquipoVisitante;

    @NotBlank(message = "El equipo visitante es obligatorio")
    @Schema(description = "Nombre del equipo visitante", example = "FC Barcelona")
    private String equipoVisitante;

    @Schema(description = "Escudo del equipo visitante")
    private String escudoVisitante;
    // ------------------------

    @Schema(description = "Nombre del arbitro, si esta disponible", example = "Mateu Lahoz")
    private String arbitro;

    @NotNull(message = "La fecha del partido es obligatoria")
    @Schema(description = "Fecha y hora del partido")
    private LocalDateTime fecha;

    @Schema(description = "Jornada de liga", example = "12")
    private Integer jornada;

    /**
     * La fase en crudo, como la manda API-Football. En liga es redundante con {@code jornada}
     * ("Regular Season - 12"), pero en copa es lo único que hay: "Quarter-finals", "Final".
     */
    @Schema(description = "Fase o jornada tal cual la publica la API", example = "Quarter-finals")
    private String ronda;

    @Schema(description = "Goles del equipo local", example = "2")
    private Integer resultadoLocal;

    @Schema(description = "Goles del equipo visitante", example = "1")
    private Integer resultadoVisitante;

    @NotBlank(message = "El estado del partido es obligatorio")
    @Schema(description = "Estado del partido", example = "FINALIZADO")
    private String estado;

    @Schema(description = "Codigo de estado original de API-Football, que distingue lo que el "
            + "estado agrupado pierde: 1H, HT, 2H, ET, BT, P, FT, AET, PEN...", example = "2H")
    private String estadoCorto;

    @Schema(description = "Minuto de juego. Solo viene relleno mientras el partido esta en "
            + "juego; al terminar vuelve a null para no dejar un '90 congelado al lado del "
            + "resultado final", example = "67")
    private Integer minuto;

    @Schema(description = "Temporada/edición a la que pertenece el partido (según API-Football)", example = "2026")
    private Integer temporada;

    // ------------------------------------------------------- marcador desglosado ----
    // Nulos mientras el partido no haya llegado ahí, o en los partidos antiguos que se
    // ingirieron antes de que se guardara este detalle.

    @Schema(description = "Goles del local al descanso", example = "1")
    private Integer resultadoLocalDescanso;

    @Schema(description = "Goles del visitante al descanso", example = "0")
    private Integer resultadoVisitanteDescanso;

    /** Solo en eliminatorias que llegaron a la tanda. Sin esto, un 1-1 con penaltis parece un empate. */
    @Schema(description = "Penaltis anotados por el local en la tanda", example = "4")
    private Integer penaltisLocal;

    @Schema(description = "Penaltis anotados por el visitante en la tanda", example = "3")
    private Integer penaltisVisitante;

    /**
     * Que publica API-Football de esta competicion.
     *
     * <p>Viaja con el partido para que la pantalla pueda decir "esta competicion no publica
     * estadisticas" en vez de dejar un hueco vacio que se lee como un fallo nuestro. Medido
     * sobre las 1.244 ligas: solo <b>133</b> dan estadisticas de equipo y <b>116</b> de
     * jugador; eventos, 1.031; alineaciones, 492.</p>
     *
     * <p>Null significa "no se sabe" (la liga no entro en la ultima sincronizacion), y ahi
     * no se afirma nada.</p>
     */
    private Boolean coberturaStatsPartido;
    private Boolean coberturaStatsJugadores;
    private Boolean coberturaEventos;
    private Boolean coberturaAlineaciones;
}
