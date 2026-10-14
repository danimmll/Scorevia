package usuariosroles.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import usuariosroles.dto.RolDTO;
import usuariosroles.entities.Rol;

/**
 * Mapper for converting between {@link Rol} entity and {@link RolDTO}.
 *
 * <p>Uses the <b>MapStruct</b> library to automatically generate conversion code
 * between objects, avoiding manual repetitive code.</p>
 *
 * <p>Includes specific methods for different use cases (GET, POST, PUT, DELETE)
 * with customized mapping rules.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Mapper(componentModel = "spring")
public interface RolMapper {

    /**
     * Converts a {@link Rol} to {@link RolDTO} for POST or DELETE operations.
     *
     * <p>Ignores the {@code activo} field in the DTO.</p>
     *
     * @param rol {@link Rol} entity to convert.
     * @return Resulting {@link RolDTO}.
     */
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "usuarios", ignore = true)
    RolDTO toDtoPostDelete(Rol rol);

    /**
     * Converts a {@link Rol} to {@link RolDTO} for GET operations.
     *
     * @param rol {@link Rol} entity to convert.
     * @return {@link RolDTO} with all fields.
     */
    @Mapping(target = "usuarios", ignore = true)
    RolDTO toDtoGet(Rol rol);

    /**
     * Converts a {@link Rol} to {@link RolDTO} for PUT operations.
     *
     * <p>Ignores the {@code idRol} field in the DTO.</p>
     *
     * @param rol {@link Rol} entity to convert.
     * @return {@link RolDTO} without the identifier.
     */
    @Mapping(target = "idRol", ignore = true)
    @Mapping(target = "usuarios", ignore = true)
    RolDTO toDtoPut(Rol rol);

    /**
     * Converts a {@link RolDTO} to {@link Rol} entity.
     *
     * @param rolDTO {@link RolDTO} to convert.
     * @return Resulting {@link Rol} entity.
     */
    Rol toRol(RolDTO rolDTO);
}
