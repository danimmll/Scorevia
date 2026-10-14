package predicciones.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El manejo de errores de este servicio, por lo que NO debe contestar.
 *
 * <p>Comprobado contra el despliegue vivo el 2026-09-10: {@code /api/v1/predicciones/abc} devolvia
 * <b>500</b> porque la ruta real lleva un numero y el texto no se puede convertir. Un
 * cliente que escribe mal una direccion no es un error del servidor: ensucia el log, dispara
 * alertas de monitorizacion por algo que no lo es, y le miente al que llama.</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Un id que no es un numero es 400, no 500")
    void tipoDeParametroInvalidoEs400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "catalogo", Long.class, "idSesion", null, new NumberFormatException());

        ProblemDetail problem = handler.handleMethodArgumentTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.getStatus());
        assertNotNull(problem.getDetail());
        // El mensaje dice QUE parametro y con QUE valor: sin eso, "400" no ayuda a nadie.
        assertTrue(problem.getDetail().contains("idSesion"), problem.getDetail());
        assertTrue(problem.getDetail().contains("catalogo"), problem.getDetail());
        assertNotNull(problem.getProperties());
        assertNotNull(problem.getProperties().get("timestamp"));
    }

    @Test
    @DisplayName("Lo que no esta mapeado sigue siendo 500")
    void loNoMapeadoSigueSiendo500() {
        ProblemDetail problem = handler.handleGenericException(new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
    }
}
