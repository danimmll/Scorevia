package estadisticas.services.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.dto.external.*;
import estadisticas.exception.ApiFootballException;
import estadisticas.exception.LimiteDePeticionesPropioException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.stream.Collectors;

@Component
public class ApiFootballClient {

    private static final Logger logger = LoggerFactory.getLogger(ApiFootballClient.class);
    private static final int MAX_INTENTOS = 3;
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(15);
    private static final String CONST_API_KEY = "x-apisports-key";

    // FIX SonarQube [java:S1192]: Constante para el parámetro de temporada
    private static final String CONST_SEASON = "&season=";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // FIX auto-invocación: @RateLimiter en enviarPeticion() no se aplicaba porque
    // ejecutarGet() la llamaba vía 'this' dentro de la misma clase, saltándose el
    // proxy de Spring AOP (mismo problema documentado y resuelto en
    // EventoPartidoIngestServiceImpl). Se inyecta el propio bean de forma perezosa
    // para que las llamadas internas pasen por el proxy y el rate limiter se aplique.
    private final ApiFootballClient self;

    @Value("${api.football.key}")
    private String apiKey;

    @Value("${api.football.base-url}")
    private String baseUrl;

    @Value("${api.football.reserva-diaria:25000}")
    private int reservaDiaria;

    // Suelo duro: por debajo de esto no se gasta NADA, ni siquiera la ingesta en vivo.
    // Es el colchón que queda para poder diagnosticar, relanzar algo a mano o cubrir un
    // pico imprevisto sin que el día se haya quedado ya sin una sola petición.
    @Value("${api.football.reserva-intocable:5000}")
    private int reservaIntocable;

    private volatile Integer peticionesRestantes = null;
    private volatile LocalDate ultimaFechaReset = LocalDate.now(ZoneId.of("UTC"));

    public ApiFootballClient(ObjectMapper objectMapper, @Lazy ApiFootballClient self) {
        this.objectMapper = objectMapper;
        this.self = self;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private synchronized void comprobarCuota() {
        if (peticionesRestantes != null && peticionesRestantes <= reservaIntocable) {
            LocalDate hoyUTC = LocalDate.now(ZoneId.of("UTC"));

            if (hoyUTC.isEqual(ultimaFechaReset)) {
                logger.warn("Cuota de API-Football en el suelo duro ({} restantes, reserva intocable {}).",
                        peticionesRestantes, reservaIntocable);
                throw new ApiFootballException(
                        "Límite de API-Football alcanzado por hoy. Se saltarán las peticiones pendientes.");
            }

            peticionesRestantes = null;
            ultimaFechaReset = hoyUTC;
        }
    }

    private synchronized void actualizarCuotaDesdeHeaders(HttpResponse<String> response) {
        response.headers().firstValue("x-ratelimit-requests-remaining").ifPresent(valor -> {
            try {
                peticionesRestantes = Integer.parseInt(valor);
                ultimaFechaReset = LocalDate.now(ZoneId.of("UTC"));
                logger.info("Peticiones restantes API-Football: {}", peticionesRestantes);
            } catch (NumberFormatException ignored) {
                // Ignorado
            }
        });
    }

    // Para lo urgente (ingesta del día y partidos en vivo). Ni siquiera esto baja del
    // suelo duro: si se llega ahí, es que algo se ha desmadrado y conviene que pare solo.
    public synchronized boolean hayCuotaDisponible() {
        return hayCuotaDisponible(reservaIntocable);
    }

    // Los jobs de backfill/mantenimiento (perfiles pendientes, entidades secundarias,
    // eventos históricos...) no son urgentes: pueden parar un poco antes de agotar la
    // cuota por completo, dejando un colchón reservado para la ingesta del día y partidos
    // en vivo, que si son prioritarios. Sin esto, un backfill largo corriendo antes podía
    // dejar la cuota a 0 justo cuando hiciera falta para lo importante.
    public synchronized boolean hayCuotaDisponibleParaBackfill() {
        return hayCuotaDisponible(reservaDiaria);
    }

    /** Peticiones que quedan hoy según la última cabecera vista, o null si aún no se sabe. */
    public synchronized Integer getPeticionesRestantes() {
        return peticionesRestantes;
    }

    private synchronized boolean hayCuotaDisponible(int colchonReservado) {
        if (peticionesRestantes == null) {
            return true;
        }
        LocalDate hoyUTC = LocalDate.now(ZoneId.of("UTC"));
        if (!hoyUTC.isEqual(ultimaFechaReset)) {
            return true;
        }
        return peticionesRestantes > colchonReservado;
    }

    // =======================================================================
    // MÉTODOS DE NEGOCIO
    // =======================================================================

    public ApiFootballFixturesResponseDTO getFixturesByDate(String date) {
        return ejecutarGet("/fixtures?date=" + date, ApiFootballFixturesResponseDTO.class, "Fixtures por fecha " + date);
    }

    public ApiFootballFixturesResponseDTO getLiveFixtures() {
        return ejecutarGet("/fixtures?live=all", ApiFootballFixturesResponseDTO.class, "Fixtures en vivo");
    }

    /** Tope que impone API-Football al parámetro {@code ids} de /fixtures. */
    public static final int MAX_FIXTURES_POR_PETICION = 20;

    /**
     * Hasta 20 partidos <b>con todo dentro</b> en una sola petición.
     *
     * <p>Al pedir fixtures por id, la respuesta incluye además eventos, alineaciones,
     * estadísticas de equipo y estadísticas de jugador de cada uno. Es la diferencia entre
     * gastar tres peticiones por partido y gastar una por cada veinte: para los 42.000
     * partidos que hoy están pendientes de estadísticas, 85.000 peticiones frente a 2.100.</p>
     *
     * @param ids entre 1 y {@value #MAX_FIXTURES_POR_PETICION} ids de partido.
     */
    public ApiFootballFixturesResponseDTO getFixturesByIds(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Hay que pedir al menos un partido");
        }
        if (ids.size() > MAX_FIXTURES_POR_PETICION) {
            // Mejor fallar aquí que gastar la petición: la API ignora los ids que sobran sin
            // avisar, y los partidos de más quedarían marcados como "sin datos" sin haber
            // llegado a preguntarse por ellos.
            throw new IllegalArgumentException(
                    "API-Football admite como mucho " + MAX_FIXTURES_POR_PETICION + " ids por petición, se pidieron " + ids.size());
        }

        String parametro = ids.stream().map(String::valueOf).collect(Collectors.joining("-"));
        return ejecutarGet("/fixtures?ids=" + parametro, ApiFootballFixturesResponseDTO.class,
                "Fixtures por ids (" + ids.size() + ")");
    }

    public ApiTeamStatsResponseDTO getTeamStatistics(Integer fixtureId) {
        return ejecutarGet("/fixtures/statistics?fixture=" + fixtureId, ApiTeamStatsResponseDTO.class, "Team Stats del partido " + fixtureId);
    }

    public ApiPlayerStatsResponseDTO getPlayerStatistics(Integer fixtureId) {
        return ejecutarGet("/fixtures/players?fixture=" + fixtureId, ApiPlayerStatsResponseDTO.class, "Player Stats del partido " + fixtureId);
    }

    public ApiTrophiesResponseDTO getTrophies(String playerIds) {
        return ejecutarGet("/trophies?player=" + playerIds, ApiTrophiesResponseDTO.class, "Trofeos para jugadores: " + playerIds);
    }

    public ApiTrophiesResponseDTO getTrophiesByCoach(String coachIds) {
        return ejecutarGet("/trophies?coach=" + coachIds, ApiTrophiesResponseDTO.class, "Trofeos para entrenadores: " + coachIds);
    }

    /** Tope de ids por peticion en los endpoints que aceptan varios (comprobado: con 21 falla). */
    public static final int MAX_IDS_POR_PETICION = 20;

    /**
     * El palmares de hasta 20 entrenadores en una sola peticion.
     *
     * <p>El parametro es {@code coachs}, en plural, no {@code coach}: con {@code coach} la API
     * responde "The Coach field must contain an integer" y se pierde la peticion. Y la forma de
     * la respuesta tambien cambia, agrupada por entrenador, de ahi el DTO propio.</p>
     *
     * <p>Es la diferencia entre 27.123 peticiones y 1.357 para recorrer el escalafon entero.</p>
     *
     * @param ids entre 1 y {@value #MAX_IDS_POR_PETICION} ids de entrenador.
     */
    public ApiTrophiesPorEntrenadorDTO getTrophiesByCoachs(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Hay que pedir al menos un entrenador");
        }
        if (ids.size() > MAX_IDS_POR_PETICION) {
            // Igual que en getFixturesByIds: la API se queda con los primeros y no avisa, asi
            // que los de mas se darian por preguntados sin haberlo sido.
            throw new IllegalArgumentException(
                    "API-Football admite como mucho " + MAX_IDS_POR_PETICION + " ids por peticion, se pidieron " + ids.size());
        }

        String parametro = ids.stream().map(String::valueOf).collect(Collectors.joining("-"));
        return ejecutarGet("/trophies?coachs=" + parametro, ApiTrophiesPorEntrenadorDTO.class,
                "Trofeos de " + ids.size() + " entrenadores");
    }

    public ApiSidelinedResponseDTO getSidelined(String playerIds) {
        return ejecutarGet("/sidelined?player=" + playerIds, ApiSidelinedResponseDTO.class, "Bajas para jugadores: " + playerIds);
    }

    public ApiTransfersResponseDTO getTransfersByTeam(Integer teamId) {
        return ejecutarGet("/transfers?team=" + teamId, ApiTransfersResponseDTO.class, "Traspasos del equipo: " + teamId);
    }

    public JsonNode getTeamsByLeagueAndSeason(Integer leagueId, Integer season) {
        return ejecutarGet("/teams?league=" + leagueId + CONST_SEASON + season, JsonNode.class, "Equipos de la liga " + leagueId);
    }

    public JsonNode getStandings(Integer leagueId, Integer season) {
        return ejecutarGet("/standings?league=" + leagueId + CONST_SEASON + season, JsonNode.class, "Clasificación liga " + leagueId);
    }

    public JsonNode getCoachsByTeam(Integer teamId) {
        return ejecutarGet("/coachs?team=" + teamId, JsonNode.class, "Entrenadores equipo " + teamId);
    }

    public JsonNode getLeagues(Integer currentSeason) {
        return ejecutarGet("/leagues?season=" + currentSeason, JsonNode.class, "Ligas de la temporada " + currentSeason);
    }

    public JsonNode getPlayersByTeamAndSeason(Integer teamId, Integer season, int page) {
        return ejecutarGet("/players?team=" + teamId + CONST_SEASON + season + "&page=" + page, JsonNode.class, "Jugadores del equipo " + teamId + " página " + page);
    }

    public JsonNode getPlayerProfile(Integer playerId) {
        return ejecutarGet("/players/profiles?player=" + playerId, JsonNode.class, "Perfil del jugador " + playerId);
    }

    /**
     * Una pagina del catalogo completo de jugadores: <b>250 fichas por peticion</b>.
     *
     * <p>Es la misma ruta que {@link #getPlayerProfile(Integer)} pero sin el parametro
     * {@code player}, y ahi esta la diferencia de coste. Medido sobre la base: 267.689 de los
     * 510.032 jugadores tienen el nombre abreviado ("D. Barrow") porque el campo {@code name}
     * de la API llega asi, y solo 29.000 tienen guardado el completo. Rellenar el resto uno a
     * uno serian <b>481.000 peticiones</b> —mas de tres dias de cuota entera—; por paginas de
     * 250 son unas <b>2.000</b>, un 1,3% de la cuota de un dia.</p>
     *
     * <p>Cada ficha trae {@code firstname} y {@code lastname} por separado, que es de donde
     * sale el nombre sin abreviar.</p>
     */
    public JsonNode getPlayerProfilesPagina(int page) {
        return ejecutarGet("/players/profiles?page=" + page, JsonNode.class,
                "Catalogo de jugadores, pagina " + page);
    }

    /**
     * Cuotas previas al partido de todas las casas, en una petición.
     *
     * <p>La documentación dice que se publican entre 14 y 1 días antes, se refrescan cada 3
     * horas y se guardan 7 días después. Fuera de esa ventana la respuesta viene vacía, así
     * que quien llama debe cortar antes en vez de gastar la petición (ver CuotasServiceImpl).</p>
     */
    public ApiOddsResponseDTO getOddsByFixture(Integer fixtureId) {
        return ejecutarGet("/odds?fixture=" + fixtureId, ApiOddsResponseDTO.class, "Cuotas del partido " + fixtureId);
    }

    /**
     * Quién se pierde un partido (lesionados, sancionados y dudas), hasta 20 partidos de golpe.
     *
     * <p>No es lo mismo que {@code /sidelined}, que es el historial de lesiones de un jugador a
     * lo largo de su carrera: esto es la lista de bajas de un partido concreto.</p>
     */
    public ApiInjuriesResponseDTO getInjuriesByFixtures(Collection<Integer> fixtureIds) {
        if (fixtureIds == null || fixtureIds.isEmpty()) {
            throw new IllegalArgumentException("Hay que pedir al menos un partido");
        }
        if (fixtureIds.size() > MAX_FIXTURES_POR_PETICION) {
            throw new IllegalArgumentException(
                    "API-Football admite como mucho " + MAX_FIXTURES_POR_PETICION + " ids por petición");
        }
        String parametro = fixtureIds.stream().map(String::valueOf).collect(Collectors.joining("-"));
        return ejecutarGet("/injuries?ids=" + parametro, ApiInjuriesResponseDTO.class,
                "Bajas de " + fixtureIds.size() + " partido(s)");
    }

    /**
     * Cómo le ha ido a un equipo en una competición: racha, goles por franja de minutos,
     * porterías a cero, mayores goleadas y tarjetas.
     *
     * <p>Ojo con el nombre: {@link #getTeamStatistics(Integer)} es otra cosa distinta (las
     * estadísticas de un equipo en UN partido). Esto es el agregado de la temporada.</p>
     */
    public JsonNode getEstadisticasEquipoTemporada(Integer leagueId, Integer teamId, Integer season) {
        return ejecutarGet("/teams/statistics?league=" + leagueId + "&team=" + teamId + CONST_SEASON + season,
                JsonNode.class, "Rendimiento del equipo " + teamId + " en la liga " + leagueId);
    }

    /** Máximos goleadores de una liga y temporada. Una petición por liga+temporada. */
    public JsonNode getTopScorers(Integer leagueId, Integer season) {
        return ejecutarGet("/players/topscorers?league=" + leagueId + CONST_SEASON + season,
                JsonNode.class, "Goleadores de la liga " + leagueId);
    }

    /** Máximos asistentes de una liga y temporada. */
    public JsonNode getTopAssists(Integer leagueId, Integer season) {
        return ejecutarGet("/players/topassists?league=" + leagueId + CONST_SEASON + season,
                JsonNode.class, "Asistentes de la liga " + leagueId);
    }

    /** Jugadores con más amarillas de una liga y temporada. */
    public JsonNode getTopYellowCards(Integer leagueId, Integer season) {
        return ejecutarGet("/players/topyellowcards?league=" + leagueId + CONST_SEASON + season,
                JsonNode.class, "Amonestados de la liga " + leagueId);
    }

    /** Jugadores con más rojas de una liga y temporada. */
    public JsonNode getTopRedCards(Integer leagueId, Integer season) {
        return ejecutarGet("/players/topredcards?league=" + leagueId + CONST_SEASON + season,
                JsonNode.class, "Expulsados de la liga " + leagueId);
    }

    /**
     * Estado de la cuenta y consumo real del día.
     *
     * <p>Esta llamada <b>no cuenta para la cuota diaria</b> (lo dice la documentación), así que
     * es la forma honesta de saber cuántas peticiones quedan sin gastar una para averiguarlo.
     * Lo que se sabe por las cabeceras es solo lo que quedaba en la última petición hecha.</p>
     */
    public JsonNode getStatus() {
        return ejecutarGet("/status", JsonNode.class, "Estado de la cuenta");
    }

    public ApiFootballEventsResponseDTO getEventosFixture(Integer fixtureId) {
        return ejecutarGet("/fixtures/events?fixture=" + fixtureId, ApiFootballEventsResponseDTO.class, "Eventos del partido " + fixtureId);
    }


    /**
     * La queja que trae la respuesta, o null si viene limpia.
     *
     * <p>API-Football no usa codigos HTTP para esto: contesta <b>200</b> con
     * {@code "errors": {"rateLimit": "Too many requests..."}} y {@code "response": []}. Visto
     * desde fuera es indistinguible de "no hay datos", que es justo lo que hay que poder
     * distinguir.</p>
     *
     * <p>Cuando no hay error, el campo llega como array vacio; cuando lo hay, como objeto con
     * la razon dentro.</p>
     */
    private String errorDeLaApi(JsonNode cuerpo) {
        JsonNode errores = cuerpo != null ? cuerpo.get("errors") : null;
        if (errores == null || errores.isEmpty()) {
            return null;
        }
        return errores.toString();
    }

    /** Cuanto se espera cuando la API dice que se ha excedido el limite por minuto. */
    private static final Duration ESPERA_VENTANA_MINUTO = Duration.ofSeconds(20);

    private void esperarAVentanaDeMinuto() {
        try {
            Thread.sleep(ESPERA_VENTANA_MINUTO.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiFootballException("Interrupcion esperando a la ventana de peticiones", e);
        }
    }

    private <T> T ejecutarGet(String endpoint, Class<T> tipoRespuesta, String contexto) {
        ApiFootballException ultimoError = new ApiFootballException("No se obtuvo respuesta al recuperar " + contexto);

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            comprobarCuota();
            boolean esUltimoIntento = (intento == MAX_INTENTOS);

            try {
                HttpResponse<String> response = self.enviarPeticion(endpoint);
                actualizarCuotaDesdeHeaders(response);

                if (response.statusCode() == 200) {
                    JsonNode cuerpo = objectMapper.readTree(response.body());
                    String queja = errorDeLaApi(cuerpo);
                    if (queja != null) {
                        // HTTP 200 pero la API se queja y manda "response": [] -- el caso
                        // tipico es "rateLimit". Sin esto, quien llama lee una lista vacia y
                        // concluye que ese equipo no tiene plantilla, ese partido no tiene
                        // eventos... El 2026-09-09 dejo al Real Madrid con 20 jugadores: la
                        // pagina 1 llego, la 2 volvio "vacia" por el limite por minuto, y la
                        // sincronizacion solto a los 24 que faltaban por no venir en la unica
                        // pagina que si llego.
                        logger.warn("API-Football devolvio 200 con error ({}): {}", contexto, queja);
                        ultimoError = new ApiFootballException("La API respondio con error al recuperar "
                                + contexto + ": " + queja);
                        if (esUltimoIntento) {
                            throw ultimoError;
                        }
                        // Si la queja es el limite POR MINUTO, reintentar a los pocos
                        // milisegundos es tirar dos peticiones mas contra la misma pared: hay
                        // que dejar correr la ventana. El resto de quejas van al backoff normal.
                        if (queja.contains("rateLimit")) {
                            esperarAVentanaDeMinuto();
                        } else {
                            esperarAntesDeReintentar(intento);
                        }
                        continue;
                    }
                    return objectMapper.treeToValue(cuerpo, tipoRespuesta);
                }

                logger.error("Error en API-Football ({}): HTTP {}", contexto, response.statusCode());
                ultimoError = new ApiFootballException("Error HTTP " + response.statusCode() + " al recuperar " + contexto);

                if (!esErrorTransitorio(response.statusCode())) {
                    throw ultimoError;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiFootballException("Interrupción al recuperar " + contexto, e);
            } catch (LimiteDePeticionesPropioException e) {
                // Nuestro freno, no la API: lo que falta es turno, asi que se espera y se
                // vuelve. Antes esto subia tal cual y tumbaba el lote entero.
                ultimoError = e;
                esperarPorFaltaDeTurno(intento);
                continue;
            } catch (IOException e) {
                ultimoError = new ApiFootballException("Fallo de conexión al recuperar " + contexto, e);
            }

            if (esUltimoIntento) {
                throw ultimoError;
            }

            logger.warn("Reintentando petición ({}/{}) para {}: {}", intento, MAX_INTENTOS, contexto, ultimoError.getMessage());
            esperarAntesDeReintentar(intento);
        }

        throw ultimoError;
    }

    // Package-private (no 'private'): debe poder ser sobrescrito por el proxy CGLIB
    // de Spring para que @RateLimiter/@CircuitBreaker intercepten las llamadas realizadas
    // vía 'self'.
    @RateLimiter(name = "apiFootballRateLimiter", fallbackMethod = "rateLimitFallback")
    // Sin esto, una caída sostenida de API-Football hacía que cada ítem de un lote agotara
    // los 3 reintentos de ejecutarGet (hasta ~45s cada uno) antes de fallar; con el circuito
    // abierto tras varios fallos consecutivos, las siguientes peticiones fallan rápido en
    // vez de agotar ese tiempo una y otra vez durante el resto del lote.
    @CircuitBreaker(name = "apiFootballCircuitBreaker", fallbackMethod = "circuitBreakerFallback")
    HttpResponse<String> enviarPeticion(String endpoint) throws InterruptedException, IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header(CONST_API_KEY, apiKey)
                .timeout(TIMEOUT_LECTURA)
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /*
     * OJO CON LAS FIRMAS DE ESTOS DOS.
     *
     * Estaban los dos declarados como (String, Throwable), o sea identicas, y resilience4j
     * elige el fallback por el tipo de la excepcion: con dos candidatos igual de validos
     * llamaba al que le venia bien. El resultado era que un circuito abierto se anunciaba en el
     * log como "rate limit excedido" y al reves. Perseguimos durante un rato una saturacion del
     * limitador que no existia -- pasaban 2 peticiones por minuto -- porque el log culpaba al
     * limitador de lo que hacia el cortacircuitos.
     *
     * Cada uno pide AHORA su excepcion concreta. Asi cada aviso dice la verdad, y cualquier otro
     * fallo (un IOException, por ejemplo) ya no cae aqui: sube a ejecutarGet, que es quien sabe
     * reintentarlo.
     */

    private HttpResponse<String> rateLimitFallback(String endpoint, RequestNotPermitted t) {
        logger.warn("Sin turno en el limitador propio para {}. No es un fallo de la API.", endpoint);
        throw new LimiteDePeticionesPropioException(
                "Sin turno en el limitador de peticiones propio.", t);
    }

    private HttpResponse<String> circuitBreakerFallback(String endpoint, CallNotPermittedException t) {
        logger.error("Circuito ABIERTO para API-Football: no se llega a preguntar por {}. "
                + "Se reabre solo en cuanto vuelvan a pasar llamadas buenas.", endpoint);
        throw new ApiFootballException("API-Football no disponible temporalmente (circuito abierto).", t);
    }

    private boolean esErrorTransitorio(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    /**
     * Espera cuando lo que ha faltado es turno en el limitador propio.
     *
     * <p>Mas larga que la de un reintento normal y a proposito: el limitador reparte permisos
     * cada segundo, asi que volver a los 500 ms es volver a encontrarselo lleno.</p>
     */
    private void esperarPorFaltaDeTurno(int intento) {
        try {
            Thread.sleep(1500L * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiFootballException("Interrupción esperando turno en el limitador", e);
        }
    }

    private void esperarAntesDeReintentar(int intento) {
        try {
            Thread.sleep(500L * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiFootballException("Interrupción durante la espera entre reintentos", e);
        }
    }
}