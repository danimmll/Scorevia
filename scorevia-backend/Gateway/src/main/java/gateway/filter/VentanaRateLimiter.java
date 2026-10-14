package gateway.filter;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contador de peticiones por clave (típicamente IP) en una ventana fija de tiempo, en
 * memoria. Extraído para que {@link AuthRateLimiterFilter} y {@link GeneralRateLimiterFilter}
 * compartan la misma lógica con límites distintos.
 * <p>
 * En memoria a propósito, igual que documenta {@link AuthRateLimiterFilter}: si el Gateway
 * llega a escalar a varias instancias, el límite deja de ser exacto entre ellas (se podría
 * alcanzar N veces el límite real). El día que eso pase, migrar a Redis + el
 * {@code RequestRateLimiter} oficial de Spring Cloud Gateway.
 * </p>
 */
class VentanaRateLimiter {

    private final int limitePeticiones;
    private final long duracionVentanaSegundos;
    private final ConcurrentHashMap<String, Ventana> ventanasPorClave = new ConcurrentHashMap<>();
    private final AtomicLong ultimoBarridoEpochSegundo = new AtomicLong(Instant.now().getEpochSecond());

    VentanaRateLimiter(int limitePeticiones, long duracionVentanaSegundos) {
        this.limitePeticiones = limitePeticiones;
        this.duracionVentanaSegundos = duracionVentanaSegundos;
    }

    boolean permitir(String clave) {
        barrerEntradasObsoletasSiToca();
        return ventanasPorClave.computeIfAbsent(clave, k -> new Ventana()).registrarPeticion();
    }

    // Sin esto ventanasPorClave crece sin límite: cada clave nueva (IP distinta) que pasa
    // por aquí se queda para siempre, aunque su ventana llevara mucho expirada — con IPs
    // rotando (u ofuscando X-Forwarded-For) es una fuga de memoria trivial de disparar.
    // Barrido perezoso, como mucho una vez por ventana, para no añadir un hilo/bean aparte
    // solo para esto.
    private void barrerEntradasObsoletasSiToca() {
        long ahora = Instant.now().getEpochSecond();
        long ultimo = ultimoBarridoEpochSegundo.get();
        if (ahora - ultimo < duracionVentanaSegundos) {
            return;
        }
        if (ultimoBarridoEpochSegundo.compareAndSet(ultimo, ahora)) {
            ventanasPorClave.values().removeIf(ventana -> ventana.estaObsoleta(ahora));
        }
    }

    private final class Ventana {
        private long inicioEpochSegundo = Instant.now().getEpochSecond();
        private int contador = 0;

        synchronized boolean registrarPeticion() {
            long ahora = Instant.now().getEpochSecond();
            if (ahora - inicioEpochSegundo >= duracionVentanaSegundos) {
                inicioEpochSegundo = ahora;
                contador = 0;
            }
            contador++;
            return contador <= limitePeticiones;
        }

        synchronized boolean estaObsoleta(long ahora) {
            return ahora - inicioEpochSegundo >= duracionVentanaSegundos * 2L;
        }
    }
}
