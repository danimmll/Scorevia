package incidencias.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.jspecify.annotations.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protección del POST /incidencias.
 * <p>
 * <b>Ojo:</b> esto NO es una barrera independiente del Gateway, pese a lo que pudiera
 * parecer. {@code InternalSecretRelayFilter} (en el Gateway) inyecta la misma cabecera
 * {@code X-Internal-Secret} en la totalidad del tráfico que reenvía hacia {@code /incidencias/**},
 * venga de otro microservicio (vía Feign, ver {@code IncidenciasClientInterceptor}) o de un
 * cliente externo cualquiera -- el secreto es idéntico en ambos casos
 * ({@code incidencias.internal-secret}). Este filtro por sí solo NO distingue una llamada
 * de servicio a servicio de una externa.
 * </p>
 * <p>
 * La única barrera real hoy contra un {@code POST /incidencias} externo es el predicate
 * {@code Method=GET,OPTIONS} de la ruta {@code modulo-incidencias-lectura} en el
 * {@code application.yml} del Gateway. Si esa ruta cambia (se añade un método de escritura
 * público, o una ruta nueva hacia incidencias sin el mismo predicate), este filtro seguirá
 * "pasando" sin darse cuenta -- porque el Gateway se habrá encargado él solo de poner la
 * cabecera. A diferencia del patrón general de {@code /api/internal/**} (ver
 * {@code docs/seguridad.md}), donde la independencia sí es real porque esas rutas no tienen
 * NINGÚN predicate de entrada en el Gateway.
 * </p>
 *
 * @author Daniel
 * @version 1.0
 */
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_SECRETO_INTERNO = "X-Internal-Secret";

    private final String secretoEsperado;

    public InternalServiceAuthFilter(String secretoEsperado) {
        this.secretoEsperado = secretoEsperado;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {

        // Coincidencia exacta o de segmento ("/api/v1/incidencias" o "/api/v1/incidencias/..."),
        // no un startsWith a secas: eso también matchearía una ruta futura como
        // "/api/v1/incidenciasFoo", que no debería quedar bajo esta protección solo por
        // compartir el prefijo textual. El prefijo "/api/v1" es el real desde la migración de
        // rutas de negocio (ver docs/back/infraestructura.md) -- antes era "/incidencias" a
        // secas, y este filtro se quedó comparando contra el path viejo: dejaba pasar sin
        // bloquear cualquier POST /api/v1/incidencias externo sin X-Internal-Secret, porque la
        // URI real nunca hacía match con la comprobación.
        String uri = request.getRequestURI();
        boolean esRutaIncidencias = "/api/v1/incidencias".equals(uri) || uri.startsWith("/api/v1/incidencias/");
        boolean esEscrituraDeIncidencias = "POST".equalsIgnoreCase(request.getMethod()) && esRutaIncidencias;

        if (esEscrituraDeIncidencias && !secretoValido(request.getHeader(HEADER_SECRETO_INTERNO))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"title\":\"Acceso restringido\",\"detail\":\"Este endpoint solo puede invocarse entre microservicios internos.\"}");
            return;
        }

        filterChain.doFilter(request, response);
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