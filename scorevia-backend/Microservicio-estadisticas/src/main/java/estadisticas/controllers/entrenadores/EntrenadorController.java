package estadisticas.controllers.entrenadores;

import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.dto.entrenadores.HistorialEntrenadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Entrenadores", description = "Endpoints para perfiles de técnicos y su palmarés")
public interface EntrenadorController {

    @Operation(summary = "Perfil de entrenador", description = "Devuelve los detalles de un técnico")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entrenador obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "Entrenador no encontrado")
    })
    ResponseEntity<EntrenadorDTO> obtenerPorId(@PathVariable("id") String idString);

    @Operation(summary = "Entrenador actual del equipo", description = "Devuelve el técnico asignado actualmente al club")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entrenador obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "El equipo no tiene entrenador activo")
    })
    ResponseEntity<EntrenadorDTO> obtenerPorEquipo(@PathVariable("idEquipo") String idEquipoString);

    @Operation(summary = "Palmarés del entrenador", description = "Devuelve los trofeos ganados desde el banquillo")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trofeos obtenidos correctamente")
    })
    ResponseEntity<List<TrofeoDTO>> obtenerTrofeos(@PathVariable("id") String idString);

    @Operation(summary = "Entrenadores por liga y temporada", description = "Devuelve los técnicos de los equipos que participan en una competición en una temporada dada")
    ResponseEntity<List<EntrenadorDTO>> obtenerPorLiga(@PathVariable("idLiga") String idLigaString, @RequestParam("temporada") String temporadaString);

    @Operation(summary = "Clubes entrenados", description = "Historial de clubes por los que ha pasado este técnico")
    ResponseEntity<List<HistorialEntrenadorDTO>> obtenerHistorialClubes(@PathVariable("id") String idString);

    @Operation(summary = "Historial de banquillo de un equipo", description = "Técnicos que han pasado por el banquillo de este equipo")
    ResponseEntity<List<HistorialEntrenadorDTO>> obtenerHistorialPorEquipo(@PathVariable("idEquipo") String idEquipoString);

    @Operation(summary = "Buscar entrenadores por nombre",
            description = "Autocompletado de técnicos por nombre.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Búsqueda realizada correctamente")
    })
    ResponseEntity<List<EntrenadorDTO>> buscarEntrenadores(@RequestParam String nombre);
}