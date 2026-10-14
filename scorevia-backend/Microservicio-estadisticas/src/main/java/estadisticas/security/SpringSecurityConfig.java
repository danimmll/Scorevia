package estadisticas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import estadisticas.security.filter.InternalServiceAuthFilter;
import estadisticas.security.filter.JwtValidationFilter;

@SuppressWarnings("squid:S4502")
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {

    private final AuthenticationConfiguration configuration;
    private final ObjectMapper objectMapper;
    private static final String ROL_ADMIN = "ADMIN";
    private static final String ROL_PREMIUM = "PREMIUM";
    private static final String ROL_FREE = "FREE";

    @Value("${incidencias.internal-secret}")
    private String internalSecret;

    @Bean
    AuthenticationManager manager() {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) {
        return http
                .addFilterBefore(new InternalServiceAuthFilter(internalSecret), JwtValidationFilter.class)
                .addFilter(new JwtValidationFilter(manager(), objectMapper))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Sin esto, el HEALTHCHECK de docker-compose (sin JWT) caía bajo la
                        // regla genérica y fallaba con 401 siempre, aunque la app estuviera sana.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/partidos/**").hasRole(ROL_ADMIN)
                        // Va ANTES del permitAll de GET /api/v1/partidos/**: el estado de la
                        // ingesta (cuota restante, cola de partidos) es información de
                        // operación, no de producto, y no tiene por qué ser pública.
                        .requestMatchers(HttpMethod.GET, "/api/v1/partidos/estado-ingesta").hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/partidos/**",
                                "/api/v1/equipos/**",
                                "/api/v1/ligas/**",
                                "/api/v1/jugadores/**",
                                "/api/v1/clasificaciones/**",
                                "/api/v1/entrenadores/**",
                                // La ficha del árbitro es tan pública como la del equipo o la
                                // del jugador: se llega a ella desde la ficha de un partido.
                                "/api/v1/arbitros/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }
}