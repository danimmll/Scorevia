package estadisticas.exception;

/**
 * Error al llamar a API-Football (HTTP distinto de 200, timeout, JSON invalido...).
 * Separada de {@link estadisticas.exception.BadRequestException}
 * porque el problema aqui es del proveedor externo, no de la peticion del cliente.
 *
 * @author Daniel
 * @version 1.0
 */
public class ApiFootballException extends RuntimeException {

    public ApiFootballException(String message) {
        super(message);
    }

    public ApiFootballException(String message, Throwable cause) {
        super(message, cause);
    }
}
