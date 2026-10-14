package incidencias.security.filter;

import javax.crypto.SecretKey;

/*
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

import io.jsonwebtoken.security.Keys;

public class TokenJwtConfig {

    /** Private constructor to prevent instantiation. */
    private TokenJwtConfig() {
    }

    /** Secret key string retrieved from environment or fallback value. */
    // Sin fallback hardcodeado a propósito: si falta la env var, mejor que
    // la app no arranque a que firme/valide JWTs con una clave pública del repo.
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