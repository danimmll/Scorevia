package estadisticas.controllers.ligas;

import estadisticas.dto.ligas.LigaDTO;
import estadisticas.dto.ligas.TemporadaLigaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import estadisticas.dto.ligas.RankingJugadorDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Ligas", description = "Endpoints para consultar competiciones")
public interface LigaController {

    @Operation(summary = "Obtener todas las ligas", description = "Devuelve el catálogo de todas las ligas disponibles en la plataforma")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ligas obtenidas correctamente")
    })
    ResponseEntity<List<LigaDTO>> obtenerTodas();

    @Operation(summary = "Obtener una liga por ID", description = "Devuelve los detalles de una competición específica")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liga obtenida correctamente"),
            @ApiResponse(responseCode = "400", description = "ID no válido"),
            @ApiResponse(responseCode = "404", description = "Liga no encontrada")
    })
    ResponseEntity<LigaDTO> obtenerPorId(@PathVariable("id") String idString);

    @Operation(summary = "Temporadas disponibles de una liga",
            description = "Devuelve, de la más reciente a la más antigua, las temporadas de las que hay clasificación, "
                    + "marcando cuáles tienen partidos ya disputados. Permite al cliente abrir la última temporada con "
                    + "datos reales en vez de una recién sorteada con la tabla a cero, y ofrecer un selector de temporada.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Temporadas obtenidas correctamente"),
            @ApiResponse(responseCode = "400", description = "ID no válido")
    })
    ResponseEntity<List<TemporadaLigaDTO>> obtenerTemporadas(@PathVariable("id") String idString);

    @Operation(summary = "Ranking de jugadores de una competición",
            description = "Goleadores, asistentes, amarillas o rojas de una liga y temporada. Sale directamente de " +
                    "API-Football (una petición por liga+temporada+tipo) y se cachea 6 horas: es un agregado que " +
                    "ellos ya calculan y que se mueve como mucho una vez por jornada.")
    ResponseEntity<List<RankingJugadorDTO>> obtenerRanking(@PathVariable("id") String idString,
                                                          @RequestParam("temporada") Integer temporada,
                                                          @RequestParam(name = "tipo", defaultValue = "GOLES") String tipo);
}
