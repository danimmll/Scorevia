package usuariosroles.controllers.usuario;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioUpdateDTO;

/**
 * Interface for the user management controller.
 * Defines CRUD endpoints and additional operations related to user administration.
 *
 * <p>Uses Swagger annotations to document API operations and responses.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Tag(name = "Usuarios", description = "API para la gestión de usuarios")
public interface UsuarioController {

    /**
     * Retrieves a paginated list of users.
     *
     * @param page Page number (default: 0).
     * @param size Page size (default: 5).
     * @param sort Field to sort by (default: idUsuario).
     * @param dir  Sort direction (asc or desc, default: asc).
     * @return List of users.
     */
    @Operation(
        summary = "Obtener lista paginada de usuarios",
        description = "Recupera una lista paginada de usuarios con opciones de ordenación."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Usuarios obtenidos correctamente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<Page<UsuarioDTO>> getUsuarios(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "5") int size,
                                                 @RequestParam(defaultValue = "idUsuario") String sort,
                                                 @RequestParam(defaultValue = "asc") String dir);

    /**
     * Retrieves a user by its identifier.
     *
     * @param idString User ID in string format.
     * @return User found.
     */
    @Operation(
        summary = "Obtener usuario por ID",
        description = "Recupera un usuario utilizando su identificador único."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Usuario obtenido correctamente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<UsuarioDTO> getUsuarioById(@PathVariable("id_usuario") String idString);

    /**
     * Creates a new user.
     *
     * @param usuarioDTO DTO containing user data to create.
     * @param result     Validation result for the request body.
     * @return Created user.
     */
    @Operation(
        summary = "Crear nuevo usuario",
        description = "Crea un nuevo usuario en el sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Usuario creado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación en los datos proporcionados"),
        @ApiResponse(responseCode = "409", description = "Conflicto: el usuario ya existe")
    })
    ResponseEntity<Object> crearUsuario(@Valid @RequestBody UsuarioDTO usuarioDTO, BindingResult result);

    /**
     * Updates an existing user.
     *
     * @param usuarioDTO DTO containing updated user data.
     * @param result     Validation result for the request body.
     * @param idString   User ID in string format.
     * @return Updated user.
     */
    @Operation(
        summary = "Actualizar usuario",
        description = "Actualiza un usuario existente con nuevos datos."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Usuario actualizado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación en los datos proporcionados"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflicto: ya existe otro usuario con el mismo correo electrónico")
    })
    ResponseEntity<Object> putUsuario(@Valid @RequestBody UsuarioUpdateDTO usuarioDTO, BindingResult result,
                                      @PathVariable("id_usuario") String idString);

    /**
     * Deletes a user by its identifier.
     *
     * @param idString User ID in string format.
     * @return Deleted user.
     */
    @Operation(
        summary = "Eliminar usuario",
        description = "Elimina un usuario del sistema utilizando su identificador único."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Usuario eliminado correctamente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<UsuarioDTO> deleteUsuario(@PathVariable("id_usuario") String idString);

    /**
     * Exports all users to an Excel file.
     *
     * @return Excel file containing all users.
     */
    @Operation(
        summary = "Exportar usuarios a Excel",
        description = "Exporta todos los usuarios del sistema a un archivo Excel."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Archivo Excel generado correctamente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/exportar")
    ResponseEntity<InputStreamResource> exportarTodosUsuariosExcel();
}