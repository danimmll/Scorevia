package estadisticas.controllers.jugadores;

import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.dto.jugadores.TemporadaJugadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Jugadores", description = "Endpoints para perfiles de futbolistas, plantillas y palmarés")
public interface JugadorController {

    @Operation(summary = "Perfil de jugador", description = "Devuelve los datos biográficos y físicos de un jugador")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Jugador obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "Jugador no encontrado")
    })
    ResponseEntity<JugadorDTO> obtenerPerfil(@PathVariable("id") String idString);

    @Operation(summary = "Palmarés del jugador", description = "Devuelve la lista de trofeos ganados en su carrera")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trofeos obtenidos correctamente")
    })
    ResponseEntity<List<TrofeoDTO>> obtenerTrofeos(@PathVariable("id") String idString);

    @Operation(summary = "Historial clínico/disciplinario", description = "Devuelve el timeline de lesiones y sanciones del jugador")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial obtenido correctamente")
    })
    ResponseEntity<List<HistorialBajaDTO>> obtenerBajas(@PathVariable("id") String idString);

    @Operation(summary = "Historial de traspasos", description = "Devuelve todos los movimientos de mercado del jugador")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Traspasos obtenidos correctamente")
    })
    ResponseEntity<List<TraspasoDTO>> obtenerTraspasos(@PathVariable("id") String idString);

    @Operation(summary = "Obtener todos los jugadores (Paginado)", description = "Devuelve una página con la lista de todos los jugadores del sistema.")
    ResponseEntity<Page<JugadorDTO>> getAllJugadores(@PageableDefault(size = 20) Pageable pageable);

    @Operation(summary = "Obtener jugadores por Equipo (Paginado)", description = "Devuelve una página con los jugadores de un equipo específico.")
    ResponseEntity<Page<JugadorDTO>> obtenerPlantilla(@PathVariable Integer idEquipo, @PageableDefault(size = 20) Pageable pageable);

    @Operation(summary = "Obtener jugadores por Liga y temporada (Paginado)", description = "Devuelve una página con los jugadores de una liga específica en una temporada dada.")
    ResponseEntity<Page<JugadorDTO>> getJugadoresPorLiga(@PathVariable Integer idLiga, @RequestParam Integer temporada, @PageableDefault(size = 20) Pageable pageable);

    @Operation(summary = "Buscar jugadores por nombre (Paginado)", description = "Búsqueda global por nombre, sin restringir a la liga o equipo actual.")
    ResponseEntity<Page<JugadorDTO>> buscarJugadores(@RequestParam String nombre, @PageableDefault(size = 20) Pageable pageable);

    @Operation(summary = "Trayectoria de dorsales", description = "Devuelve la evolución de dorsal y posición del jugador por equipo y temporada")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial de dorsales obtenido correctamente")
    })
    ResponseEntity<List<HistorialDorsalDTO>> obtenerHistorialDorsales(@PathVariable("id") String idString);

    @Operation(summary = "El jugador temporada a temporada",
            description = "Partidos, minutos, goles, asistencias y tarjetas en cada temporada y "
                    + "competicion, de la mas reciente a la mas antigua. Sale de agregar sus "
                    + "partidos ya ingeridos, asi que no cuesta ninguna peticion a API-Football. "
                    + "Un jugador que cambio de club a mitad de temporada aparece con una fila "
                    + "por etapa.")
    ResponseEntity<List<TemporadaJugadorDTO>> obtenerPorTemporadas(@PathVariable("id") String idString);
}
