package predicciones.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Añade automáticamente la cabecera de secreto interno a toda petición que
 * el Feign client de incidencias haga, para que el filtro
 * InternalServiceAuthFilter del microservicio de incidencias las acepte.
 * <p>
 * El nombre de la cabecera ("X-Internal-Secret") está duplicado a mano aquí
 * porque cada microservicio es un artefacto Maven independiente y no
 * comparten clases; si en algún momento se añade un módulo "commons"
 * compartido, esta constante debería vivir ahí.
 * </p>
 */
@Configuration
public class IncidenciasFeignConfig {

    private static final String HEADER_SECRETO_INTERNO = "X-Internal-Secret";

    @Value("${incidencias.internal-secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> template.header(HEADER_SECRETO_INTERNO, internalSecret);
    }
}