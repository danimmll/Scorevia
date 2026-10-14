package estadisticas.services.external;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeticionesEnParaleloTest {

    @Test
    void devuelveLosResultadosEnElOrdenEnQueSePidieron() throws Exception {
        // Es la propiedad de la que dependen todos los rellenos: el resultado i es del item i.
        // Sin ella, los trofeos de un entrenador acabarian guardados en la ficha de otro.
        PeticionesEnParalelo enParalelo = new PeticionesEnParalelo(4);

        List<Callable<String>> lecturas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int indice = i;
            lecturas.add(() -> {
                // Las primeras tardan mas: si el orden saliera del orden de terminacion en vez
                // del de peticion, esto lo destaparia.
                Thread.sleep(indice < 5 ? 40 : 1);
                return "resultado-" + indice;
            });
        }

        List<String> resultados = enParalelo.leer(lecturas);

        assertEquals(20, resultados.size());
        for (int i = 0; i < 20; i++) {
            assertEquals("resultado-" + i, resultados.get(i));
        }
    }

    @Test
    void unaLecturaQueFallaNoTumbaALasDemas() {
        PeticionesEnParalelo enParalelo = new PeticionesEnParalelo(4);

        List<String> resultados = enParalelo.leer(List.<Callable<String>>of(
                () -> "primera",
                () -> { throw new IllegalStateException("502 de la API"); },
                () -> "tercera"));

        assertEquals(3, resultados.size());
        assertEquals("primera", resultados.get(0));
        assertNull(resultados.get(1), "la que falla deja su hueco a null, no se salta");
        assertEquals("tercera", resultados.get(2));
    }

    @Test
    void configurado_a_un_hilo_ejecuta_todas_y_en_orden() {
        // El interruptor para volver al comportamiento de antes (una detras de otra). Tiene que
        // seguir devolviendo TODAS: la primera version se dejaba las demas por el camino.
        PeticionesEnParalelo unSoloHilo = new PeticionesEnParalelo(1);

        List<String> resultados = unSoloHilo.leer(List.<Callable<String>>of(
                () -> "a",
                () -> { throw new IllegalStateException("fallo"); },
                () -> "c"));

        assertEquals(List.of("a", "c"), resultados.stream().filter(java.util.Objects::nonNull).toList());
        assertEquals(3, resultados.size());
        assertNull(resultados.get(1));
    }

    @Test
    void lanzaVariasALaVez_noUnaDetrasDeOtra() throws Exception {
        // La razon de ser de la clase. Cada lectura espera a que hayan llegado las cuatro; si
        // salieran en fila india, la primera se quedaria esperando para siempre y el latch
        // agotaria su tiempo.
        PeticionesEnParalelo enParalelo = new PeticionesEnParalelo(4);
        CountDownLatch todasDentro = new CountDownLatch(4);
        AtomicInteger llegadas = new AtomicInteger();

        List<Callable<Boolean>> lecturas = Collections.nCopies(4, () -> {
            llegadas.incrementAndGet();
            todasDentro.countDown();
            return todasDentro.await(5, TimeUnit.SECONDS);
        });

        List<Boolean> resultados = enParalelo.leer(lecturas);

        assertEquals(4, llegadas.get());
        assertTrue(resultados.stream().allMatch(Boolean.TRUE::equals),
                "las cuatro lecturas tienen que haber estado en el aire a la vez");
    }

    @Test
    void sinLecturas_noHaceNada() {
        assertEquals(List.of(), new PeticionesEnParalelo(4).leer(List.of()));
    }
}
