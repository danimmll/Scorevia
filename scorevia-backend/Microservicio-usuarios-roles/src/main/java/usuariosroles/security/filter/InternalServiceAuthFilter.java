package usuariosroles.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Defensive filter for the users/roles service.
 * <p>
 * Authentication endpoints are public at business level, but they should still
 * arrive through the Gateway. This filter rejects direct container calls unless
 * the Gateway has injected the internal shared secret.
 * </p>
 */
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_SECRETO_INTERNO = "X-Internal-Secret";

    private final String secretoEsperado;

    public InternalServiceAuthFilter(String secretoEsperado) {
        this.secretoEsperado = secretoEsperado;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (esRutaInfraestructura(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String secretoRecibido = request.getHeader(HEADER_SECRETO_INTERNO);
        if (!secretoValido(secretoRecibido)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"title\":\"Acceso restringido\",\"detail\":\"Este microservicio solo acepta tráfico interno desde el Gateway.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean esRutaInfraestructura(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")
                || path.equals("/error");
    }

    private boolean secretoValido(String secretoRecibido) {
        if (secretoRecibido == null || secretoEsperado == null) {
            return false;
        }
        return MessageDigest.isEqual(
                secretoRecibido.getBytes(StandardCharsets.UTF_8),
                secretoEsperado.getBytes(StandardCharsets.UTF_8));
    }
}
