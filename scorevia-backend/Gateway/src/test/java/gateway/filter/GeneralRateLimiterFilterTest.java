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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeneralRateLimiterFilterTest {

    private static final int LIMITE = 300;

    @Mock
    private org.springframework.cloud.gateway.filter.GatewayFilterChain chain;

    private GeneralRateLimiterFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GeneralRateLimiterFilter();
        lenient().when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private ServerWebExchange peticionDesde(String ip, String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .remoteAddress(new InetSocketAddress(ip, 12345))
                        .build());
    }

    @Test
    void dentroDelLimite_DejaPasarTodasLasPeticiones() {
        for (int i = 0; i < LIMITE; i++) {
            filter.filter(peticionDesde("10.1.0.1", "/api/v1/partidos/en-vivo"), chain).block();
        }

        verify(chain, times(LIMITE)).filter(any(ServerWebExchange.class));
    }

    @Test
    void porEncimaDelLimite_Devuelve429YNoLlegaAlChain() {
        String ip = "10.1.0.2";
        for (int i = 0; i < LIMITE; i++) {
            filter.filter(peticionDesde(ip, "/api/v1/partidos/en-vivo"), chain).block();
        }

        ServerWebExchange peticionExtra = peticionDesde(ip, "/api/v1/partidos/en-vivo");
        filter.filter(peticionExtra, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, peticionExtra.getResponse().getStatusCode());
        verify(chain, times(LIMITE)).filter(any(ServerWebExchange.class));
    }

    @Test
    void ipsDistintas_TienenContadoresIndependientes() {
        for (int i = 0; i < LIMITE; i++) {
            filter.filter(peticionDesde("10.1.0.3", "/api/v1/partidos/en-vivo"), chain).block();
        }

        ServerWebExchange exchangeOtraIp = peticionDesde("10.1.0.4", "/api/v1/partidos/en-vivo");
        filter.filter(exchangeOtraIp, chain).block();

        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchangeOtraIp.getResponse().getStatusCode());
        verify(chain, times(LIMITE + 1)).filter(any(ServerWebExchange.class));
    }

    @Test
    void rutaAuth_QuedaExcluidaDelLimiteGeneral() {
        // /auth tiene su propio límite más estricto en AuthRateLimiterFilter; por encima
        // del límite general (300) no debe bloquear aquí.
        for (int i = 0; i < LIMITE + 5; i++) {
            filter.filter(peticionDesde("10.1.0.5", "/api/v1/auth/login"), chain).block();
        }

        verify(chain, times(LIMITE + 5)).filter(any(ServerWebExchange.class));
    }

    @Test
    void rutaActuator_QuedaExcluidaDelLimiteGeneral() {
        for (int i = 0; i < LIMITE + 5; i++) {
            filter.filter(peticionDesde("10.1.0.6", "/actuator/health"), chain).block();
        }

        verify(chain, times(LIMITE + 5)).filter(any(ServerWebExchange.class));
    }

    @Test
    void usaLaUltimaIpDeXForwardedFor_ParaContarPorClienteReal() {
        // Dos peticiones con el mismo último salto en X-Forwarded-For deben compartir
        // contador aunque remoteAddress (el proxy) sea siempre el mismo.
        String ip = "10.1.0.7";
        for (int i = 0; i < LIMITE; i++) {
            ServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/partidos/en-vivo")
                            .header("X-Forwarded-For", "203.0.113.5, " + ip)
                            .remoteAddress(new InetSocketAddress("172.18.0.1", 12345))
                            .build());
            filter.filter(exchange, chain).block();
        }

        ServerWebExchange peticionExtra = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/partidos/en-vivo")
                        .header("X-Forwarded-For", "203.0.113.5, " + ip)
                        .remoteAddress(new InetSocketAddress("172.18.0.1", 12345))
                        .build());
        filter.filter(peticionExtra, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, peticionExtra.getResponse().getStatusCode());
    }

    @Test
    void ordenDeEjecucionEsMuyAlto() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, filter.getOrder());
    }
}
