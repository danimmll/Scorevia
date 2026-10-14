package predicciones.services.prediction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import predicciones.config.EstadisticasClient;
import predicciones.dto.FormaPrediccionDTO;
import predicciones.dto.internal.EventoPartidoDTO;
import predicciones.exception.ConflictException;

import java.util.List;

/**
 * Punto único de acceso a las llamadas Feign hacia estadísticas usadas por las
 * predicciones en vivo, con caché de corta duración (ver {@link predicciones.config.CacheConfig})
 * y circuit breaker: sin esto, una caída sostenida de estadísticas hacía que cada sondeo de
 * cada usuario reintentara la llamada completa (hasta ~5s de timeout cada vez, multiplicado
 * por todos los usuarios mirando partidos en vivo a la vez).
 * <p>
 * Es un bean aparte (no métodos privados de {@link PredictionServiceImpl}) a propósito:
 * {@code @Cacheable} es un proxy de Spring que no intercepta llamadas internas
 * (self-invocation) dentro de la misma clase.
 * </p>
 * <p>
 * Circuit breaker vía la abstracción de Spring Cloud ({@link CircuitBreakerFactory}, con
 * {@code spring-cloud-starter-circuitbreaker-resilience4j} gestionado por el mismo BOM de
 * Spring Cloud que el resto del proyecto) en vez de la anotación propia de Resilience4j, para
 * no añadir una dependencia con versión sin gestionar por ningún BOM ya importado.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstadisticasFeignGateway {

    private static final String CIRCUIT_ESTADISTICAS = "estadisticasFeign";

    private final EstadisticasClient estadisticasClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Value("${incidencias.internal-secret}")
    private String internalSecret;

    // sync = true: sin esto, cuando la entrada expira, varias peticiones concurrentes para el
    // mismo partido (varios usuarios viendo el mismo partido en vivo) fallan el caché a la vez
    // y cada una dispara su propia llamada Feign redundante -- justo lo que este caché existe
    // para evitar, según el propio javadoc de la clase.
    @Cacheable(value = "contextoPrediccionLive", key = "#idPartido", sync = true)
    public FormaPrediccionDTO obtenerContexto(Integer idPartido) {
        return circuitBreakerFactory.create(CIRCUIT_ESTADISTICAS).run(
                () -> estadisticasClient.getContextoPrediccion(internalSecret, idPartido),
                causa -> fallbackContexto(idPartido, causa));
    }

    // Sin cachear a propósito: detectar una tarjeta roja recién sacada tiene que reflejarse
    // cuanto antes en la predicción en vivo, no esperar al TTL de la caché de contexto.
    public List<EventoPartidoDTO> obtenerEventosEnVivo(Integer idPartido) {
        return circuitBreakerFactory.create(CIRCUIT_ESTADISTICAS).run(
                () -> estadisticasClient.obtenerEventosPartido(internalSecret, idPartido),
                causa -> fallbackEventos(idPartido, causa));
    }

    private FormaPrediccionDTO fallbackContexto(Integer idPartido, Throwable causa) {
        CircuitBreakerFallbackSupport.relanzarSiEsErrorDeNegocio(causa);
        log.error("Circuito abierto o fallo de comunicación al traer el contexto de predicción del partido {}: {}",
                idPartido, causa.getMessage());
        throw new ConflictException("Sin comunicación con estadísticas.");
    }

    private List<EventoPartidoDTO> fallbackEventos(Integer idPartido, Throwable causa) {
        CircuitBreakerFallbackSupport.relanzarSiEsErrorDeNegocio(causa);
        log.error("Circuito abierto o fallo de comunicación al traer eventos en vivo del partido {}: {}",
                idPartido, causa.getMessage());
        throw new ConflictException("Sin comunicación con estadísticas.");
    }
}
