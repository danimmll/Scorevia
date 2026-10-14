package estadisticas.services.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Lo que esta cola tiene que garantizar es sobre todo cuánta cuota gasta.
 *
 * <p>El caso que hay que aguantar sin despeinarse es un día con muchísimos partidos —tres mil,
 * buena parte acabando en la misma media hora—. Los partidos se piden de veinte en veinte
 * (una petición por tanda), así que lo que se fija aquí es que la ronda vaya en bloque, que no
 * se repita lo que ya se sabe imposible y que nada crezca sin tope.</p>
 */
@ExtendWith(MockitoExtension.class)
class ColaPostPartidoTest {

    @Mock private EstadisticasIngestService estadisticasIngestService;
    @Mock private ApiFootballClient apiFootballClient;

    private ColaPostPartido cola;

    /** Responde al lote diciendo lo mismo de todos los partidos pedidos. */
    private void responder(ResultadoIngestaPartido resultado) {
        when(estadisticasIngestService.ingestarLoteDePartidos(anyList())).thenAnswer(inv -> {
            List<Integer> ids = inv.getArgument(0);
            Map<Integer, ResultadoIngestaPartido> respuesta = new LinkedHashMap<>();
            ids.forEach(id -> respuesta.put(id, resultado));
            return respuesta;
        });
    }

    @BeforeEach
    void setUp() {
        cola = new ColaPostPartido(estadisticasIngestService, apiFootballClient);
        // @Value no se inyecta en un test de Mockito puro. Retardo inicial a 0 para no tener
        // que esperar cinco minutos de reloj real en cada caso.
        ReflectionTestUtils.setField(cola, "retardoInicialSegundos", 0L);
        ReflectionTestUtils.setField(cola, "esperaReintentoSegundos", 600L);
        ReflectionTestUtils.setField(cola, "maxIntentos", 3);
        ReflectionTestUtils.setField(cola, "maxPorRonda", 40);
        ReflectionTestUtils.setField(cola, "colaMaxima", 5000);
        lenient().when(apiFootballClient.hayCuotaDisponible()).thenReturn(true);
    }

    @Test
    @DisplayName("Un partido con estadísticas ya disponibles sale de la cola a la primera")
    void partidoCompletadoSaleDeLaCola() {
        responder(ResultadoIngestaPartido.OK);

        cola.encolar(1);
        int completados = cola.procesarRonda();

        assertThat(completados).isEqualTo(1);
        assertThat(cola.tamano()).isZero();
    }

    @Test
    @DisplayName("Todavía sin datos: se reintenta más tarde, no en la misma ronda")
    void sinDatosEsperaAlSiguienteTurno() {
        responder(ResultadoIngestaPartido.SIN_DATOS);

        cola.encolar(1);
        cola.procesarRonda();
        // Las rondas van cada 30 segundos; la espera del reintento es de 10 minutos, así que
        // las veinte siguientes no deben tocar la API otra vez.
        for (int i = 0; i < 20; i++) {
            cola.procesarRonda();
        }

        verify(estadisticasIngestService, times(1)).ingestarLoteDePartidos(anyList());
        assertThat(cola.tamano()).isEqualTo(1);
    }

    @Test
    @DisplayName("Competición sin cobertura: fuera de la cola sin gastar un solo reintento")
    void sinCoberturaNoSeReintenta() {
        responder(ResultadoIngestaPartido.SIN_COBERTURA);

        cola.encolar(1);
        cola.procesarRonda();

        assertThat(cola.tamano()).isZero();
        verify(estadisticasIngestService, times(1)).ingestarLoteDePartidos(anyList());
    }

    @Test
    @DisplayName("Tras agotar los intentos el partido se deja a la recogida diaria")
    void seRindeTrasLosIntentosMaximos() {
        responder(ResultadoIngestaPartido.SIN_DATOS);
        // Sin espera entre intentos para poder recorrerlos en un test.
        ReflectionTestUtils.setField(cola, "esperaReintentoSegundos", 0L);

        cola.encolar(1);
        cola.procesarRonda();
        cola.procesarRonda();
        cola.procesarRonda();

        verify(estadisticasIngestService, times(3)).ingestarLoteDePartidos(anyList());
        assertThat(cola.tamano()).isZero();
    }

    @Test
    @DisplayName("Encolar dos veces el mismo partido no retrasa su turno ni lo duplica")
    void encolarDosVecesNoReiniciaLaEspera() {
        responder(ResultadoIngestaPartido.OK);

        cola.encolar(1);
        cola.encolar(1);
        cola.encolar(1);

        assertThat(cola.tamano()).isEqualTo(1);
        assertThat(cola.procesarRonda()).isEqualTo(1);
        verify(estadisticasIngestService, times(1)).ingestarLoteDePartidos(List.of(1));
    }

    @Test
    @DisplayName("Mil partidos acabando a la vez se reparten en rondas, no se piden de golpe")
    void milPartidosSeRepartenEnRondas() {
        responder(ResultadoIngestaPartido.OK);

        for (int i = 1; i <= 1000; i++) {
            cola.encolar(i);
        }

        int completadosPrimeraRonda = cola.procesarRonda();

        // Los 40 de la ronda van en UNA llamada al servicio, que por dentro los parte en dos
        // tandas de veinte, o sea dos peticiones cada 30 segundos.
        assertThat(completadosPrimeraRonda).isEqualTo(40);
        assertThat(cola.tamano()).isEqualTo(960);
        verify(estadisticasIngestService, times(1)).ingestarLoteDePartidos(anyList());
    }

    @Test
    @DisplayName("La cola tiene tope: lo que no cabe se deja para la recogida, no se acumula")
    void colaAcotada() {
        ReflectionTestUtils.setField(cola, "colaMaxima", 100);

        for (int i = 1; i <= 500; i++) {
            cola.encolar(i);
        }

        assertThat(cola.tamano()).isEqualTo(100);
    }

    @Test
    @DisplayName("Sin cuota no se toca la API, y los partidos siguen esperando su turno")
    void sinCuotaNoGasta() {
        when(apiFootballClient.hayCuotaDisponible()).thenReturn(false);

        cola.encolar(1);
        cola.encolar(2);
        int completados = cola.procesarRonda();

        assertThat(completados).isZero();
        assertThat(cola.tamano()).isEqualTo(2);
        verify(estadisticasIngestService, never()).ingestarLoteDePartidos(anyList());
    }

    @Test
    @DisplayName("Si la ronda revienta entera, la cola se queda intacta para la siguiente")
    void unFalloNoVaciaLaCola() {
        when(estadisticasIngestService.ingestarLoteDePartidos(anyList()))
                .thenThrow(new IllegalStateException("boom"));

        cola.encolar(1);
        cola.encolar(2);
        int completados = cola.procesarRonda();

        assertThat(completados).isZero();
        assertThat(cola.tamano()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cada partido de la tanda se resuelve según lo que diga el lote de él")
    void cadaPartidoSigueSuPropioResultado() {
        when(estadisticasIngestService.ingestarLoteDePartidos(anyList())).thenReturn(Map.of(
                1, ResultadoIngestaPartido.OK,
                2, ResultadoIngestaPartido.SIN_DATOS,
                3, ResultadoIngestaPartido.SIN_COBERTURA));

        cola.encolar(1);
        cola.encolar(2);
        cola.encolar(3);
        int completados = cola.procesarRonda();

        assertThat(completados).isEqualTo(1);
        // El 1 se completó y el 3 no tiene nada que esperar; solo el 2 vuelve más tarde.
        assertThat(cola.tamano()).isEqualTo(1);
    }
}
