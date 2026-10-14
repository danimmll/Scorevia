package gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds the internal shared secret to every request that leaves the Gateway.
 * <p>
 * Backend microservices use this header as a defensive layer against direct
 * container access. Any client-provided value is overwritten here so external
 * callers cannot spoof the internal channel through the public Gateway.
 * </p>
 */
@Component
public class InternalSecretRelayFilter implements GlobalFilter, Ordered {

    public static final String HEADER_SECRETO_INTERNO = "X-Internal-Secret";

    @Value("${incidencias.internal-secret}")
    private String internalSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(HEADER_SECRETO_INTERNO);
                    headers.add(HEADER_SECRETO_INTERNO, internalSecret);
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
