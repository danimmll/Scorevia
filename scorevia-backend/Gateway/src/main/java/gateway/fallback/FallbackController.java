package gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Invoked by the Resilience4j {@code CircuitBreaker} gateway filter whenever a
 * downstream microservice is unreachable, too slow, or its circuit is open.
 * <p>
 * Returns a clean, machine-readable 503 instead of letting callers see a raw
 * connection/timeout exception.
 * </p>
 */
@RestController
public class FallbackController {

    // fallbackUri usa forward:/fallback, que conserva el método HTTP original de la
    // petición al microservicio caído, así que este endpoint debe aceptar cualquiera
    // de los verbos usados por las rutas del gateway.
    @RequestMapping(value = "/fallback",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.PATCH, RequestMethod.DELETE})
    public Mono<ProblemDetail> fallback() {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The requested service is temporarily unavailable. Please try again later.");
        problemDetail.setTitle("Service Unavailable");
        return Mono.just(problemDetail);
    }
}
