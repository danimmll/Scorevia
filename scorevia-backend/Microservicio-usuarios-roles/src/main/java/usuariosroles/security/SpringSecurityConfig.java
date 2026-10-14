package usuariosroles.security;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import usuariosroles.dto.IncidenciaDTO;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.security.filter.InternalServiceAuthFilter;
import usuariosroles.security.filter.JwtAuthenticationFilter;
import usuariosroles.security.filter.JwtValidationFilter;
import usuariosroles.config.IncidenciasClient;
import usuariosroles.services.jwt.JwtTokenService;

@SuppressWarnings("squid:S4502")
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {

    private final AuthenticationConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final JwtTokenService jwtTokenService;

    // Fail-closed: ver el mismo fallback en application.yml (app.cookie.secure).
    @Value("${app.cookie.secure:true}")
    private boolean secureCookie;

    // Ver app.cookie.same-site en application.yml.
    @Value("${app.cookie.same-site:Lax}")
    private String sameSiteCookie;

    @Value("${incidencias.internal-secret}")
    private String internalSecret;

    private static final String ROL_ADMIN = "ADMIN";
    private static final String ROL_PREMIUM = "PREMIUM";
    private static final String ROL_FREE = "FREE";
    // "/**" (no solo "/api/v1/usuarios/*/roles") para cubrir también el DELETE real,
    // "/api/v1/usuarios/{id_usuario}/roles/{id_rol}" (un segmento más que el POST) — con el
    // patrón anterior ese DELETE no matcheaba ninguna regla ADMIN-only y caía en el
    // "anyRequest().authenticated()" genérico, dependiendo solo de la reautorización interna
    // de UsuarioRolServiceImpl.quitarRolUsuario como única defensa.
    private static final String RUTA_INTERMEDIA = "/api/v1/usuarios/*/roles/**";
    private static final String RUTA_ROLES = "/api/v1/roles/**";
    private static final String RUTA_FAVORITOS = "/api/v1/usuarios/*/favoritos/**";

    private final Logger logger = LoggerFactory.getLogger(SpringSecurityConfig.class);

    @Bean
    AuthenticationManager manager() {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, IncidenciasClient incidenciasClient, UsuarioRepository repository) {

        JwtAuthenticationFilter jwtAuthFilter = new JwtAuthenticationFilter(
                manager(), incidenciasClient, repository, objectMapper, secureCookie,
                sameSiteCookie, jwtTokenService);
        jwtAuthFilter.setFilterProcessesUrl("/api/v1/auth/login");

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Sin esto, el HEALTHCHECK de docker-compose (sin JWT) caía bajo la
                        // regla genérica y fallaba con 401 siempre, aunque la app estuviera sana.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verificar-email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/solicitar-reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/swagger-resources/**","/webjars/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios").hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/*").authenticated()

                        .requestMatchers(HttpMethod.GET, RUTA_ROLES).hasAnyRole(ROL_ADMIN, ROL_PREMIUM, ROL_FREE)
                        .requestMatchers("/api/v1/usuarios-roles/**").hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.POST, RUTA_ROLES).hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.PUT, RUTA_ROLES).hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, RUTA_ROLES).hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.POST, RUTA_INTERMEDIA).hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.PUT, RUTA_INTERMEDIA).hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, RUTA_INTERMEDIA).hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/usuarios/*").authenticated()

                        .requestMatchers(HttpMethod.GET, RUTA_FAVORITOS).authenticated()
                        .requestMatchers(HttpMethod.POST, RUTA_FAVORITOS).authenticated()
                        .requestMatchers(HttpMethod.DELETE, RUTA_FAVORITOS).authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios/**").hasRole(ROL_ADMIN)

                        // Tráfico exclusivamente entre microservicios (Feign, sin JWT de usuario):
                        // la única barrera real es InternalServiceAuthFilter comprobando el secreto
                        // compartido, igual que el mismo patrón en Microservicio-incidencias.
                        .requestMatchers("/api/internal/**").permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(new InternalServiceAuthFilter(internalSecret), JwtAuthenticationFilter.class)
                .addFilter(jwtAuthFilter)
                .addFilter(new JwtValidationFilter(manager(), objectMapper, incidenciasClient, repository, jwtTokenService))
                .exceptionHandling(ex -> ex
                        // Sin esto, Spring Security cae al Http403ForbiddenEntryPoint por defecto para
                        // cualquier petición sin token a una ruta protegida: un 403 con cuerpo vacío (ni
                        // el ProblemDetail uniforme que espera mensajeDeError() en el frontend, ni el 401
                        // semánticamente correcto para "sin credenciales"), y sin reportar incidencia --
                        // a diferencia de Microservicio-incidencias, que sí tiene este mismo handler.
                        .authenticationEntryPoint((request, response, authException) -> {
                            IncidenciaDTO incidenciaDTO = new IncidenciaDTO();
                            incidenciaDTO.setEndpoint(request.getRequestURI());
                            incidenciaDTO.setTipo("Acceso no autorizado");
                            incidenciaDTO.setClase(this.getClass().getSimpleName());
                            incidenciaDTO.setMetodo("authenticationEntryPoint");
                            incidenciaDTO.setTraza("Intento de acceso sin token");
                            incidenciaDTO.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
                            incidenciaDTO.setUsuarioContexto(obtenerUsuarioActual());

                            Thread.startVirtualThread(() -> {
                                try {
                                    incidenciasClient.reportarIncidencia(incidenciaDTO);
                                } catch (Exception e) {
                                    logger.error("No se pudo registrar la incidencia de seguridad vía Feign: {}", e.getMessage());
                                }
                            });

                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "No estás autenticado o falta el token");
                            problem.setType(URI.create("unauthorized"));
                            problem.setTitle("Autenticación requerida");
                            problem.setProperty("timestamp", LocalDateTime.now(ZoneId.systemDefault()));

                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.getWriter().write(objectMapper.writeValueAsString(problem));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            IncidenciaDTO incidenciaDTO = new IncidenciaDTO();
                            incidenciaDTO.setEndpoint(request.getRequestURI());
                            incidenciaDTO.setTipo("Access denied");
                            incidenciaDTO.setClase(this.getClass().getSimpleName());
                            incidenciaDTO.setMetodo("accessDeniedHandler");
                            incidenciaDTO.setTraza(accessDeniedException.getMessage());
                            incidenciaDTO.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
                            incidenciaDTO.setUsuarioContexto(obtenerUsuarioActual());

                            // Fuera del hilo de la petición: ver la misma justificación en JwtValidationFilter.
                            Thread.startVirtualThread(() -> {
                                try {
                                    incidenciasClient.reportarIncidencia(incidenciaDTO);
                                } catch (Exception e) {
                                    logger.error("No se pudo registrar la incidencia de seguridad vía Feign: {}", e.getMessage());
                                }
                            });

                            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permisos para acceder a este recurso");
                            problem.setType(URI.create("forbidden-access"));
                            problem.setTitle("Acceso al recurso restringido");
                            problem.setProperty("timestamp", LocalDateTime.now(ZoneId.systemDefault()));

                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write(objectMapper.writeValueAsString(problem));
                        }))
                .build();
    }

    private String obtenerUsuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String correo) {
            return correo;
        }
        return "Anónimo";
    }
}