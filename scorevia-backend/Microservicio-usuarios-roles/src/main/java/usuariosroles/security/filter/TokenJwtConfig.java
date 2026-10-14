package usuariosroles.security.filter;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;

/**
 * Configuration class for JWT token settings.
 * <p>
 * Provides constants and the secret key used for signing and verifying JWT tokens.
 * The secret key is retrieved from the environment variable {@code JWT_SECRET_KEY},
 * or falls back to a default value if the environment variable is not set.
 * </p>
 *
 * <p>This class is non-instantiable and only contains static members.</p>
 *
 * @author Daniel
 * @version 1.0
 */
public class TokenJwtConfig {

    /** Private constructor to prevent instantiation. */
    private TokenJwtConfig() {
    }

    /**
     * Secret key leída de la variable de entorno {@code JWT_SECRET_KEY}.
     * <p>
     * A propósito NO hay fallback hardcodeado: antes había una clave de
     * respaldo commiteada en el repo, así que si la variable de entorno
     * fallaba en desplegarse, la app arrancaba igual firmando JWTs con una
     * clave pública en GitHub. Preferimos que la app falle en el arranque
     * a que quede vulnerable en silencio.
     * </p>
     */
    private static final String ENV_SECRET = requireEnv("JWT_SECRET_KEY");

    private static String requireEnv(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno " + nombre + ": la app no puede arrancar sin ella (no hay fallback por seguridad).");
        }
        return valor;
    }

    /** SecretKey instance used for signing and verifying JWT tokens. */
    public static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(ENV_SECRET.getBytes());

    /** Prefix used in the Authorization header for JWT tokens. */
    public static final String PREFIX_TOKEN = "Bearer ";

    /** Name of the HTTP Authorization header. */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Default content type for JWT-related responses. */
    public static final String CONTENT_TYPE = "application/json";
}