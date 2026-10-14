package incidencias.security;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import incidencias.dto.IncidenciaDTO;
import incidencias.security.filter.InternalServiceAuthFilter;
import incidencias.security.filter.JwtValidationFilter;
import incidencias.services.incidencias.IncidenciaServiceImpl;

@SuppressWarnings("squid:S4502")
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {

    private final AuthenticationConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final IncidenciaServiceImpl incidenciaService;

    @Value("${incidencias.internal-secret}")
    private String internalSecret;

    private static final String ROL_ADMIN = "ADMIN";

    @Bean
    AuthenticationManager manager() {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) {
        return http
                .addFilterBefore(new InternalServiceAuthFilter(internalSecret), JwtValidationFilter.class)
                .addFilter(new JwtValidationFilter(manager(), objectMapper, incidenciaService))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Sin esto, el HEALTHCHECK de docker-compose (sin JWT) caía bajo
                        // anyRequest().authenticated(), fallaba con 401 SIEMPRE -- y en este
                        // módulo, además, cada 401 registra una incidencia propia
                        // (manejarAutenticacionFallida), así que habría inundado la tabla de
                        // incidencias con un "Acceso no autorizado" falso cada 10s.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/swagger-resources/**","/webjars/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidencias", "/api/v1/incidencias/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidencias", "/api/v1/incidencias/**").hasRole(ROL_ADMIN)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::manejarAutenticacionFallida)
                        .accessDeniedHandler(this::manejarAccesoDenegado)
                )
                .build();
    }

    // Extraído de la lambda inline de authenticationEntryPoint para poder testearlo con
    // Mockito puro (sin arrancar el contexto de Spring, que en este módulo no tiene
    // datasource de test configurado).
    void manejarAutenticacionFallida(HttpServletRequest request, HttpServletResponse response,
                                     AuthenticationException authException) throws IOException {
        IncidenciaDTO incidencia = new IncidenciaDTO();
        incidencia.setEndpoint(request.getRequestURI());
        incidencia.setTipo("Acceso no autorizado");
        incidencia.setClase(this.getClass().getSimpleName());
        incidencia.setMetodo("authenticationEntryPoint");
        incidencia.setTraza("Intento de acceso sin token");
        incidencia.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
        incidencia.setUsuarioContexto(obtenerUsuarioActual());
        incidenciaService.crearIncidenciaDTO(incidencia);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "No estás autenticado o falta el token");
        problem.setType(URI.create("unauthorized"));
        problem.setTitle("Autenticación requerida");
        problem.setProperty("timestamp", LocalDateTime.now(ZoneId.systemDefault()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    // Extraído de la lambda inline de accessDeniedHandler, mismo motivo que arriba.
    void manejarAccesoDenegado(HttpServletRequest request, HttpServletResponse response,
                               AccessDeniedException accessDeniedException) throws IOException {
        IncidenciaDTO incidencia = new IncidenciaDTO();
        incidencia.setEndpoint(request.getRequestURI());
        incidencia.setTipo("Acceso denegado");
        incidencia.setClase(this.getClass().getSimpleName());
        incidencia.setMetodo("accessDeniedHandler");
        incidencia.setTraza(accessDeniedException.getMessage());
        incidencia.setFecha(LocalDateTime.now(ZoneId.systemDefault()));
        incidencia.setUsuarioContexto(obtenerUsuarioActual());
        incidenciaService.crearIncidenciaDTO(incidencia);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permisos suficientes para esta acción");
        problem.setType(URI.create("forbidden-access"));
        problem.setTitle("Acceso denegado");
        problem.setProperty("timestamp", LocalDateTime.now(ZoneId.systemDefault()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private String obtenerUsuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String correo) {
            return correo;
        }
        return "Anónimo";
    }
}