package usuariosroles.security.filter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

import usuariosroles.dto.IncidenciaDTO;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.InvalidTokenTypeException;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.config.IncidenciasClient;
import usuariosroles.services.jwt.JwtTokenService;

import static usuariosroles.security.filter.TokenJwtConfig.*;

@Slf4j
public class JwtValidationFilter extends BasicAuthenticationFilter {

    private static final String TYPE_ACCESS = "ACCESS";
    private static final String JWT_INVALIDO = "JWT inválido";

    private final ObjectMapper objectMapper;
    private final IncidenciasClient incidenciasClient;
    private final UsuarioRepository usuarioRepository;
    private final JwtTokenService jwtTokenService;

    public JwtValidationFilter(AuthenticationManager authenticationManager,
                               ObjectMapper objectMapper,
                               IncidenciasClient incidenciasClient,
                               UsuarioRepository usuarioRepository,
                               JwtTokenService jwtTokenService) {
        super(authenticationManager);
        this.objectMapper = objectMapper;
        this.incidenciasClient = incidenciasClient;
        this.usuarioRepository = usuarioRepository;
        this.jwtTokenService = jwtTokenService;
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
            Claims claims = jwtTokenService.validarTokenExtraerClaims(token, TYPE_ACCESS);

            String correo = claims.get("correo", String.class);

            Optional<Usuario> usuarioBD = usuarioRepository.findByCorreoUsuario(correo);
            if (usuarioBD.isEmpty() || !Boolean.TRUE.equals(usuarioBD.get().getActivo())) {
                sendJwtError(request, response, HttpStatus.UNAUTHORIZED,
                        "Usuario inactivo o borrado", "token-user-inactive",
                        "Token inválido por usuario inactivo");
                return;
            }

            // 🛡️ FIX IDOR ESTANDARIZADO
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
        } catch (InvalidTokenTypeException e) {
            sendJwtError(request, response, HttpStatus.UNAUTHORIZED, "Tipo de token no válido para esta operación", "token-type-invalid", JWT_INVALIDO);
        } catch (Exception e) {
            log.error("Error crítico procesando el token en modulo de usuarios: {}", e.getMessage(), e);
            sendJwtError(request, response, HttpStatus.FORBIDDEN, "Error al verificar el token", "token-error", "Problema de acceso");
        }
    }

    private boolean esRutaPublica(String path) {
        if (path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/logout")) {
            return false;
        }
        return path.startsWith("/api/v1/auth") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
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

    private void sendJwtError(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                              String detail, String type, String title) throws IOException {

        String correo = "Anónimo";
        try {
            String token = extraerToken(request);
            if (token != null) {
                Claims claims = Jwts.parser().verifyWith(SECRET_KEY).build().parseSignedClaims(token).getPayload();
                correo = claims.get("correo", String.class);
            }
        } catch (Exception ignored) {
            //EXCEPCION IGNORADA SIGUE FLUJO
        }

        IncidenciaDTO incidenciaDTO = new IncidenciaDTO();
        incidenciaDTO.setEndpoint(request.getRequestURI());
        incidenciaDTO.setTipo("Token inválido");
        incidenciaDTO.setClase(this.getClass().getSimpleName());
        incidenciaDTO.setMetodo("sendJwtError");
        incidenciaDTO.setTraza(detail);
        incidenciaDTO.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
        incidenciaDTO.setUsuarioContexto(correo);

        // Fuera del hilo de la petición: un token expirado/mal formado es tráfico normal
        // (renovaciones, sesiones viejas), no algo que deba retrasar cada respuesta 401/400
        // hasta el timeout de Feign si el microservicio de incidencias está lento.
        Thread.startVirtualThread(() -> {
            try {
                incidenciasClient.reportarIncidencia(incidenciaDTO);
            } catch (Exception e) {
                log.error("Fallo al comunicar con el microservicio de incidencias: {}", e.getMessage());
            }
        });

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(CONTENT_TYPE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}