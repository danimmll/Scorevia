package gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Base común de los dos limitadores por IP del gateway.
 *
 * <p>{@link AuthRateLimiterFilter} y {@link GeneralRateLimiterFilter} solo se diferencian en
 * a qué rutas se aplican, con qué cupo y con qué mensaje de aviso; el resto -- resolver la IP
 * del cliente, consultar la ventana deslizante y cortar con 429 -- era el mismo cuerpo copiado
 * dos veces. Aquí vive una sola vez, y cada filtro concreto aporta únicamente su criterio de
 * ruta.</p>
 */
public abstract class RateLimiterPorIpFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterPorIpFilter.class);

    private final VentanaRateLimiter rateLimiter;

    protected RateLimiterPorIpFilter(int limitePeticionesPorVentana, long duracionVentanaSegundos) {
        this.rateLimiter = new VentanaRateLimiter(limitePeticionesPorVentana, duracionVentanaSegundos);
    }

    /** Si esta petición entra o no en el cupo de este filtro. */
    protected abstract boolean aplicaA(String path);

    /** Cómo se llama este límite en el log cuando alguien lo supera. */
    protected abstract String nombreDelLimite();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!aplicaA(path)) {
            return chain.filter(exchange);
        }

        String ip = ClienteIpResolver.resolver(exchange);
        if (!rateLimiter.permitir(ip)) {
            log.warn("Rate limit {} superado desde IP {} en {}", nombreDelLimite(), ip, path);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    /**
     * Coincidencia por segmento de ruta, no un {@code startsWith} a secas: con startsWith,
     * una ruta futura como {@code /authorized-thing} o {@code /actuatorish} caería del mismo
     * lado que {@code /auth} o {@code /actuator} solo por compartir el prefijo textual.
     */
    protected static boolean bajoPrefijo(String path, String prefijo) {
        return path.equals(prefijo) || path.startsWith(prefijo + "/");
    }
}
