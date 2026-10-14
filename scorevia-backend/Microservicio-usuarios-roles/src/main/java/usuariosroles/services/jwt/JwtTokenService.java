package usuariosroles.services.jwt;

import io.jsonwebtoken.Claims;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/**
 * Interface para el servicio de gestión de tokens JWT bajo arquitectura Access/Refresh.
 * * @author Daniel
 * @version 2.0
 */
public interface JwtTokenService {

    /**
     * Genera un Access Token de vida corta con las autoridades y metadatos del usuario.
     */
    String generarAccessToken(Integer idUsuario, String correo, Collection<? extends GrantedAuthority> roles);

    /**
     * Genera un Refresh Token de vida larga blindado únicamente para renovar sesiones, y
     * persiste su jti como sesión activa (necesario para poder rotarlo/revocarlo más adelante).
     */
    String generarRefreshToken(Integer idUsuario, String correo);

    /**
     * Valida el token contra la firma criptográfica y extrae sus Claims verificando el tipo.
     */
    Claims validarTokenExtraerClaims(String token, String tipoEsperado);

    /**
     * Comprueba que un refresh token ya validado (claims con jti) sigue activo server-side y lo
     * marca revocado (rotación: cada refresh consume el token usado). Si el jti no existe o ya
     * estaba revocado, se interpreta como reuso de un token robado y se revocan TODAS las
     * sesiones del usuario antes de lanzar la excepción.
     */
    void verificarYRotarRefreshToken(Claims claimsRefresh);

    /**
     * Revoca un refresh token concreto por su jti (usado en logout). No lanza si ya estaba
     * revocado o no existe: el objetivo de logout es "que quede inválido", no auditar reusos.
     */
    void revocarRefreshTokenPorJti(String jti);

    /**
     * Revoca TODAS las sesiones activas (refresh tokens) de un usuario. Usado tras un reset de
     * contraseña: si alguien más tenía sesión abierta con la contraseña antigua (robada o
     * compartida), un cambio de contraseña debe cerrarle el acceso también a él, no solo evitar
     * que vuelva a hacer login con la contraseña vieja.
     */
    void revocarTodasLasSesiones(Integer idUsuario);
}
