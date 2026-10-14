package usuariosroles.controllers.usuariorol;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import usuariosroles.dto.UsuarioRolDTO;

/**
 * Interface for the user-role assignment management controller.
 * Defines endpoints to assign, remove, and query roles for users,
 * as well as users associated with specific roles.
 *
 * <p>Uses Swagger annotations to document API operations and responses.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Tag(name = "Asignación Usuario-Rol", description = "API para la gestión de asignaciones entre usuarios y roles")
public interface UsuarioRolController {

    /**
     * Assigns a role to a user.
     *
     * @param usuarioRolDTO DTO containing role assignment data.
     * @param result        Validation result for the request body.
     * @param idUsuarioString User ID in string format.
     * @return Created assignment.
     */
    @Operation(
        summary = "Asignar rol a usuario",
        description = "Asigna un rol a un usuario si ambos existen y el rol no está previamente asignado."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Rol asignado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación en los datos proporcionados"),
        @ApiResponse(responseCode = "404", description = "Usuario o rol no encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflicto: el rol ya está asignado al usuario")
    })
    ResponseEntity<Object> asignarRol(@Valid @RequestBody UsuarioRolDTO usuarioRolDTO, BindingResult result,
                                      @PathVariable("id_usuario") String idUsuarioString);

    /**
     * Removes a role assigned to a user.
     *
     * @param idUsuarioString User ID in string format.
     * @param idRolString     Role ID in string format.
     * @return Removed assignment.
     */
    @Operation(
        summary = "Quitar rol a usuario",
        description = "Elimina un rol previamente asignado a un usuario."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rol eliminado correctamente"),
        @ApiResponse(responseCode = "404", description = "Asignación no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<UsuarioRolDTO> quitarRol(@PathVariable("id_usuario") String idUsuarioString,
                                            @PathVariable("id_rol") String idRolString);

    /**
     * Retrieves all roles assigned to a user.
     *
     * @param idUsuarioString User ID in string format.
     * @return List of assigned roles.
     */
    @Operation(
        summary = "Obtener roles de un usuario",
        description = "Recupera todos los roles asignados a un usuario específico."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Roles obtenidos correctamente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<List<UsuarioRolDTO>> getRolesUsuario(@PathVariable("id_usuario") String idUsuarioString);

    /**
     * Retrieves all users who have a specific role.
     *
     * @param idRolString Role ID in string format.
     * @return List of users.
     */
    @Operation(
        summary = "Obtener usuarios por rol",
        description = "Recupera todos los usuarios que tienen asignado un rol específico."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Usuarios obtenidos correctamente"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<List<UsuarioRolDTO>> getUsuariosRol(@PathVariable("id_rol") String idRolString);

    /**
     * Retrieves all existing user-role assignments.
     *
     * @return List of assignments.
     */
    @Operation(
        summary = "Obtener todas las asignaciones",
        description = "Recupera todas las asignaciones existentes entre usuarios y roles en el sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Asignaciones obtenidas correctamente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<Page<UsuarioRolDTO>> getTodasAsignaciones(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @RequestParam(defaultValue = "id.idUsuario") String sort,
                                                              @RequestParam(defaultValue = "asc") String dir);
}