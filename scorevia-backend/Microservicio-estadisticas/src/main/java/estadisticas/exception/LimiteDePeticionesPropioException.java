package estadisticas.exception;

/**
 * No se consiguio turno en NUESTRO limitador de peticiones. La API no tiene nada que ver.
 *
 * <p>Existe para poder distinguirla de {@link ApiFootballException}, y la diferencia importa
 * mucho mas de lo que parece: el cortacircuitos cuenta los fallos de la API para dejar de
 * insistir cuando el proveedor esta caido, pero esto no es un fallo del proveedor, es nuestro
 * propio freno funcionando. Metidas en el mismo saco, un rato de cola se leia como "API-Football
 * esta caida" y abria el circuito con la API respondiendo perfectamente: el 2026-09-08 corto
 * dos recogidas seguidas y dejo 1.315 partidos sin estadisticas que la API tenia listas.</p>
 *
 * <p>Va en {@code ignore-exceptions} del cortacircuitos, y {@code ejecutarGet} la reintenta
 * esperando un poco: cuando lo que falta es turno, la respuesta correcta es esperar.</p>
 */
public class LimiteDePeticionesPropioException extends ApiFootballException {

    public LimiteDePeticionesPropioException(String message, Throwable cause) {
        super(message, cause);
    }
}
