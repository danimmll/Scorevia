package usuariosroles.services.usuariorol;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import usuariosroles.dto.UsuarioRolDTO;

/**
 * Interface for the user-role assignment management service.
 * Defines operations to assign, remove, and query roles for users,
 * as well as users associated with specific roles.
 */
public interface UsuarioRolService {

    /**
     * Assigns a role to a user if both exist and the role is not already assigned.
     *
     * @param idUsuario Identifier of the user.
     * @param idRol Identifier of the role.
     * @return DTO of the created assignment, or null if the user or role does not exist.
     */
    UsuarioRolDTO asignarRolUsuario(Integer idUsuario, Integer idRol);

    /**
     * Removes a role assigned to a user.
     *
     * @param idUsuario Identifier of the user.
     * @param idRol Identifier of the role.
     * @return DTO of the removed assignment, or null if it does not exist.
     */
    UsuarioRolDTO quitarRolUsuario(Integer idUsuario, Integer idRol);

    /**
     * Retrieves all roles assigned to a user.
     *
     * @param idUsuario Identifier of the user.
     * @return List of DTOs containing information about the assigned roles.
     */
    List<UsuarioRolDTO> getRolesUsuario(Integer idUsuario);

    /**
     * Retrieves all users who have a specific role.
     *
     * @param idRol Identifier of the role.
     * @return List of DTOs containing information about the assigned users.
     */
    List<UsuarioRolDTO> getUsuariosRol(Integer idRol);

    /**
     * Retrieves all existing user-role assignments, paginated.
     *
     * @param pageable Pagination and sorting parameters.
     * @return Page of DTOs containing the assignments.
     */
    Page<UsuarioRolDTO> getAsignaciones(Pageable pageable);
}
