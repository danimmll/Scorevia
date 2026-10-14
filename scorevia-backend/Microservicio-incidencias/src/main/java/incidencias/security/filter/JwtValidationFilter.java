package incidencias.security.filter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import incidencias.dto.IncidenciaDTO;
import incidencias.services.incidencias.IncidenciaServiceImpl;

@Slf4j
public class JwtValidationFilter extends BasicAuthenticationFilter {

    private static final String TYPE_ACCESS = "ACCESS";
    private static final String JWT_INVALIDO = "JWT inválido";

    private final ObjectMapper objectMapper;
    private final IncidenciaServiceImpl incidenciaService;

    public JwtValidationFilter(AuthenticationManager authenticationManager, ObjectMapper objectMapper, IncidenciaServiceImpl incidenciaService) {
        super(authenticationManager);
        this.objectMapper = objectMapper;
        this.incidenciaService = incidenciaService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws IOException, ServletException {

        if (esRutaPublica(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String token = extraerToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = Jwts.parser().verifyWith(TokenJwtConfig.SECRET_KEY).build().parseSignedClaims(token).getPayload();

            // Los REFRESH token no llevan 'authorities' y solo deben usarse para pedir un
            // nuevo ACCESS token en usuarios-roles: sin esta comprobación, un REFRESH token
            // robado (vida útil mucho más larga) serviría igualmente como Bearer aquí.
            String tipoToken = claims.get("token_type", String.class);
            if (!TYPE_ACCESS.equals(tipoToken)) {
                sendJwtError(request, response, HttpStatus.UNAUTHORIZED, "Tipo de token no válido para esta operación", "token-type-invalid", JWT_INVALIDO);
                return;
            }

            String correo = claims.get("correo", String.class);

            // 🛡️ FIX IDOR ESTANDARIZADO (Arreglado el desajuste de "idUsuarioLogueado" vs "idUsuario")
            Integer idUsuario = claims.get("idUsuario", Integer.class);
            request.setAttribute("idUsuario", idUsuario);

            List<GrantedAuthority> authorities = extraerRoles(claims.get("authorities"));

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(correo, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            chain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            sendJwtError(request, response, HttpStatus.UNAUTHORIZED, "Token expirado", "token-expired", "JWT expirado");
        } catch (MalformedJwtException e) {
            sendJwtError(request, response, HttpStatus.BAD_REQUEST, "Token mal formado", "token-malformed", JWT_INVALIDO);
        } catch (SignatureException e) {
            sendJwtError(request, response, HttpStatus.UNAUTHORIZED, "Firma inválida del token", "token-signature-invalid", JWT_INVALIDO);
        } catch (Exception e) {
            log.error("Error crítico al validar token en incidencias: {}", e.getMessage(), e);
            sendJwtError(request, response, HttpStatus.FORBIDDEN, "Error al verificar el token", "token-error", "Problema de acceso");
        }
    }

    private boolean esRutaPublica(String path) {
        return path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources") || path.startsWith("/webjars");
    }

    private String extraerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private List<GrantedAuthority> extraerRoles(Object authoritiesClaim) {
        return switch (authoritiesClaim) {
            case String authStr -> {
                try {
                    yield Arrays.asList(objectMapper.readValue(authStr, SimpleGrantedAuthority[].class));
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("No se pudieron parsear los roles del JWT", e);
                }
            }
            case Collection<?> authCollection -> authCollection.stream()
                    .map(Object::toString)
                    .map(role -> {
                        String cleanRole = role.replace("{authority=", "").replace("}", "");
                        return cleanRole.startsWith("ROLE_") ? cleanRole : "ROLE_" + cleanRole;
                    })
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            case null, default -> List.of();
        };
    }

    private void sendJwtError(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail, String type, String title) throws IOException {
        IncidenciaDTO incidencia = new IncidenciaDTO();
        incidencia.setEndpoint(request.getRequestURI());
        incidencia.setTipo(title);
        incidencia.setClase(this.getClass().getSimpleName());
        incidencia.setMetodo("sendJwtError");
        incidencia.setTraza(detail);
        incidencia.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
        incidencia.setUsuarioContexto(obtenerUsuarioActual());

        incidenciaService.crearIncidenciaDTO(incidencia);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(TokenJwtConfig.CONTENT_TYPE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private String obtenerUsuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String correo) {
            return correo;
        }
        return "Anónimo";
    }
}