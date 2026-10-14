package estadisticas.security.filter;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;

/**
 * Configuracion de constantes JWT. Misma clave que el resto de microservicios
 * (variable de entorno JWT_SECRET_KEY), porque el token se emite una vez en
 * Microservicio-usuarios-roles y se valida igual en cada servicio downstream.
 *
 * @author Daniel
 * @version 1.0
 */
public class TokenJwtConfig {

    private TokenJwtConfig() {
    }

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

    public static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(ENV_SECRET.getBytes());

    public static final String PREFIX_TOKEN = "Bearer ";

    public static final String HEADER_AUTHORIZATION = "Authorization";

    public static final String CONTENT_TYPE = "application/json";
}