package usuariosroles.exception;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Centralized exception handler for the application.
 *
 * <p>Each method captures a specific type of error and returns a clear, structured
 * response to the client, including:</p>
 * <ul>
 *   <li>HTTP status code</li>
 *   <li>Descriptive message</li>
 *   <li>Error type</li>
 *   <li>Timestamp of occurrence</li>
 * </ul>
 *
 * <p>This helps maintain consistent error responses across the entire API.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Property name for storing the error timestamp. */
    private static final String TIEMPO = "timestamp";

    /** Tipo de ProblemDetail para los errores de petición mal formada. */
    private static final String TIPO_BAD_REQUEST = "bad-request";

    /**
     * Handles errors when a requested resource does not exist.
     *
     * @param ex Exception indicating that the resource was not found.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(codigoDe(ex, "not-found")));
		problem.setTitle("Recurso no encontrado");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles errors when attempting to create a resource that already exists.
     *
     * @param ex Exception indicating duplicate data.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(DuplicateException.class)
    public ProblemDetail handleDuplicateException(DuplicateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(409), ex.getMessage());
        problem.setType(URI.create(codigoDe(ex, "conflict-duplicate")));
		problem.setTitle("Conflicto por duplicado");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles errors when there is a conflict with an existing element.
     *
     * @param ex Exception indicating a conflict.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(codigoDe(ex, "conflict")));
		problem.setTitle("Conflicto con elemento");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles errors when the request sent is invalid.
     *
     * @param ex Exception indicating a malformed request.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequestException(BadRequestException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(TIPO_BAD_REQUEST));
		problem.setTitle("Petición mal formada");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles authorization errors when the authenticated user cannot operate on
     * the requested resource.
     *
     * @param ex Exception indicating insufficient permissions.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbiddenException(ForbiddenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create(codigoDe(ex, "forbidden")));
        problem.setTitle("Acceso denegado");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles cases where there is no information to return.
     *
     * <p>A 204 response must not carry a body (RFC 9110 §15.3.5), so unlike the other
     * handlers this one cannot return a {@link ProblemDetail} with the error message.</p>
     *
     * @return an empty {@link ResponseEntity} with status 204.
     */
    @ExceptionHandler(NoContentException.class)
    public ResponseEntity<Void> handleNoContentException() {
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles errors related to security configuration.
     *
     * @param ex Exception indicating a security configuration problem.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(SecurityConfigException.class)
    public ProblemDetail handleSecurityConfigException(SecurityConfigException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("config-problem"));
		problem.setTitle("Error al configurar la seguridad");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles unique-constraint violations that slip past the application-level
     * duplicate checks under concurrent requests (e.g. two simultaneous registrations
     * with the same email/nickname racing the check-then-act in the service layer).
     *
     * @param ex the constraint violation raised by the database.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        // Este handler es app-wide: además del registro (correo/nickname), también dispara
        // en violaciones de constraints únicos de otros recursos (p.ej. un favorito
        // duplicado), donde el mensaje de "correo o nickname" no tenía ningún sentido.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Ya existe un recurso con esos datos.");
        problem.setType(URI.create("conflict-duplicate"));
        problem.setTitle("Conflicto por duplicado");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errores.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errores);
    }

    /**
     * Re-lanza {@link AccessDeniedException} en vez de resolverla aquí mismo.
     *
     * <p>Sin ESTE handler, el catch-all {@link #handleGenericException(Exception)} de abajo
     * la interceptaría primero (la resolución de excepciones de Spring MVC actúa antes de que
     * el {@code ExceptionTranslationFilter} de Spring Security tenga ocasión), convirtiendo un
     * 403 legítimo en un 500.</p>
     *
     * <p>Pero en vez de construir aquí mismo una respuesta 403 propia (que antes dejaba un
     * acceso denegado por {@code @PreAuthorize} sin ningún rastro de auditoría, a diferencia
     * de uno denegado por regla de ruta), simplemente se relanza: al salir de
     * {@code DispatcherServlet}, la excepción sí llega al {@code ExceptionTranslationFilter}
     * del filter chain, que invoca el {@code accessDeniedHandler} configurado en
     * {@code SpringSecurityConfig} -- el mismo que ya reporta incidencia para los accesos
     * denegados por regla de ruta. Así ambos caminos (ruta y {@code @PreAuthorize}) terminan
     * en el mismo sitio, con la misma auditoría. Mismo patrón ya usado en
     * {@code Microservicio-incidencias.GlobalExceptionHandler}.</p>
     *
     * @param ex the access-denied exception raised by method security.
     * @throws AccessDeniedException siempre, para que se propague al filter chain.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void rethrowAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    /**
     * Handles a query/path parameter that Spring couldn't convert to the expected type
     * (e.g. a non-numeric {@code idUsuario}, or an invalid enum value). Sin este handler
     * caía en el catch-all genérico de abajo: 500 con traza de error, en vez de un 400
     * claro para un simple input mal formado. Mismo patrón ya usado en
     * {@code Microservicio-incidencias.GlobalExceptionHandler}.
     *
     * @param ex la excepción de conversión de tipo lanzada por Spring MVC
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String mensaje = "El parámetro '%s' tiene un valor inválido: '%s'".formatted(ex.getName(), ex.getValue());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, mensaje);
        problem.setType(URI.create(TIPO_BAD_REQUEST));
        problem.setTitle("Parámetro inválido");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles invalid pagination/query parameters (e.g. negative page, zero size) that
     * Spring Data throws as {@link IllegalArgumentException} before reaching the service
     * layer, so they surface as 400 instead of falling into the generic 500 handler below.
     *
     * @param ex the invalid-argument exception.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(TIPO_BAD_REQUEST));
        problem.setTitle("Parámetros de petición inválidos");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles requests to URLs that no controller maps.
     *
     * <p>Spring raises {@link NoResourceFoundException} for an unmapped path, and without
     * this handler it fell through to {@link #handleGenericException} and came back as a
     * 500 with a full stack trace in the log. Every typo in a URL therefore looked like the
     * service had crashed. It is a 404, so it is answered as one.</p>
     *
     * @param ex Exception indicating that no handler matched the requested path.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(NoResourceFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "No existe el recurso solicitado: " + ex.getResourcePath());
        problem.setType(URI.create("not-found"));
        problem.setTitle("Recurso no encontrado");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Handles a request whose path exists but not for the method used.
     *
     * <p>Same reason as the handler above: without it a wrong verb came back as a 500 "Ha
     * ocurrido un error inesperado", which reads as a crash instead of as "that verb is not
     * available here". It is a 405, and it now lists the methods the path does accept.</p>
     *
     * @param ex Exception indicating that the HTTP method is not supported for the path.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String permitidos = ex.getSupportedHttpMethods() == null || ex.getSupportedHttpMethods().isEmpty()
                ? "ninguno"
                : ex.getSupportedHttpMethods().toString();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo " + ex.getMethod() + " no esta permitido en esta ruta. Admite: " + permitidos);
        problem.setType(URI.create("method-not-allowed"));
        problem.setTitle("Metodo no permitido");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * Falta un parametro obligatorio en la peticion.
     *
     * <p>Sin este manejador caia en {@link #handleGenericException} y volvia como un 500
     * "Ha ocurrido un error inesperado": el cliente entendia que el servidor se habia roto
     * cuando lo que pasaba es que su peticion estaba incompleta, y el log se llenaba de trazas
     * de "Error no controlado" por cada llamada mal formada. Es la misma clase de fallo que el
     * 405 arreglado el 2026-09-06 y se colo igual; medido el 2026-09-08,
     * {@code GET /api/v1/equipos/liga/140} sin {@code temporada} devolvia 500.</p>
     *
     * <p>Es un 400, y dice QUE parametro falta y de que tipo, que es lo que el cliente
     * necesita para arreglarlo.</p>
     *
     * @param ex excepcion con el nombre y el tipo del parametro ausente.
     * @return {@link ProblemDetail} con la informacion del error.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Falta el parametro obligatorio '" + ex.getParameterName() + "' (" + ex.getParameterType() + ").");
        problem.setType(URI.create("missing-parameter"));
        problem.setTitle("Falta un parametro obligatorio");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }


    /**
     * Catch-all handler for any exception not explicitly mapped above.
     *
     * <p>Ensures unmapped exceptions never fall through to Spring's default error body,
     * keeping the response shape consistent with the rest of this class.</p>
     *
     * @param ex the unmapped exception.
     * @return {@link ProblemDetail} containing error information.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        logger.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado");
        problem.setType(URI.create("internal-server-error"));
        problem.setTitle("Error interno del servidor");
        problem.setProperty(TIEMPO, Instant.now());
        return problem;
    }

    /**
     * El codigo fino de la excepcion, o el generico si no trae ninguno.
     *
     * <p>Es lo que viaja en el `type` del ProblemDetail y lo que el front usa para traducir
     * el mensaje. Sin esto, todos los conflictos se veian igual en ingles.</p>
     */
    private String codigoDe(Object ex, String porDefecto) {
        if (ex instanceof ConCodigo conCodigo && conCodigo.getCodigo() != null) {
            return conCodigo.getCodigo();
        }
        return porDefecto;
    }
}
