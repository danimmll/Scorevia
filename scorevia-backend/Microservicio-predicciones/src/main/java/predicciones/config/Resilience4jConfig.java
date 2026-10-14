package predicciones.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import predicciones.exception.BadRequestException;
import predicciones.exception.ConflictException;
import predicciones.exception.ForbiddenException;
import predicciones.exception.NotFoundException;

import java.time.Duration;

/**
 * Configuración por defecto para todos los circuit breakers creados vía
 * {@link org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory} en este
 * servicio: protege las llamadas Feign a estadísticas ({@code EstadisticasFeignGateway}).
 * Mismos valores de
 * referencia que el circuit breaker por ruta del Gateway.
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .slowCallRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(4))
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        // CustomFeignErrorDecoder decodifica 400/401/403/404/409 a estas
                        // excepciones: son respuestas legítimas de un servicio SANO (partido
                        // inexistente, no autorizado...), no una caída. Sin esto, una ráfaga de
                        // IDs inválidos/peticiones no autorizadas podía abrir el circuito para
                        // cualquier petición durante 10s aunque estadísticas
                        // respondiera perfectamente.
                        .ignoreExceptions(NotFoundException.class, BadRequestException.class,
                                ConflictException.class, ForbiddenException.class)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(5))
                        .build())
                .build());
    }
}
