package gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Rate limiting en memoria por IP para el resto de rutas públicas (partidos, ligas,
 * ranking, tienda, predicciones, etc.), que hasta ahora no tenían ningún límite de
 * peticiones más allá del circuit breaker de cada ruta (que protege al backend de
 * saturarse, pero no evita abuso/scraping de un único cliente).
 * <p>
 * Límite deliberadamente generoso: pensado para frenar scraping/abuso básico de un solo
 * IP, no para acotar el uso normal de la app (un usuario con la app abierta puede generar
 * fácilmente varias peticiones por segundo con las actualizaciones en vivo).
 * {@code /api/v1/auth/**} tiene su propio límite más estricto en {@link AuthRateLimiterFilter}
 * y queda excluido de este para no duplicar contadores sobre la misma ruta.
 * </p>
 */
@Component
public class GeneralRateLimiterFilter extends RateLimiterPorIpFilter {

    private static final String RUTA_AUTH = "/api/v1/auth";
    private static final String RUTA_ACTUATOR = "/actuator";
    private static final int LIMITE_PETICIONES_POR_VENTANA = 300;
    private static final long DURACION_VENTANA_SEGUNDOS = 60;

    public GeneralRateLimiterFilter() {
        super(LIMITE_PETICIONES_POR_VENTANA, DURACION_VENTANA_SEGUNDOS);
    }

    @Override
    protected boolean aplicaA(String path) {
        return !bajoPrefijo(path, RUTA_AUTH) && !bajoPrefijo(path, RUTA_ACTUATOR);
    }

    @Override
    protected String nombreDelLimite() {
        return "general";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
