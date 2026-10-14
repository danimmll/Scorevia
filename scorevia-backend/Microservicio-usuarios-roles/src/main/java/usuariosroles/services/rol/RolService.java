package usuariosroles.services.rol;

import java.util.List;
import usuariosroles.dto.RolDTO;

/**
 * Interface for the role management service.
 * Defines CRUD operations and verification methods related to role administration.
 */
public interface RolService {

    /**
     * Creates a new role.
     *
     * @param rolDTO DTO containing role data.
     * @return DTO of the created role.
     */
    RolDTO crearRolDTO(RolDTO rolDTO);

    /**
     * Retrieves a role by its identifier.
     *
     * @param id Role identifier.
     * @return DTO of the found role, or null if it does not exist.
     */
    RolDTO getRolByIdDTO(Integer id);

    /**
     * Retrieves all active roles.
     *
     * @return List of DTOs representing active roles.
     */
    List<RolDTO> getRolesDTO();

    /**
     * Updates an existing role.
     *
     * @param rolDTO DTO containing updated role data.
     * @return DTO of the updated role, or null if it does not exist.
     */
    RolDTO putRolDTO(RolDTO rolDTO);

    /**
     * Activates a previously deactivated role.
     *
     * @param rolDTO DTO containing the name of the role to activate.
     * @return DTO of the activated role, or null if it does not exist.
     */
    RolDTO activarRolDTO(RolDTO rolDTO);

    /**
     * Marks a role as inactive (logical deletion).
     *
     * @param id Role identifier.
     * @return DTO of the updated role.
     */
    RolDTO deleteRol(Integer id);

    /**
     * Checks if a role exists by its name.
     *
     * @param nombreRol Name of the role.
     * @return true if it exists, false otherwise.
     */
    boolean existsRol(String nombreRol);

    /**
     * Checks if a deactivated role exists by its name.
     *
     * @param nombreRol Name of the role.
     * @return true if it exists and is deactivated, false otherwise.
     */
    boolean existsRolDesactivado(String nombreRol);
}
