package usuariosroles.controllers.favoritos;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import usuariosroles.dto.FavoritoDTO;

/**
 * Interface for the user favorites controller.
 * Manages favorite teams, leagues and matches per user (watchlist).
 *
 * @author Daniel
 * @version 1.0
 */
@Tag(name = "Favoritos", description = "API para la gestión de equipos, ligas y partidos favoritos del usuario")
public interface FavoritoController {

    // --- EQUIPOS ---

    @Operation(summary = "Añadir equipo a favoritos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Equipo añadido a favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes modificar los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "409", description = "El equipo ya está en favoritos")
    })
    ResponseEntity<Void> addEquipoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                           @PathVariable("id_equipo_api") String idEquipoApiString);

    @Operation(summary = "Eliminar equipo de favoritos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Equipo eliminado de favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes modificar los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    ResponseEntity<Void> removeEquipoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                              @PathVariable("id_equipo_api") String idEquipoApiString);

    @Operation(summary = "Listar equipos favoritos del usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de equipos favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes ver los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    ResponseEntity<List<FavoritoDTO>> getEquiposFavoritos(@PathVariable("id_usuario") String idUsuarioString);

    // --- LIGAS ---

    @Operation(summary = "Añadir liga a favoritos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Liga añadida a favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes modificar los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "409", description = "La liga ya está en favoritos")
    })
    ResponseEntity<Void> addLigaFavorita(@PathVariable("id_usuario") String idUsuarioString,
                                         @PathVariable("id_liga_api") String idLigaApiString);

    @Operation(summary = "Eliminar liga de favoritos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Liga eliminada de favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes modificar los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    ResponseEntity<Void> removeLigaFavorita(@PathVariable("id_usuario") String idUsuarioString,
                                            @PathVariable("id_liga_api") String idLigaApiString);

    @Operation(summary = "Listar ligas favoritas del usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de ligas favoritas"),
            @ApiResponse(responseCode = "403", description = "No puedes ver los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    ResponseEntity<List<FavoritoDTO>> getLigasFavoritas(@PathVariable("id_usuario") String idUsuarioString);

    // --- PARTIDOS ---

    @Operation(summary = "Añadir partido a favoritos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Partido añadido a favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes modificar los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
            @ApiResponse(responseCode = "409", description = "El partido ya está en favoritos")
    })
    ResponseEntity<Void> addPartidoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                            @PathVariable("id_partido_api") String idPartidoApiString);

    @Operation(summary = "Eliminar partido de favoritos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Partido eliminado de favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes modificar los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    ResponseEntity<Void> removePartidoFavorito(@PathVariable("id_usuario") String idUsuarioString,
                                               @PathVariable("id_partido_api") String idPartidoApiString);

    @Operation(summary = "Listar partidos favoritos del usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de partidos favoritos"),
            @ApiResponse(responseCode = "403", description = "No puedes ver los favoritos de otro usuario"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    ResponseEntity<List<FavoritoDTO>> getPartidosFavoritos(@PathVariable("id_usuario") String idUsuarioString);
}