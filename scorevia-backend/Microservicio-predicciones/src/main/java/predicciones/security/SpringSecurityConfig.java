package predicciones.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import predicciones.security.filter.InternalServiceAuthFilter;
import predicciones.security.filter.JwtValidationFilter;

@SuppressWarnings("squid:S4502")
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SpringSecurityConfig {

    private final AuthenticationConfiguration configuration;
    private final ObjectMapper objectMapper;

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
                        // El acceso PREMIUM/ADMIN se decide dentro del servicio
                        // (PredictionServiceImpl.verificarAcceso), no aquí: esta regla solo exige
                        // estar autenticado para llegar al controlador.
                        .requestMatchers(HttpMethod.GET, "/api/v1/predicciones/**").authenticated()
                        .anyRequest().authenticated()
                )
                .build();
    }
}