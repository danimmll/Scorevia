package usuariosroles.exception;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rethrowAccessDenied_DeberiaPropagarLaExcepcion() {
        // Confirma que un fallo de @PreAuthorize sigue llegando al accessDeniedHandler del
        // filter chain (SpringSecurityConfig) -- que reporta incidencia -- en vez de quedarse
        // resuelto aquí mismo sin dejar rastro de auditoría.
        AccessDeniedException ex = new AccessDeniedException("Denegado");

        assertThrows(AccessDeniedException.class, () -> handler.rethrowAccessDenied(ex));
    }
}
