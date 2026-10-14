package estadisticas.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tests for {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Una URL que no mapea ningún controlador responde 404, no 500")
    void urlSinControladorDevuelve404() {
        // Antes esto caía en el catch-all y salía como 500 "Ha ocurrido un error
        // inesperado" con traza completa, así que cualquier ruta mal escrita parecía
        // que el servicio se había caído.
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "api/v1/equipos", "api/v1/equipos");

        ProblemDetail problem = handler.handleNoResourceFoundException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Recurso no encontrado");
        assertThat(problem.getDetail()).contains("api/v1/equipos");
    }

    @Test
    @DisplayName("Una excepción sin mapear sigue siendo 500 y no filtra el mensaje interno")
    void excepcionSinMapearDevuelve500() {
        ProblemDetail problem = handler.handleGenericException(
                new IllegalStateException("password=hunter2 en el pool de conexiones"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("Ha ocurrido un error inesperado");
        assertThat(problem.getDetail()).doesNotContain("hunter2");
    }

    @Test
    @DisplayName("Una petición a la que le falta un parámetro obligatorio responde 400, no 500")
    void parametroObligatorioAusenteDevuelve400() {
        // Medido contra el servicio desplegado el 2026-09-08: GET /api/v1/equipos/liga/140 sin
        // "temporada" contestaba 500 "Ha ocurrido un error inesperado" y dejaba una traza de
        // "Error no controlado" en el log. La petición estaba incompleta, no el servidor roto.
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("temporada", "String");

        ProblemDetail problem = handler.handleMissingRequestParameter(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        // El nombre del parámetro tiene que salir: sin él, el cliente sabe que se equivocó
        // pero no en qué.
        assertThat(problem.getDetail()).contains("temporada").contains("String");
        assertThat(problem.getTitle()).isEqualTo("Falta un parametro obligatorio");
    }
}
