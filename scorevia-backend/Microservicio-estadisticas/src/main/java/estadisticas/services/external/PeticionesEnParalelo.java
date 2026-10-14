package estadisticas.services.external;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pide varias cosas a API-Football a la vez en vez de una detras de otra.
 *
 * <h2>El problema que resuelve</h2>
 *
 * <p>Los rellenos iban despacio y era facil echarle la culpa a la cuota, pero no era eso.
 * Medido sobre el log del 2026-09-08: cada llamada tarda unos <b>330 ms</b> de ida y vuelta
 * contra api-sports.io, y el motor de ingesta corre en un solo hilo, asi que la siguiente no
 * sale hasta que vuelve la anterior. Eso son <b>3 peticiones por segundo</b> cuando el
 * limitador ya permitia 10 y el plan Mega permite 15. El cuello de botella era la espera de
 * red, no el limite: el 70% del tiempo el proceso estaba parado mirando el socket.</p>
 *
 * <p>En numeros redondos: los 27.123 entrenadores del relleno del palmares son 2 horas y media
 * de uno en uno, y unos 32 minutos pidiendo de ocho en ocho.</p>
 *
 * <h2>Lo que NO cambia</h2>
 *
 * <p>Cada peticion sigue pasando por {@code ApiFootballClient}, asi que el limitador, el
 * contador de cuota, los reintentos y el cortacircuitos siguen mandando igual: esto solo
 * decide cuantas hay en el aire a la vez, no cuantas se hacen ni con que reglas.</p>
 *
 * <p>Y solo se paraleliza la <b>lectura</b>. Lo que se escribe en la base sigue haciendose en
 * el hilo que pidio el relleno, en el mismo orden de siempre: el EntityManager de JPA no se
 * comparte entre hilos, y repartir los guardados habria sido cambiar un problema de velocidad
 * por uno de datos.</p>
 */
@Slf4j
@Component
public class PeticionesEnParalelo {

    /**
     * Cuantas peticiones puede haber en el aire a la vez.
     *
     * <p>Ocho, no mas: a 330 ms cada una, ocho hilos dan unas 24 peticiones por segundo y el
     * limitador esta en 14, asi que a partir de ahi los hilos de sobra solo estarian haciendo
     * cola dentro del limitador y gastando memoria.</p>
     */
    private final int hilos;

    private final ExecutorService ejecutor;

    public PeticionesEnParalelo(@Value("${api.football.hilos-lectura:8}") int hilos) {
        this.hilos = Math.max(1, hilos);
        this.ejecutor = new ThreadPoolExecutor(
                this.hilos, this.hilos, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(512),
                r -> {
                    Thread hilo = new Thread(r, "ApiFootball-lectura");
                    hilo.setDaemon(true);
                    return hilo;
                },
                // Si la cola se llena, la tarea la ejecuta quien la encolo. Descartarla seria
                // perder un item del relleno sin enterarse; esto solo lo frena.
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Ejecuta las lecturas a la vez y devuelve los resultados <b>en el mismo orden</b>.
     *
     * <p>Una lectura que falle no tumba a las demas: su hueco viene a {@code null}. Quien
     * llama ya sabia distinguir "la API no tiene ese dato" de "la peticion fallo", y esto no
     * le cambia el trato.</p>
     */
    public <T> List<T> leer(List<? extends Callable<T>> lecturas) {
        if (lecturas.isEmpty()) {
            return List.of();
        }
        // Una sola lectura, o el helper configurado a un hilo: se ejecuta aqui mismo. Ademas de
        // ahorrarse el viaje al pool, es lo que hace que los tests sean deterministas.
        // Ojo con List.of(): no admite nulls, y una lectura fallida devuelve precisamente null.
        if (lecturas.size() == 1 || hilos == 1) {
            List<T> resultados = new ArrayList<>(lecturas.size());
            for (Callable<T> lectura : lecturas) {
                resultados.add(ejecutarAqui(lectura));
            }
            return resultados;
        }

        List<Future<T>> enVuelo = new ArrayList<>(lecturas.size());
        for (Callable<T> lectura : lecturas) {
            enVuelo.add(ejecutor.submit(lectura));
        }

        List<T> resultados = new ArrayList<>(lecturas.size());
        for (Future<T> futuro : enVuelo) {
            resultados.add(esperar(futuro));
        }
        return resultados;
    }

    private <T> T ejecutarAqui(Callable<T> lectura) {
        try {
            return lectura.call();
        } catch (Exception e) {
            log.debug("Lectura de API-Football fallida: {}", e.getMessage());
            return null;
        }
    }

    private <T> T esperar(Future<T> futuro) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Cancelar lo que quede en vuelo: si el relleno se corta, no tiene sentido seguir
            // gastando cuota en peticiones cuyo resultado ya no va a leer nadie.
            futuro.cancel(true);
            return null;
        } catch (Exception e) {
            log.debug("Lectura de API-Football fallida: {}", e.getMessage());
            return null;
        }
    }

    @PreDestroy
    void cerrar() {
        ejecutor.shutdownNow();
    }
}
