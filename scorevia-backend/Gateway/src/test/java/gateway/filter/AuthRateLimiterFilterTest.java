package gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthRateLimiterFilterTest {

    @Mock
    private org.springframework.cloud.gateway.filter.GatewayFilterChain chain;

    private AuthRateLimiterFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimiterFilter();
        lenient().when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private ServerWebExchange peticionDesde(String ip, String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .remoteAddress(new InetSocketAddress(ip, 12345))
                        .build());
    }

    @Test
    void rutaFueraDeAuth_NuncaSeLimita() {
        // 25 peticiones seguidas a una ruta que no es /auth, por encima del límite de 20/min
        for (int i = 0; i < 25; i++) {
            ServerWebExchange exchange = peticionDesde("10.0.0.1", "/api/v1/partidos/en-vivo");
            filter.filter(exchange, chain).block();
        }

        verify(chain, times(25)).filter(any(ServerWebExchange.class));
    }

    @Test
    void dentroDelLimite_DejaPasarTodasLasPeticiones() {
        for (int i = 0; i < 20; i++) {
            ServerWebExchange exchange = peticionDesde("10.0.0.2", "/api/v1/auth/login");
            filter.filter(exchange, chain).block();
        }

        verify(chain, times(20)).filter(any(ServerWebExchange.class));
    }

    @Test
    void porEncimaDelLimite_Devuelve429YNoLlegaAlChain() {
        String ip = "10.0.0.3";
        for (int i = 0; i < 20; i++) {
            filter.filter(peticionDesde(ip, "/api/v1/auth/login"), chain).block();
        }

        ServerWebExchange peticionExtra = peticionDesde(ip, "/api/v1/auth/login");
        filter.filter(peticionExtra, chain).block();

        assertThat(peticionExtra.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chain, times(20)).filter(any(ServerWebExchange.class)); // Las 20 previas, la 21ª no llega
    }

    @Test
    void ipsDistintas_TienenContadoresIndependientes() {
        for (int i = 0; i < 20; i++) {
            filter.filter(peticionDesde("10.0.0.4", "/api/v1/auth/login"), chain).block();
        }

        // Otra IP, primera petición: no debería verse afectada por el límite de la anterior.
        ServerWebExchange exchangeOtraIp = peticionDesde("10.0.0.5", "/api/v1/auth/login");
        filter.filter(exchangeOtraIp, chain).block();

        assertThat(exchangeOtraIp.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chain, times(21)).filter(any(ServerWebExchange.class));
    }

    @Test
    void sinRemoteAddress_NoRompeYDejaPasarLaPeticion() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    void ordenDeEjecucionEsMuyAlto() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }
}