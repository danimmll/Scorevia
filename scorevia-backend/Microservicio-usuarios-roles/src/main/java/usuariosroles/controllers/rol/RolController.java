package usuariosroles.controllers.rol;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import usuariosroles.dto.RolDTO;

/**
 * Interface for the role management controller.
 * Defines CRUD endpoints related to role administration.
 *
 * <p>Uses Swagger annotations to document API operations and responses.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Tag(name = "Roles", description = "API para la gestión de roles")
public interface RolController {

    /**
     * Retrieves the list of all roles.
     *
     * @return List of roles.
     */
    @Operation(
        summary = "Obtener todos los roles",
        description = "Recupera todos los roles del sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Roles obtenidos correctamente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<List<RolDTO>> getRoles();

    /**
     * Retrieves a role by its identifier.
     *
     * @param idString Role ID in string format.
     * @return Role found.
     */
    @Operation(
        summary = "Obtener rol por ID",
        description = "Recupera un rol utilizando su identificador único."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rol obtenido correctamente"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<RolDTO> getRolById(@PathVariable("id_rol") String idString);

    /**
     * Creates a new role.
     *
     * @param rolDTO DTO containing role data to create.
     * @param result Validation result for the request body.
     * @return Created role.
     */
    @Operation(
        summary = "Crear nuevo rol",
        description = "Crea un nuevo rol en el sistema."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Rol creado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación en los datos proporcionados"),
        @ApiResponse(responseCode = "409", description = "Conflicto: el rol ya existe")
    })
    ResponseEntity<Object> crearRol(@Valid @RequestBody RolDTO rolDTO, BindingResult result);

    /**
     * Updates an existing role.
     *
     * @param rolDTO DTO containing updated role data.
     * @param result Validation result for the request body.
     * @param idString Role ID in string format.
     * @return Updated role.
     */
    @Operation(
        summary = "Actualizar rol",
        description = "Actualiza un rol existente con nuevos datos."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rol actualizado correctamente"),
        @ApiResponse(responseCode = "400", description = "Error de validación en los datos proporcionados"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflicto: ya existe otro rol con el mismo nombre")
    })
    ResponseEntity<Object> putRol(@Valid @RequestBody RolDTO rolDTO, BindingResult result,
                                  @PathVariable("id_rol") String idString);

    /**
     * Deletes a role by its identifier.
     *
     * @param idString Role ID in string format.
     * @return Deleted role.
     */
    @Operation(
        summary = "Eliminar rol",
        description = "Elimina un rol del sistema utilizando su identificador único."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Rol eliminado correctamente"),
        @ApiResponse(responseCode = "404", description = "Rol no encontrado"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    ResponseEntity<RolDTO> deleteRol(@PathVariable("id_rol") String idString);
}