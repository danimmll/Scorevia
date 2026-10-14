package gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InternalSecretRelayFilterTest {

    private static final String REAL_SECRET = "real-configured-secret";

    @Mock
    private org.springframework.cloud.gateway.filter.GatewayFilterChain chain;

    private InternalSecretRelayFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalSecretRelayFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", REAL_SECRET);
        lenient().when(chain.filter(org.mockito.ArgumentMatchers.any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void clientSuppliedSecretIsAlwaysOverwritten() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidencias")
                        .header(InternalSecretRelayFilter.HEADER_SECRETO_INTERNO, "spoofed-client-value")
                        .build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        var forwardedHeaders = captor.getValue().getRequest().getHeaders()
                .get(InternalSecretRelayFilter.HEADER_SECRETO_INTERNO);

        assertThat(forwardedHeaders)
                .as("only the real configured secret should reach the downstream request")
                .containsExactly(REAL_SECRET)
                .doesNotContain("spoofed-client-value");
    }

    @Test
    void configuredSecretIsInjectedWhenClientSendsNoHeader() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidencias").build());

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        var forwardedHeaders = captor.getValue().getRequest().getHeaders()
                .get(InternalSecretRelayFilter.HEADER_SECRETO_INTERNO);

        assertThat(forwardedHeaders).containsExactly(REAL_SECRET);
    }

    @Test
    void originalExchangeRequestIsLeftUntouched() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/incidencias")
                        .header(InternalSecretRelayFilter.HEADER_SECRETO_INTERNO, "spoofed-client-value")
                        .build());

        filter.filter(exchange, chain).block();

        // The original exchange passed in by the gateway is immutable; the filter
        // must build a *new* mutated exchange rather than mutate this one in place.
        assertThat(exchange.getRequest().getHeaders().get(InternalSecretRelayFilter.HEADER_SECRETO_INTERNO))
                .containsExactly("spoofed-client-value");
    }

    @Test
    void filterHasHighestPrecedenceOrder() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
