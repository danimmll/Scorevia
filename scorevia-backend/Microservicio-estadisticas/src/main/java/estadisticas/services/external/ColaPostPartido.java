package estadisticas.services.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los partidos que acaban de terminar y a los que aún les faltan las estadísticas.
 *
 * <p><b>Para qué.</b> El marcador y los eventos ya llegan solos mientras el partido se juega
 * (el scheduler en vivo baja el timeline cada vez que se mueve el resultado), pero las
 * estadísticas de equipo y de jugadores —posesión, tiros, minutos, notas, y con ellas las
 * alineaciones que pinta la ficha del partido— viven en otros dos endpoints que solo tienen
 * sentido pedir una vez acabado. Hasta ahora eso lo hacía un script a mano. Esto lo hace en
 * cuanto suena el pitido final.</p>
 *
 * <p><b>Por qué con espera y no al instante.</b> API-Football tarda unos minutos en publicar
 * las estadísticas definitivas: preguntar en el mismo segundo del pitido devuelve vacío y
 * gasta el intento igual. Por eso cada partido entra en la cola con una hora a la que estará
 * listo, y si al llegar no hay datos vuelve más tarde, esperando cada vez un poco más.</p>
 *
 * <p><b>Por qué está acotada.</b> El peor día imaginable —tres mil partidos acabando el mismo
 * día, buena parte de ellos en la misma media hora— no puede convertirse en un mapa que crece
 * sin freno ni en una ronda interminable. De ahí las tres cotas: tamaño máximo de la cola,
 * tope de partidos por ronda e intentos máximos por partido. En peticiones ya duele poco,
 * porque cada una cubre veinte partidos. Lo que se
 * quede fuera no se pierde: la recogida diaria
 * ({@link EstadisticasIngestService#ingestarEstadisticasRecientes}) barre después lo que
 * falte, y es también la que cubre los reinicios, porque esta cola vive en memoria.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Slf4j
@Component
public class ColaPostPartido {

    /** Un partido esperando su turno. */
    private record Pendiente(Integer idPartido, Instant listoEn, int intentos) { }

    private final Map<Integer, Pendiente> pendientes = new ConcurrentHashMap<>();

    private final EstadisticasIngestService estadisticasIngestService;
    private final ApiFootballClient apiFootballClient;

    /**
     * Cuánto se espera desde el pitido final antes del primer intento. Cinco minutos es el
     * término medio entre pedirlo demasiado pronto (respuesta vacía, intento gastado) y hacer
     * esperar al usuario que entra en la ficha justo al acabar el partido.
     */
    @Value("${scorevia.ingesta.post-partido.retardo-inicial-segundos:300}")
    private long retardoInicialSegundos = 300L;

    /**
     * Espera base entre reintentos, que se multiplica por el número de intento: 10, 20 y 30
     * minutos con los valores por defecto. Un partido cuyas estadísticas no aparecen en la
     * primera media hora no las va a tener por insistir cada minuto.
     */
    @Value("${scorevia.ingesta.post-partido.espera-reintento-segundos:600}")
    private long esperaReintentoSegundos = 600L;

    @Value("${scorevia.ingesta.post-partido.max-intentos:3}")
    private int maxIntentos = 3;

    /**
     * Tope de partidos atendidos en cada ronda. Al ir de veinte en veinte por petición, 40
     * partidos son 2 peticiones cada 30 segundos, y aun así se vacían 3.000 partidos en poco
     * más de media hora aunque acabaran todos a la vez. El tope sigue existiendo para no
     * meter una ronda eterna delante de las demás, no por cuota.
     */
    @Value("${scorevia.ingesta.post-partido.max-por-ronda:40}")
    private int maxPorRonda = 40;

    /** Tope de la cola. Lo que no quepa lo recoge la pasada diaria. */
    @Value("${scorevia.ingesta.post-partido.cola-maxima:5000}")
    private int colaMaxima = 5000;

    public ColaPostPartido(EstadisticasIngestService estadisticasIngestService,
                           ApiFootballClient apiFootballClient) {
        this.estadisticasIngestService = estadisticasIngestService;
        this.apiFootballClient = apiFootballClient;
    }

    /**
     * Apunta un partido que acaba de terminar.
     *
     * <p>Idempotente: si ya estaba encolado no se reinicia su espera ni se pierde el número
     * de intentos que llevara. Un partido puede aparecer varias veces como recién acabado —el
     * feed en vivo lo sigue listando un rato después del final—, y sin esto cada aparición
     * retrasaría su turno otros cinco minutos, dejándolo en la cola para siempre.</p>
     */
    public void encolar(Integer idPartido) {
        if (idPartido == null) return;

        if (!pendientes.containsKey(idPartido) && pendientes.size() >= colaMaxima) {
            log.warn("Cola post-partido llena ({} partidos): {} queda para la recogida diaria.",
                    colaMaxima, idPartido);
            return;
        }

        Pendiente nuevo = new Pendiente(idPartido, Instant.now().plusSeconds(retardoInicialSegundos), 0);
        if (pendientes.putIfAbsent(idPartido, nuevo) == null) {
            log.info("Partido {} terminado: sus estadísticas se pedirán en {}s.", idPartido, retardoInicialSegundos);
        }
    }

    /**
     * Atiende los partidos a los que ya les ha llegado el turno.
     *
     * @return cuántos se completaron en esta ronda.
     */
    public int procesarRonda() {
        if (pendientes.isEmpty()) {
            return 0;
        }

        Instant ahora = Instant.now();
        List<Pendiente> turno = pendientes.values().stream()
                .filter(p -> !p.listoEn().isAfter(ahora))
                // Por orden de llegada: si hay más de los que caben en una ronda, primero los
                // que llevan esperando más, no los que el mapa devuelva antes.
                .sorted(Comparator.comparing(Pendiente::listoEn))
                .limit(maxPorRonda)
                .toList();

        if (turno.isEmpty()) {
            return 0;
        }

        if (!apiFootballClient.hayCuotaDisponible()) {
            log.warn("Cola post-partido en pausa por cuota: {} partidos siguen esperando.", pendientes.size());
            return 0;
        }

        Map<Integer, ResultadoIngestaPartido> resultados;
        try {
            // Una llamada para toda la ronda: el servicio la parte en tandas de 20 partidos,
            // que es lo que admite la API por petición.
            resultados = estadisticasIngestService.ingestarLoteDePartidos(
                    turno.stream().map(Pendiente::idPartido).toList());
        } catch (Exception e) {
            // Que la ronda reviente no puede vaciar la cola: se quedan todos donde estaban y
            // se vuelve a intentar en la siguiente.
            log.warn("Error atendiendo la ronda de la cola post-partido: {}", e.getMessage());
            return 0;
        }

        int completados = 0;
        for (Pendiente p : turno) {
            if (anotarResultado(p, resultados.get(p.idPartido()))) {
                completados++;
            }
        }
        return completados;
    }

    /** @return true si el partido quedó completado con sus datos. */
    private boolean anotarResultado(Pendiente p, ResultadoIngestaPartido resultado) {
        if (resultado == null) {
            // No debería pasar: el servicio devuelve una entrada por cada id pedido. Si pasa,
            // se trata como un fallo y se vuelve más tarde.
            reprogramar(p);
            return false;
        }

        switch (resultado) {
            case OK -> {
                pendientes.remove(p.idPartido());
                log.info("Partido {} completado tras el pitido final (intento {}).", p.idPartido(), p.intentos() + 1);
                return true;
            }
            case SIN_COBERTURA, NO_ENCONTRADO -> {
                // Nada que esperar: ni la competición publica estadísticas ni el partido existe.
                pendientes.remove(p.idPartido());
                return false;
            }
            default -> {
                reprogramar(p);
                return false;
            }
        }
    }

    private void reprogramar(Pendiente p) {
        int intentos = p.intentos() + 1;
        if (intentos >= maxIntentos) {
            log.info("Partido {}: sin estadísticas tras {} intentos, se deja a la recogida diaria.",
                    p.idPartido(), intentos);
            pendientes.remove(p.idPartido());
            return;
        }

        Instant proximo = Instant.now().plus(Duration.ofSeconds(esperaReintentoSegundos * intentos));
        pendientes.put(p.idPartido(), new Pendiente(p.idPartido(), proximo, intentos));
    }

    /** Cuántos partidos esperan ahora mismo. Para logs y para el endpoint de estado. */
    public int tamano() {
        return pendientes.size();
    }
}
