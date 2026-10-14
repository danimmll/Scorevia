package incidencias.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleGenericException_DeberiaDevolverInternalServerError() {
        ProblemDetail problem = handler.handleGenericException(new RuntimeException("boom"));

        assertNotNull(problem);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertNotNull(problem.getProperties());
        assertNotNull(problem.getProperties().get("timestamp"));
    }

    @Test
    void rethrowAccessDenied_DeberiaPropagarLaExcepcion() {
        AccessDeniedException ex = new AccessDeniedException("Denegado");

        assertThrows(AccessDeniedException.class, () -> handler.rethrowAccessDenied(ex));
    }
}
