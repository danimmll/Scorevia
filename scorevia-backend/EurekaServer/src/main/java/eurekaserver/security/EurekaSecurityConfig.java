package eurekaserver.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El dashboard y la API REST de registro de Eureka no llevan protección propia por
 * defecto: hoy solo están a salvo porque docker-compose publica el puerto en
 * 127.0.0.1. Esto añade Basic Auth como defensa en profundidad, para que un cambio de
 * topología de despliegue (p. ej. exponerlo sin querer en AWS) no deje la topología
 * completa del clúster (IPs internas, puertos, nombres de servicio) abierta a cualquiera.
 * <p>
 * {@code /actuator/health} queda fuera de la autenticación a propósito: el healthcheck
 * de docker-compose (y cualquier orquestador equivalente) lo consulta sin credenciales,
 * y todos los demás servicios dependen de ese healthcheck para arrancar en orden
 * ({@code depends_on: condition: service_healthy}).
 * </p>
 */
@SuppressWarnings("squid:S4502")
@Configuration
public class EurekaSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) {
        return http
                // Los clientes Eureka (Gateway y microservicios) se registran vía POST/PUT/DELETE
                // autenticados con Basic Auth, no con una sesión de navegador respaldada por
                // cookie: no hay token CSRF que puedan mandar, así que sin desactivarlo aquí
                // Spring Security rechazaría con 403 cualquier registro/renovación de instancia.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
