package usuariosroles.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.entities.Usuario;

/**
 * Mapper for converting between {@link Usuario} entity and {@link UsuarioDTO}.
 *
 * <p>Uses the <b>MapStruct</b> library to automatically generate conversion code
 * between objects, avoiding manual repetitive code.</p>
 *
 * @author Daniel
 * @version 1.1
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "idUsuario", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "contrasenhaUsuario", ignore = true)
        // El nickname y el nombreUsuario se mapean automáticamente
    UsuarioDTO toDtoPost(Usuario usuario);

    @Mapping(target = "contrasenhaUsuario", ignore = true)
    @Mapping(target = "roles", ignore = true)
    UsuarioDTO toDtoGetById(Usuario usuario);

    @Mapping(target = "contrasenhaUsuario", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "roles", ignore = true)
    UsuarioDTO toDtoGetAll(Usuario usuario);

    @Mapping(target = "contrasenhaUsuario", ignore = true)
    @Mapping(target = "roles", ignore = true)
    UsuarioDTO toDtoExcel(Usuario usuario);

    @Mapping(target = "idUsuario", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "contrasenhaUsuario", ignore = true)
    UsuarioDTO toDtoPut(Usuario usuario);

    @Mapping(target = "correoUsuario", ignore = true)
    @Mapping(target = "contrasenhaUsuario", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "urlFoto", ignore = true)
    @Mapping(target = "nickname", ignore = true) // Ignoramos nickname al borrar por privacidad
    UsuarioDTO toDtoDelete(Usuario usuario);

    @Mapping(target = "emailVerificado", ignore = true)
    @Mapping(target = "codigoVerificacion", ignore = true)
    @Mapping(target = "codigoVerificacionExpira", ignore = true)
    @Mapping(target = "intentosVerificacion", ignore = true)
    @Mapping(target = "bloqueadoVerificacionHasta", ignore = true)
    @Mapping(target = "intentosLogin", ignore = true)
    @Mapping(target = "bloqueadoLoginHasta", ignore = true)
    // esPremium/activo: UsuarioDTO los expone como campos normales (sin
    // @JsonIgnore, a diferencia de "roles") porque también se usan para DEVOLVER el perfil al
    // cliente -- pero este método (toUsuario) solo se usa en el registro
    // (UsuarioServiceImpl.crearUsuario), y ahí el DTO de entrada es el mismo body público que
    // manda cualquiera sin autenticar. Sin este ignore, un POST /api/v1/auth/register con
    // "esPremium": true se persistía tal cual -- Usuario.prePersist() solo aplica su
    // default (false/true) cuando el campo llega null, nunca lo sobreescribe si el cliente
    // ya mandó un valor. Estos campos los decide siempre el servidor, nunca el registro.
    @Mapping(target = "esPremium", ignore = true)
    @Mapping(target = "activo", ignore = true)
    Usuario toUsuario(UsuarioDTO usuarioDTO);
}