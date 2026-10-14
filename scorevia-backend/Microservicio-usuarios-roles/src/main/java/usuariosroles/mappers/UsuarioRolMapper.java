package usuariosroles.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import usuariosroles.dto.UsuarioRolDTO;
import usuariosroles.entities.UsuarioRol;

/**
 * Mapper for converting {@link UsuarioRol} entity to {@link UsuarioRolDTO}.
 *
 * <p>Uses <b>MapStruct</b> to automatically generate conversion code,
 * avoiding manual repetitive coding.</p>
 *
 * <p>Includes specific methods for different use cases (POST, GET, DELETE, etc.)
 * with customized mapping rules that ignore or assign certain fields depending on the context.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface UsuarioRolMapper {

    /**
     * Converts a {@link UsuarioRol} to {@link UsuarioRolDTO} for POST, GET, or DELETE operations.
     *
     * <p>Includes user and role identifiers but ignores names and statuses.</p>
     *
     * @param usuarioRol {@link UsuarioRol} entity to convert.
     * @return {@link UsuarioRolDTO} containing user and role IDs.
     */
    @Mapping(target = "idRol", source = "rol.idRol")
    @Mapping(target = "idUsuario", source = "usuario.idUsuario")
    @Mapping(target = "nombreUsuario", ignore = true)
    @Mapping(target = "activoUsuario", ignore = true)
    @Mapping(target = "nombreRol", ignore = true)
    @Mapping(target = "activoRol", ignore = true)
    UsuarioRolDTO toDtoDeleteGetPost(UsuarioRol usuarioRol);

    /**
     * Converts a {@link UsuarioRol} to {@link UsuarioRolDTO} for listing roles of a user.
     *
     * <p>Includes role ID and data but ignores user data.</p>
     *
     * @param usuarioRol {@link UsuarioRol} entity to convert.
     * @return {@link UsuarioRolDTO} containing role information.
     */
    @Mapping(target = "idRol", source = "rol.idRol")
    @Mapping(target = "idUsuario", ignore = true)
    @Mapping(target = "nombreUsuario", ignore = true)
    @Mapping(target = "activoUsuario", ignore = true)
    @Mapping(target = "nombreRol", source = "rol.nombreRol")
    @Mapping(target = "activoRol", source = "rol.activo")
    UsuarioRolDTO toDtoRolesUsuario(UsuarioRol usuarioRol);

    /**
     * Converts a {@link UsuarioRol} to {@link UsuarioRolDTO} for listing users of a role.
     *
     * <p>Includes user name and status but ignores role data.</p>
     *
     * @param usuarioRol {@link UsuarioRol} entity to convert.
     * @return {@link UsuarioRolDTO} containing user information.
     */
    @Mapping(target = "idRol", ignore = true)
    @Mapping(target = "idUsuario", ignore = true)
    @Mapping(target = "nombreUsuario", source = "usuario.nombreUsuario")
    @Mapping(target = "activoUsuario", source = "usuario.activo")
    @Mapping(target = "nombreRol", ignore = true)
    @Mapping(target = "activoRol", ignore = true)
    UsuarioRolDTO toDtoUsuariosRol(UsuarioRol usuarioRol);
}
