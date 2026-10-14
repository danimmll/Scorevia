package predicciones.services.prediction;

import predicciones.exception.BadRequestException;
import predicciones.exception.ConflictException;
import predicciones.exception.ForbiddenException;
import predicciones.exception.NotFoundException;

/**
 * Distingue, dentro de un fallback de circuit breaker, un error de negocio legítimo (partido
 * inexistente, no autorizado...) de una caída real de comunicación.
 * <p>
 * {@code Resilience4jConfig} usa {@code ignoreExceptions} para que estas excepciones tipadas
 * (ya decodificadas por {@code CustomFeignErrorDecoder} desde una respuesta real 400/403/404/409
 * de un servicio SANO) no cuenten para abrir el circuito -- pero eso solo afecta a la métrica de
 * fallos del circuito, no evita que Spring Cloud invoque igualmente el fallback de
 * {@code CircuitBreaker.run(supplier, fallback)} para CUALQUIER excepción que lance el supplier.
 * Sin esto, un fallback que siempre traduce a "sin comunicación" convertía un 404/403 real en un
 * 409 genérico, enmascarando el error de negocio real ante el cliente.
 * </p>
 */
final class CircuitBreakerFallbackSupport {

    private CircuitBreakerFallbackSupport() {
    }

    static void relanzarSiEsErrorDeNegocio(Throwable causa) {
        if (causa instanceof NotFoundException || causa instanceof BadRequestException
                || causa instanceof ForbiddenException || causa instanceof ConflictException) {
            throw (RuntimeException) causa;
        }
    }
}
