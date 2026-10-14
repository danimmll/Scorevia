package predicciones.services.prediction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import predicciones.config.EstadisticasClient;
import predicciones.dto.FormaPrediccionDTO;
import predicciones.dto.internal.EventoPartidoDTO;
import predicciones.exception.ConflictException;
import predicciones.exception.NotFoundException;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EstadisticasFeignGatewayTest {

    @Mock
    private EstadisticasClient estadisticasClient;

    @Mock
    private CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    @Mock
    private CircuitBreaker circuitBreaker;

    @InjectMocks
    private EstadisticasFeignGateway gateway;

    @BeforeEach
    void setUp() {
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
    }

    @Test
    void obtenerContexto_CircuitoCerrado_DelegaEnElClienteFeign() {
        FormaPrediccionDTO esperado = new FormaPrediccionDTO();
        when(estadisticasClient.getContextoPrediccion(any(), anyInt())).thenReturn(esperado);
        when(circuitBreaker.run(any(), any())).thenAnswer(invocacion -> {
            Supplier<?> supplier = invocacion.getArgument(0);
            return supplier.get();
        });

        FormaPrediccionDTO resultado = gateway.obtenerContexto(10);

        assertSame(esperado, resultado);
    }

    @Test
    void obtenerContexto_CircuitoAbiertoOFalloFeign_LanzaConflictException() {
        when(circuitBreaker.run(any(), any())).thenAnswer(invocacion -> {
            Function<Throwable, ?> fallback = invocacion.getArgument(1);
            return fallback.apply(new RuntimeException("estadísticas caída"));
        });

        assertThrows(ConflictException.class, () -> gateway.obtenerContexto(10));
    }

    @Test
    void obtenerContexto_ErrorDeNegocioLegitimo_SePropagaTalCualSinEnmascarar() {
        // NotFoundException real (partido inexistente), no una caída de comunicación: el
        // fallback no debe convertirla en un ConflictException genérico.
        NotFoundException original = new NotFoundException("Partido no encontrado");
        when(circuitBreaker.run(any(), any())).thenAnswer(invocacion -> {
            Function<Throwable, ?> fallback = invocacion.getArgument(1);
            return fallback.apply(original);
        });

        NotFoundException lanzada = assertThrows(NotFoundException.class, () -> gateway.obtenerContexto(10));
        assertSame(original, lanzada);
    }

    @Test
    void obtenerEventosEnVivo_CircuitoCerrado_DelegaEnElClienteFeign() {
        List<EventoPartidoDTO> esperado = List.of(new EventoPartidoDTO());
        when(estadisticasClient.obtenerEventosPartido(any(), anyInt())).thenReturn(esperado);
        when(circuitBreaker.run(any(), any())).thenAnswer(invocacion -> {
            Supplier<?> supplier = invocacion.getArgument(0);
            return supplier.get();
        });

        List<EventoPartidoDTO> resultado = gateway.obtenerEventosEnVivo(10);

        assertSame(esperado, resultado);
    }

    @Test
    void obtenerEventosEnVivo_CircuitoAbiertoOFalloFeign_LanzaConflictException() {
        when(circuitBreaker.run(any(), any())).thenAnswer(invocacion -> {
            Function<Throwable, ?> fallback = invocacion.getArgument(1);
            return fallback.apply(new RuntimeException("estadísticas caída"));
        });

        assertThrows(ConflictException.class, () -> gateway.obtenerEventosEnVivo(10));
    }
}
