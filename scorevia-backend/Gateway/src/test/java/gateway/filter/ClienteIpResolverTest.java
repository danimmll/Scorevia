package gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClienteIpResolverTest {

    @Test
    void conXForwardedFor_DevuelveElUltimoValor() {
        // El proxy de confianza añade la IP real del cliente al FINAL de la cabecera; los
        // valores anteriores pueden haberlos puesto el propio cliente para suplantarla.
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/partidos")
                        .header("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 3.3.3.3")
                        .remoteAddress(new InetSocketAddress("172.18.0.1", 12345))
                        .build());

        assertEquals("3.3.3.3", ClienteIpResolver.resolver(exchange));
    }

    @Test
    void conXForwardedForUnSoloValor_DevuelveEseValor() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/partidos")
                        .header("X-Forwarded-For", "9.9.9.9")
                        .build());

        assertEquals("9.9.9.9", ClienteIpResolver.resolver(exchange));
    }

    @Test
    void conCabeceraMalformadaConComaFinal_SaltaHuecosYDevuelveElUltimoNoVacio() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/partidos")
                        .header("X-Forwarded-For", "1.1.1.1, 2.2.2.2,")
                        .build());

        assertEquals("2.2.2.2", ClienteIpResolver.resolver(exchange));
    }

    @Test
    void sinXForwardedFor_CaeARemoteAddress() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/partidos")
                        .remoteAddress(new InetSocketAddress("192.168.1.50", 12345))
                        .build());

        assertEquals("192.168.1.50", ClienteIpResolver.resolver(exchange));
    }

    @Test
    void sinXForwardedForNiRemoteAddress_DevuelveDesconocida() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/partidos").build());

        assertEquals("desconocida", ClienteIpResolver.resolver(exchange));
    }
}
