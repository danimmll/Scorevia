package usuariosroles.services.usuario;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioUpdateDTO;
import usuariosroles.entities.Usuario;

/**
 * Interface for the user management service.
 * Defines CRUD operations and verification methods related to user administration.
 */
public interface UsuarioService {

    /**
     * Creates a new user with the default role.
     *
     * @param usuarioDTO DTO containing user data.
     * @return DTO of the created user with the assigned role.
     */
    UsuarioDTO crearUsuario(UsuarioDTO usuarioDTO);

    /**
     * Retrieves an active user by its identifier.
     *
     * @param id Identifier of the user.
     * @return DTO of the found user, or null if it does not exist.
     */
    UsuarioDTO getUsuarioById(Integer id);

    /**
     * Retrieves all active users.
     *
     * @param pageable Pagination information.
     * @return Page containing DTOs of active users.
     */
    Page<UsuarioDTO> getUsuarios(Pageable pageable);

    /**
     * Updates an existing user with permission control.
     *
     * @param usuarioDTO DTO containing updated user data.
     * @return DTO of the updated user.
     */
    UsuarioDTO putUsuarioDTO(UsuarioUpdateDTO usuarioDTO);

    /**
     * Activates a deactivated user and assigns the default role.
     *
     * @param usuarioDTO DTO containing user data.
     * @return DTO of the activated user, or null if it does not exist.
     */
    UsuarioDTO activarUsuarioDTO(UsuarioDTO usuarioDTO);

    /**
     * Marks a user as inactive and removes all roles.
     *
     * @param id Identifier of the user.
     * @return DTO of the updated user, or null if it does not exist.
     */
    UsuarioDTO deleteUsuarioDTO(Integer id);

    /**
     * Checks if a user exists by email.
     *
     * @param correo Email address of the user.
     * @return true if it exists, false otherwise.
     */
    boolean existsUsuario(String correo);

    /**
     * Checks if a user exists by nickname.
     *
     * @param nickname Nickname of the user.
     * @return true if it exists, false otherwise.
     */
    boolean existsNickname(String nickname);

    /**
     * Checks if a deactivated user exists by email.
     *
     * @param correo Email address of the user.
     * @return true if it exists and is deactivated, false otherwise.
     */
    boolean existsUsuarioDesactivado(String correo);

    /**
     * Cuenta todos los usuarios (activos e inactivos) -- usado para saber, antes de lanzar
     * el export a Excel, si hay algo que exportar sin tener que leerlos todos primero.
     *
     * @return Número total de usuarios.
     */
    long contarUsuarios();

    /**
     * Recorre TODOS los usuarios (activos e inactivos) en lotes acotados, empujando cada
     * uno al consumidor a medida que se leen -- pensado para el export a Excel, para no
     * materializar la tabla completa en memoria de golpe (ver {@code UsuarioRepository
     * .findByIdUsuarioGreaterThanOrderByIdUsuarioAsc}).
     *
     * @param consumidor Recibe cada {@link UsuarioDTO}, uno a uno, en orden de id ascendente.
     */
    void exportarTodosStreaming(Consumer<UsuarioDTO> consumidor);

    /**
     * Comprueba el código de verificación de 6 dígitos enviado por email y,
     * si es correcto y no ha caducado, marca al usuario como verificado.
     *
     * @param correo correo del usuario que se está verificando
     * @param codigo código de 6 dígitos recibido por email
     */
    void verificarEmail(String correo, String codigo);

    /**
     * Solicita un código de reset de contraseña. Anti-enumeración a propósito: si el correo no
     * existe, no lanza ni hace nada visible desde fuera -- el llamador (controller) siempre
     * responde el mismo mensaje genérico, exista o no esa cuenta.
     *
     * @param correo correo del que se solicita recuperar el acceso
     */
    void solicitarResetPassword(String correo);

    /**
     * Comprueba el código de reset de contraseña y, si es correcto y no ha caducado, aplica la
     * nueva contraseña y revoca todas las sesiones activas del usuario.
     *
     * @param correo          correo del usuario que resetea su contraseña
     * @param codigo          código de 6 dígitos recibido por email
     * @param nuevaContrasenha contraseña nueva, en claro (se cifra aquí)
     */
    void resetPassword(String correo, String codigo, String nuevaContrasenha);

    /**
     * Procesa el login a través del token de Google. Registra al usuario si no existe.
     *
     * @param tokenString Token JWT proporcionado por Google.
     * @return La entidad Usuario autenticada.
     */
    Usuario procesarLoginGoogle(String tokenString);

    /**
     * Resuelve el nickname de un lote de usuarios por su id. Se usa internamente
     * para enriquecer respuestas de otros microservicios
     * sin exponer el resto de los datos del usuario.
     *
     * @param idsUsuarios identificadores de usuario a resolver.
     * @return mapa idUsuario -> nickname. Los ids que no existan simplemente no aparecen en el mapa.
     */
    Map<Integer, String> obtenerNicknames(List<Integer> idsUsuarios);

    /**
     * Comprueba si existe un usuario con ese id, sin exponer ningún otro dato. Se usa
     * internamente para validar referencias a un usuario desde otros microservicios (p.ej.
     * incidencias validando el denunciado de un reporte).
     *
     * @param idUsuario identificador del usuario a comprobar.
     * @return true si existe una fila con ese id (activo o no).
     */
    boolean existeUsuario(Integer idUsuario);

    /**
     * Comprueba si existe un usuario con ese id Y está activo. Se usa internamente para
     * revalidar sesiones de larga duración frente a un usuario baneado/desactivado a mitad
     * de sesión.
     *
     * @param idUsuario identificador del usuario a comprobar.
     * @return true si existe una fila con ese id y activo=true.
     */
    boolean usuarioActivo(Integer idUsuario);
}