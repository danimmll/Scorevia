package estadisticas.services.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.dto.external.*;
import estadisticas.exception.ApiFootballException;
import estadisticas.exception.LimiteDePeticionesPropioException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiFootballClientTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private HttpHeaders httpHeaders;

    private ApiFootballClient client;

    @BeforeEach
    void setUp() {
        client = new ApiFootballClient(objectMapper, null);
        // En producción self es el proxy de Spring inyectado de forma perezosa
        // en este test unitario no hay contexto de Spring, así que apuntamos 'self' al
        // propio objeto para conservar el comportamiento (sin aplicar realmente el AOP).
        ReflectionTestUtils.setField(client, "self", client);
        ReflectionTestUtils.setField(client, "httpClient", httpClient);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", "https://v3.football.api-sports.io");
        ReflectionTestUtils.setField(client, "reservaDiaria", 15000);
    }

    // =======================================================================
    // MÉTODO DE APOYO PARA MOCKEAR RESPUESTAS EXITOSAS LIMPIAMENTE
    // =======================================================================
    private <T> void configurarMockExitoso(T expectedObject, Class<T> clazz) throws Exception {
        configurarMock(expectedObject, clazz, "{\"errors\": [], \"response\": []}");
    }

    /**
     * Mockea una respuesta 200 con el cuerpo indicado.
     *
     * <p>El cliente ya no hace {@code readValue(body, clazz)} directo: primero lee el cuerpo
     * como arbol para poder mirar el campo {@code errors}, porque API-Football contesta
     * <b>200</b> con {@code errors: {rateLimit: ...}} y {@code response: []} cuando te pasas
     * del limite por minuto, y eso es indistinguible de "no hay datos" si no se mira.</p>
     */
    private <T> void configurarMock(T expectedObject, Class<T> clazz, String jsonBody) throws Exception {
        com.fasterxml.jackson.databind.JsonNode arbol =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonBody);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonBody);
        when(httpResponse.headers()).thenReturn(httpHeaders);
        when(httpHeaders.firstValue("x-ratelimit-requests-remaining")).thenReturn(Optional.of("4999"));
        when(objectMapper.readTree(jsonBody)).thenReturn(arbol);
        when(objectMapper.treeToValue(arbol, clazz)).thenReturn(expectedObject);
    }

    @Test
    void doscientosConErrorDeLaApi_NoSeConfundeConRespuestaVacia() throws Exception {
        // El caso que dejo al Real Madrid con 20 jugadores el 2026-09-09: la pagina 2 volvio
        // "vacia" por el limite por minuto y la sincronizacion solto a los que faltaban.
        String cuerpo = "{\"errors\": {\"rateLimit\": \"Too many requests\"}, \"response\": []}";
        com.fasterxml.jackson.databind.JsonNode arbol =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(cuerpo);
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(cuerpo);
        when(httpResponse.headers()).thenReturn(httpHeaders);
        when(httpHeaders.firstValue("x-ratelimit-requests-remaining")).thenReturn(Optional.of("4999"));
        when(objectMapper.readTree(cuerpo)).thenReturn(arbol);

        assertThrows(estadisticas.exception.ApiFootballException.class,
                () -> client.getPlayersByTeamAndSeason(541, 2026, 2));
    }

    // =======================================================================
    // TESTS DE TODOS LOS MÉTODOS DE NEGOCIO (100% COVERAGE)
    // =======================================================================

    @Test
    void getFixturesByDate_DebeRetornarDTO() throws Exception {
        ApiFootballFixturesResponseDTO expected = new ApiFootballFixturesResponseDTO();
        configurarMockExitoso(expected, ApiFootballFixturesResponseDTO.class);
        assertNotNull(client.getFixturesByDate("2026-01-01"));
    }

    @Test
    void getLiveFixtures_DebeRetornarDTO() throws Exception {
        ApiFootballFixturesResponseDTO expected = new ApiFootballFixturesResponseDTO();
        configurarMockExitoso(expected, ApiFootballFixturesResponseDTO.class);
        assertNotNull(client.getLiveFixtures());
    }

    @Test
    void getTeamStatistics_DebeRetornarDTO() throws Exception {
        ApiTeamStatsResponseDTO expected = new ApiTeamStatsResponseDTO();
        configurarMockExitoso(expected, ApiTeamStatsResponseDTO.class);
        assertNotNull(client.getTeamStatistics(1234));
    }

    @Test
    void getPlayerStatistics_DebeRetornarDTO() throws Exception {
        ApiPlayerStatsResponseDTO expected = new ApiPlayerStatsResponseDTO();
        configurarMockExitoso(expected, ApiPlayerStatsResponseDTO.class);
        assertNotNull(client.getPlayerStatistics(1234));
    }

    @Test
    void getTrophies_DebeRetornarDTO() throws Exception {
        ApiTrophiesResponseDTO expected = new ApiTrophiesResponseDTO();
        configurarMockExitoso(expected, ApiTrophiesResponseDTO.class);
        assertNotNull(client.getTrophies("10"));
    }

    @Test
    void getTrophiesByCoach_DebeRetornarDTO() throws Exception {
        ApiTrophiesResponseDTO expected = new ApiTrophiesResponseDTO();
        configurarMockExitoso(expected, ApiTrophiesResponseDTO.class);
        assertNotNull(client.getTrophiesByCoach("20"));
    }

    @Test
    void getSidelined_DebeRetornarDTO() throws Exception {
        ApiSidelinedResponseDTO expected = new ApiSidelinedResponseDTO();
        configurarMockExitoso(expected, ApiSidelinedResponseDTO.class);
        assertNotNull(client.getSidelined("10"));
    }

    @Test
    void getTransfersByTeam_DebeRetornarDTO() throws Exception {
        ApiTransfersResponseDTO expected = new ApiTransfersResponseDTO();
        configurarMockExitoso(expected, ApiTransfersResponseDTO.class);
        assertNotNull(client.getTransfersByTeam(541));
    }

    @Test
    void getTeamsByLeagueAndSeason_DebeRetornarJsonNode() throws Exception {
        JsonNode expected = mock(JsonNode.class);
        configurarMockExitoso(expected, JsonNode.class);
        assertNotNull(client.getTeamsByLeagueAndSeason(140, 2026));
    }

    @Test
    void getStandings_DebeRetornarJsonNode() throws Exception {
        JsonNode expected = mock(JsonNode.class);
        configurarMockExitoso(expected, JsonNode.class);
        assertNotNull(client.getStandings(140, 2026));
    }

    @Test
    void getCoachsByTeam_DebeRetornarJsonNode() throws Exception {
        JsonNode expected = mock(JsonNode.class);
        configurarMockExitoso(expected, JsonNode.class);
        assertNotNull(client.getCoachsByTeam(541));
    }

    @Test
    void getLeagues_DebeRetornarJsonNode() throws Exception {
        JsonNode expected = mock(JsonNode.class);
        configurarMockExitoso(expected, JsonNode.class);
        assertNotNull(client.getLeagues(2026));
    }

    @Test
    void getPlayersByTeamAndSeason_DebeRetornarJsonNode() throws Exception {
        JsonNode expected = mock(JsonNode.class);
        configurarMockExitoso(expected, JsonNode.class);
        assertNotNull(client.getPlayersByTeamAndSeason(541, 2026, 1));
    }

    @Test
    void getPlayerProfile_DebeRetornarJsonNode() throws Exception {
        JsonNode expected = mock(JsonNode.class);
        configurarMockExitoso(expected, JsonNode.class);
        assertNotNull(client.getPlayerProfile(999));
    }

    // =======================================================================
    // TESTS DE MANEJO DE ERRORES, REINTENTOS Y CUOTAS
    // =======================================================================

    @Test
    void ejecutarGet_CuandoDaErrorHTTP500_DebeReintentarYFallar() throws Exception {
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.headers()).thenReturn(httpHeaders);
        when(httpHeaders.firstValue("x-ratelimit-requests-remaining")).thenReturn(Optional.empty());

        ApiFootballException ex = assertThrows(ApiFootballException.class, () -> client.getLeagues(2026));
        assertTrue(ex.getMessage().contains("Error HTTP 500"));
        verify(httpClient, times(3)).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void ejecutarGet_CuandoDaErrorHTTP403_NoDebeReintentar() throws Exception {
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(403); // Error no transitorio
        when(httpResponse.headers()).thenReturn(httpHeaders);
        when(httpHeaders.firstValue("x-ratelimit-requests-remaining")).thenReturn(Optional.empty());

        ApiFootballException ex = assertThrows(ApiFootballException.class, () -> client.getLeagues(2026));
        assertTrue(ex.getMessage().contains("Error HTTP 403"));
        verify(httpClient, times(1)).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void ejecutarGet_CuandoLanzaIOException_DebeConvertirAApiFootballException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(new IOException("Timeout"));

        ApiFootballException ex = assertThrows(ApiFootballException.class, () -> client.getLeagues(2026));
        assertTrue(ex.getMessage().contains("Fallo de conexión"));
        verify(httpClient, times(3)).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void comprobarCuota_CuandoLlegaACero_LanzaExcepcionYBloqueaPeticiones() throws Exception {
        ApiFootballFixturesResponseDTO expected = new ApiFootballFixturesResponseDTO();
        String jsonBody = "{\"errors\": [], \"response\": []}";
        com.fasterxml.jackson.databind.JsonNode arbol =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonBody);

        when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(jsonBody);
        when(httpResponse.headers()).thenReturn(httpHeaders);
        // Simulamos que tras la petición quedan 0 créditos
        when(httpHeaders.firstValue("x-ratelimit-requests-remaining")).thenReturn(Optional.of("0"));
        when(objectMapper.readTree(jsonBody)).thenReturn(arbol);
        when(objectMapper.treeToValue(arbol, ApiFootballFixturesResponseDTO.class)).thenReturn(expected);

        // Primera petición: Funciona pero agota la cuota internamente
        client.getFixturesByDate("2026-01-01");
        assertFalse(client.hayCuotaDisponible());

        // Segunda petición: Falla de golpe sin llamar a httpClient
        ApiFootballException ex = assertThrows(ApiFootballException.class, () -> client.getFixturesByDate("2026-01-02"));
        assertTrue(ex.getMessage().contains("Límite de API-Football alcanzado por hoy"));

        // httpClient solo debió usarse la primera vez
        verify(httpClient, times(1)).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void hayCuotaDisponible_CuandoEsNull_DevuelveTrue() {
        // Al instanciar, la cuota es null (no se ha hecho ninguna petición)
        assertTrue(client.hayCuotaDisponible());
    }

    @Test
    void hayCuotaDisponibleParaBackfill_PorDebajoDelColchonReservado_DevuelveFalseAunqueQuedeCuota() {
        // Queda cuota real (500), pero por debajo del colchón reservado (15000) para
        // fixtures del día / partidos en vivo: el backfill debe parar igualmente.
        ReflectionTestUtils.setField(client, "peticionesRestantes", 500);

        assertFalse(client.hayCuotaDisponibleParaBackfill());
        assertTrue(client.hayCuotaDisponible());
    }

    @Test
    void hayCuotaDisponibleParaBackfill_PorEncimaDelColchonReservado_DevuelveTrue() {
        ReflectionTestUtils.setField(client, "peticionesRestantes", 20000);

        assertTrue(client.hayCuotaDisponibleParaBackfill());
    }

    @Test
    void ejecutarGet_DebeInvocarEnviarPeticionATravesDeSelf_NoDeThis() throws Exception {
        // Regresión Fix 1: ejecutarGet() debe llamar a enviarPeticion() a través del campo
        // 'self' (el proxy de Spring inyectado con @Lazy en producción) y no vía 'this',
        // para que @RateLimiter intercepte la llamada en vez de saltarse el AOP por
        // auto-invocación dentro de la misma clase.
        ApiFootballFixturesResponseDTO expected = new ApiFootballFixturesResponseDTO();
        configurarMockExitoso(expected, ApiFootballFixturesResponseDTO.class);

        ApiFootballClient selfSpy = spy(client);
        ReflectionTestUtils.setField(client, "self", selfSpy);

        client.getFixturesByDate("2026-01-01");

        verify(selfSpy, times(1)).enviarPeticion("/fixtures?date=2026-01-01");
    }

    @Test
    void rateLimitFallback_LanzaUnTipoPropio_NoUnFalloDeLaApi() {
        // El tipo es lo que se prueba aquí, no el texto: quedarse sin turno en nuestro propio
        // limitador va en el ignore-exceptions del cortacircuitos, y si volviera a ser una
        // ApiFootballException a secas dejaría de estar ignorada. Un rato de cola volvería a
        // leerse como "la API está caída" y abriría el circuito con la API respondiendo bien,
        // que es lo que cortó dos recogidas enteras el 2026-09-08.
        RequestNotPermitted cause = RequestNotPermitted.createRequestNotPermitted(
                io.github.resilience4j.ratelimiter.RateLimiter.ofDefaults("test"));

        LimiteDePeticionesPropioException ex = assertThrows(LimiteDePeticionesPropioException.class, () ->
                ReflectionTestUtils.invokeMethod(client, "rateLimitFallback", "/fixtures", cause)
        );

        assertTrue(ex.getMessage().contains("limitador"));
        assertEquals(cause, ex.getCause());
    }
}