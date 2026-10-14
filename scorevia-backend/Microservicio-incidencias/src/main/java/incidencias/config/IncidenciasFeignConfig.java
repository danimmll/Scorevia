package incidencias.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mismo patrón de secreto compartido que los FeignConfig de los demás microservicios:
 * inyecta X-Internal-Secret en toda llamada Feign saliente de este módulo, para que el
 * InternalServiceAuthFilter del lado receptor (usuarios-roles) la acepte.
 */
@Configuration
public class IncidenciasFeignConfig {

    @Value("${incidencias.internal-secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> template.header("X-Internal-Secret", internalSecret);
    }
}
