package estadisticas.controllers.equipos;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import estadisticas.dto.equipos.RendimientoEquipoDTO;
import estadisticas.dto.equipos.TemporadaEquipoDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Equipos", description = "Endpoints para la gestión de Clubes")
public interface EquipoController {

    @Operation(summary = "Obtener equipo por ID", description = "Devuelve el perfil del equipo, incluyendo los datos de su estadio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Equipo obtenido correctamente"),
            @ApiResponse(responseCode = "400", description = "ID no válido"),
            @ApiResponse(responseCode = "404", description = "Equipo no encontrado")
    })
    ResponseEntity<EquipoDTO> obtenerPorId(@PathVariable("id") String idString);

    @Operation(summary = "Obtener equipos por liga", description = "Devuelve los equipos que participan en una competición en una temporada concreta")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Equipos obtenidos correctamente"),
            @ApiResponse(responseCode = "400", description = "ID de liga o temporada no válidos")
    })
    ResponseEntity<List<EquipoDTO>> obtenerPorLiga(@PathVariable("idLiga") String idLigaString, @RequestParam("temporada") String temporadaString);

    @Operation(summary = "Historial de traspasos del equipo", description = "Devuelve las altas y bajas (movimientos) donde el equipo fue origen o destino")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Traspasos obtenidos correctamente")
    })
    ResponseEntity<List<TraspasoDTO>> obtenerTraspasos(@PathVariable("id") String idString);

    @Operation(summary = "Buscar equipos por nombre", description = "Búsqueda global por nombre, sin restringir a la liga/país actualmente filtrados")
    ResponseEntity<List<EquipoDTO>> buscarEquipos(@RequestParam String nombre);

    @Operation(summary = "Rendimiento de un equipo en una competición",
            description = "Racha, balance en casa y fuera, goles por franja de minutos, porterías a cero, " +
                    "mayores goleadas y tarjetas. Sale de API-Football en el momento (1 petición por " +
                    "equipo+liga+temporada) y se cachea 6 horas.")
    ResponseEntity<RendimientoEquipoDTO> obtenerRendimiento(@PathVariable("id") String idString,
                                                            @RequestParam("liga") Integer idLiga,
                                                            @RequestParam("temporada") Integer temporada);

    @Operation(summary = "Temporadas en las que jugo el equipo",
            description = "De la mas reciente a la mas antigua. Salen de sus partidos y no de "
                    + "las clasificaciones, porque hay competiciones sin tabla (copas a partido "
                    + "unico, amistosos) que tambien cuentan como temporada jugada.")
    ResponseEntity<List<Integer>> obtenerTemporadas(@PathVariable("id") String idString);

    @Operation(summary = "La temporada completa de un equipo",
            description = "Plantilla con lo que hizo cada jugador, entrenadores, competiciones "
                    + "jugadas con su puesto final, titulos y totales. Todo de datos ya "
                    + "ingeridos: no cuesta ninguna peticion a API-Football.")
    ResponseEntity<TemporadaEquipoDTO> obtenerTemporada(@PathVariable("id") String idString,
                                                        @PathVariable("temporada") Integer temporada);
}
