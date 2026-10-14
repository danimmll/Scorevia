package estadisticas.controllers.sync;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.databind.JsonNode;
import estadisticas.dto.sync.EstadoIngestaDTO;
import estadisticas.services.external.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import estadisticas.exception.BadRequestException;

@Slf4j
@RestController
@RequestMapping("/api/v1/partidos")
@Tag(name = "Sync", description = "Ingesta de partidos y datos extra desde API-Football")
public class SyncControllerImpl implements SyncController {

    private static final String CACHE_CONTEXTO_PREDICCION = "contextoPrediccion";

    private final PartidoIngestService partidoIngestService;
    private final EstadisticasIngestService estadisticasIngestService;
    private final EntidadesSecundariasIngestService entidadesSecundariasIngestService;
    private final Executor ingestaExecutor;
    private final MaestrosIngestService maestrosIngestService;
    private final EventoPartidoIngestService eventoPartidoIngestService;
    private final CacheManager cacheManager;
    private final ColaPostPartido colaPostPartido;
    private final ApiFootballClient apiFootballClient;

    public SyncControllerImpl(PartidoIngestService partidoIngestService,
                              EstadisticasIngestService estadisticasIngestService,
                              EntidadesSecundariasIngestService entidadesSecundariasIngestService,
                              @Qualifier("ingestaExecutor") Executor ingestaExecutor,
                              MaestrosIngestService maestrosIngestService, EventoPartidoIngestService eventoPartidoIngestService,
                              CacheManager cacheManager,
                              ColaPostPartido colaPostPartido,
                              ApiFootballClient apiFootballClient) {
        this.partidoIngestService = partidoIngestService;
        this.estadisticasIngestService = estadisticasIngestService;
        this.entidadesSecundariasIngestService = entidadesSecundariasIngestService;
        this.ingestaExecutor = ingestaExecutor;
        this.maestrosIngestService = maestrosIngestService;
        this.eventoPartidoIngestService = eventoPartidoIngestService;
        this.cacheManager = cacheManager;
        this.colaPostPartido = colaPostPartido;
        this.apiFootballClient = apiFootballClient;
    }

    /**
     * Evicción manual de "contextoPrediccion". Se usa (en vez de {@code @CacheEvict})
     * para los flujos asíncronos, de forma que la caché se vacíe DESPUÉS de que la
     * ingesta en segundo plano termine y no antes de lanzarla: si se vaciara antes,
     * una predicción que llegase mientras la ingesta está en curso recalcularía y
     * volvería a cachear el contexto con datos parciales durante el TTL.
     */
    private void evictarContextoPrediccion() {
        Optional.ofNullable(cacheManager.getCache(CACHE_CONTEXTO_PREDICCION))
                .ifPresent(Cache::clear);
    }

    /**
     * Lanza un job de sincronización en el executor dedicado, evictando la caché de
     * predicción al terminar. Sin el {@code exceptionally}, un fallo en el job (p. ej.
     * una excepción no controlada en la ingesta) hacía que {@code thenRun} nunca se
     * ejecutara y desaparecía sin dejar ningún rastro en los logs propios del servicio.
     */
    private void lanzarSyncAsync(Runnable tarea, String nombreJob) {
        CompletableFuture.runAsync(tarea, ingestaExecutor)
                .thenRun(this::evictarContextoPrediccion)
                .exceptionally(ex -> {
                    log.error("Sincronización '{}' fallida: {}", nombreJob, ex.getMessage(), ex);
                    return null;
                });
    }

    @Override
    @PostMapping("/sync")
    @CacheEvict(value = {"partidosLiga", "contextoPrediccion"}, allEntries = true)
    public ResponseEntity<String> sincronizarPorFecha(@RequestParam("fecha") String fecha) {
        validarFecha(fecha);
        int procesados = partidoIngestService.ingestarPorFecha(fecha);
        return ResponseEntity.ok("Partidos procesados: " + procesados);
    }

    @Override
    @PostMapping("/stats-sync")
    public ResponseEntity<String> sincronizarEstadisticasPendientes() {
        lanzarSyncAsync(estadisticasIngestService::ingestarEstadisticasPendientes, "stats-sync");
        return ResponseEntity.accepted().body("Sincronización iniciada.");
    }

    @Override
    @PostMapping("/sync-trofeos-bajas")
    public ResponseEntity<String> sincronizarTrofeosYBajas() {
        lanzarSyncAsync(entidadesSecundariasIngestService::ingestarTrofeosYBajasMasivo, "sync-trofeos-bajas");
        return ResponseEntity.ok("Sincronización iniciada en hilo dedicado.");
    }

    @Override
    @PostMapping("/sync-traspasos")
    public ResponseEntity<String> sincronizarTraspasos() {
        lanzarSyncAsync(entidadesSecundariasIngestService::ingestarTraspasosPorEquipo, "sync-traspasos");
        return ResponseEntity.ok("Sincronización iniciada en hilo dedicado.");
    }

    @Override
    @PostMapping("/equipos-estadios")
    public ResponseEntity<String> syncEquiposYEstadios(@RequestParam("idLiga") Integer idLiga, @RequestParam("temporada") Integer temporada) {
        maestrosIngestService.ingestarEquiposYEstadiosDeLiga(idLiga, temporada);
        return ResponseEntity.ok("Sincronización de equipos y estadios completada.");
    }

    @Override
    @PostMapping("/entrenadores")
    public ResponseEntity<String> syncEntrenadores(
            @RequestParam(name = "soloDestacadas", defaultValue = "true") boolean soloDestacadas) {
        // Por defecto SOLO las destacadas: la versión completa recorre los 28.025 equipos de
        // la base, o sea 28.000 peticiones, y con ese coste no se lanza nunca -- que es
        // justo por lo que el dato se pudría. Acotada son 260 equipos.
        if (soloDestacadas) {
            lanzarSyncAsync(maestrosIngestService::refrescarEntrenadoresDestacados, "entrenadores");
            return ResponseEntity.ok(
                    "Refresco de entrenadores de las competiciones destacadas iniciado "
                    + "(260 equipos, 1 petición cada uno).");
        }
        lanzarSyncAsync(maestrosIngestService::ingestarEntrenadoresPorEquipo, "entrenadores");
        return ResponseEntity.ok(
                "Sincronización COMPLETA de entrenadores iniciada: 28.025 equipos, "
                + "una petición cada uno. Se detiene sola si se acaba la cuota.");
    }

    private String descripcionAlcance(int maxHoras) {
        if (maxHoras < 0) return ", repasando todos";
        if (maxHoras == 0) return ", solo equipos nunca sincronizados";
        return ", refrescando los de más de " + maxHoras + " h";
    }

    @Override
    @PostMapping("/clasificaciones")
    @CacheEvict(value = "clasificaciones", allEntries = true)
    public ResponseEntity<String> syncClasificaciones(@RequestParam("idLiga") Integer idLiga, @RequestParam("temporada") Integer temporada) {
        maestrosIngestService.ingestarClasificaciones(idLiga, temporada);
        return ResponseEntity.ok("Sincronización de clasificaciones completada.");
    }

    @Override
    @PostMapping("/ligas/{temporada}")
    public ResponseEntity<Void> syncLigasMaestras(@PathVariable("temporada") Integer temporada) {
        maestrosIngestService.ingestarLigasMasivas(temporada);
        return ResponseEntity.ok().build();
    }

    @Override
    @PostMapping("/jugadores/completos/{temporada}")
    public ResponseEntity<String> syncJugadoresCompletos(
            @PathVariable("temporada") Integer temporada,
            @RequestParam(name = "soloDestacadas", defaultValue = "false") boolean soloDestacadas,
            @RequestParam(name = "maxHoras", defaultValue = "0") int maxHoras) {
        lanzarSyncAsync(
                () -> maestrosIngestService.ingestarJugadoresCompletosPorEquipo(temporada, soloDestacadas, maxHoras),
                "jugadores-completos");
        return ResponseEntity.accepted().body(
                "Sincronización de plantillas " + temporada + " iniciada en hilo dedicado ("
                        + (soloDestacadas ? "solo competiciones destacadas" : "todas las competiciones")
                        + descripcionAlcance(maxHoras)
                        + ").");
    }

    @Override
    @PostMapping("/perfiles-jugadores")
    public ResponseEntity<String> sincronizarPerfilesJugadores() {
        lanzarSyncAsync(maestrosIngestService::ingestarPerfilesPendientes, "perfiles-jugadores");
        return ResponseEntity.accepted().body("Sincronización de perfiles de jugadores iniciada en hilo dedicado.");
    }

    @Override
    @PostMapping("/trofeos-entrenadores")
    public ResponseEntity<String> sincronizarTrofeosEntrenadores() {
        lanzarSyncAsync(entidadesSecundariasIngestService::ingestarTrofeosDeEntrenadores,
                "trofeos-entrenadores");
        return ResponseEntity.accepted().body(
                "Relleno del palmares de entrenadores iniciado. Solo se piden los que no se han "
                        + "preguntado nunca (no los que no tienen trofeos, que son la mayoria y "
                        + "ya estan resueltos), asi que se puede cortar y relanzar sin repetir "
                        + "trabajo y termina de verdad.");
    }

    @Override
    @PostMapping("/nombres-completos")
    public ResponseEntity<String> sincronizarNombresCompletos(
            @RequestParam(name = "desdePagina", defaultValue = "1") int desdePagina,
            @RequestParam(name = "maxPaginas", defaultValue = "200") int maxPaginas) {
        lanzarSyncAsync(() -> maestrosIngestService.ingestarNombresCompletos(desdePagina, maxPaginas),
                "nombres-completos");
        return ResponseEntity.accepted().body(
                "Relleno de nombres completos iniciado desde la pagina " + desdePagina
                        + " (" + maxPaginas + " paginas como mucho, 250 fichas cada una). "
                        + "El log deja por donde se quedo para poder retomarlo.");
    }

    @Override
    @PostMapping("/sync-en-vivo")
    @CacheEvict(value = {"partidosLiga", "contextoPrediccion"}, allEntries = true)
    public ResponseEntity<String> sincronizarEnVivo() {
        int procesados = partidoIngestService.ingestarEnVivo();
        return ResponseEntity.ok("Partidos en vivo procesados: " + procesados);
    }

    private void validarFecha(String fecha) {
        try {
            LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Fecha no valida, formato esperado yyyy-MM-dd");
        }
    }

    @Override
    @GetMapping("/estado-ingesta")
    public ResponseEntity<EstadoIngestaDTO> estadoIngesta() {
        EstadoIngestaDTO.EstadoIngestaDTOBuilder estado = EstadoIngestaDTO.builder()
                .partidosEnCola(colaPostPartido.tamano())
                .peticionesRestantes(apiFootballClient.getPeticionesRestantes());

        // /status no cuenta para la cuota diaria (lo dice la documentación de API-Football),
        // así que aquí se puede preguntar el consumo real en vez de conformarse con lo que
        // decía la cabecera de la última petición que se hizo, que puede ser de ayer.
        try {
            JsonNode cuenta = apiFootballClient.getStatus().path("response");
            estado.plan(cuenta.path("subscription").path("plan").asText(null));
            JsonNode peticiones = cuenta.path("requests");
            if (!peticiones.path("current").isMissingNode()) {
                estado.peticionesGastadasHoy(peticiones.path("current").asInt());
            }
            if (!peticiones.path("limit_day").isMissingNode()) {
                estado.limiteDiario(peticiones.path("limit_day").asInt());
            }
        } catch (Exception e) {
            // Es información de apoyo: si /status no responde, se devuelve lo que ya se sabe.
            log.warn("No se pudo consultar /status de API-Football: {}", e.getMessage());
        }

        return ResponseEntity.ok(estado.build());
    }

    @Override
    @PostMapping("/recogida-dia")
    public ResponseEntity<String> recogidaDelDia(int horas, int max) {
        // Asíncrono como el resto de backfills: mil partidos a dos peticiones cada uno no
        // caben en el tiempo de vida de una petición HTTP.
        lanzarSyncAsync(() -> estadisticasIngestService.ingestarEstadisticasRecientes(horas, max), "recogida-dia");
        return ResponseEntity.accepted().body("Recogida iniciada sobre las últimas " + horas + " horas.");
    }

    @Override
    @PostMapping("/eventos-historicos")
    public ResponseEntity<String> syncEventosHistoricos() {
        // Asíncrono como el resto de backfills (evita bloquear/timeout en la petición con
        // un histórico grande); la caché se evicta al terminar, no antes, ver evictarContextoPrediccion().
        lanzarSyncAsync(eventoPartidoIngestService::ingestarEventosFaltantesHistoricos, "eventos-historicos");
        return ResponseEntity.accepted().body("Sincronización de eventos históricos iniciada en hilo dedicado.");
    }
}