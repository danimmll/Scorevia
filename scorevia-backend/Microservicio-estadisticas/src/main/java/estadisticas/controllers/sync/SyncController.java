package estadisticas.controllers.sync;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import estadisticas.dto.sync.EstadoIngestaDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Sync", description = "Ingesta de partidos, estadios, clasificaciones y datos extra desde API-Football")
public interface SyncController {

    @Operation(summary = "Sincronizar partidos de una fecha",
            description = "Fase 1: Llama a API-Football y crea/actualiza ligas, equipos, arbitros y partidos de esa fecha.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sincronizacion completada"),
            @ApiResponse(responseCode = "400", description = "Fecha invalida"),
            @ApiResponse(responseCode = "502", description = "Error al llamar a API-Football")
    })
    @PostMapping("/sync")
    ResponseEntity<String> sincronizarPorFecha(@RequestParam("fecha") String fecha);

    @Operation(summary = "Sincronizar estadísticas y jugadores",
            description = "Fase 2: Busca los partidos FINALIZADOS que no tengan estadísticas y descarga stats y jugadores.")
    @PostMapping("/stats-sync")
    ResponseEntity<String> sincronizarEstadisticasPendientes();

    @Operation(summary = "Sincronizar Trofeos y Bajas (Masivo)",
            description = "Fase 3: Descarga trofeos y lesiones/bajas de todos los jugadores en la BD. Proceso asíncrono.")
    @PostMapping("/sync-trofeos-bajas")
    ResponseEntity<String> sincronizarTrofeosYBajas();

    @Operation(summary = "Sincronizar Traspasos (Masivo)",
            description = "Fase 3: Descarga el historial de traspasos consultando por cada equipo en la BD. Proceso asíncrono.")
    @PostMapping("/sync-traspasos")
    ResponseEntity<String> sincronizarTraspasos();

    @Operation(summary = "Ingestar Equipos y Estadios Reales",
            description = "Backfill: Sobrescribe los equipos temporales de una liga con datos definitivos y registra sus estadios.")
    @PostMapping("/equipos-estadios")
    ResponseEntity<String> syncEquiposYEstadios(@RequestParam("idLiga") Integer idLiga, @RequestParam("temporada") Integer temporada);

    @Operation(summary = "Ingestar Entrenadores Masivamente",
            description = "Backfill: Registra a los entrenadores para todos los equipos existentes en la base de datos.")
    @PostMapping("/entrenadores")
    /**
     * @param soloDestacadas true (por defecto) refresca las 22 competiciones destacadas
     *                       (260 equipos); false recorre los 28.025 de la base.
     */
    ResponseEntity<String> syncEntrenadores(boolean soloDestacadas);

    @Operation(summary = "Generar Clasificación",
            description = "Backfill: Recupera la tabla clasificatoria completa de una liga en una temporada específica.")
    @PostMapping("/clasificaciones")
    ResponseEntity<String> syncClasificaciones(@RequestParam("idLiga") Integer idLiga, @RequestParam("temporada") Integer temporada);

    @PostMapping("/ligas/{temporada}")
    ResponseEntity<Void> syncLigasMaestras(@PathVariable Integer temporada);

    /**
     * @param soloDestacadas 218 equipos de las 22 competiciones grandes en vez de los 11.508
     *                       activos de toda la base
     * @param maxHoras       0 = solo los que no se han mirado nunca (por defecto); &gt; 0 =
     *                       ademas los sincronizados hace mas de esas horas; &lt; 0 = todos.
     *                       Sin esto la marca de "ya sincronizado" congelaba la plantilla para
     *                       siempre y no habia forma de forzar un repaso sin tocar la base
     */
    @Operation(summary = "Sincronizar plantillas",
            description = "Baja la plantilla de cada equipo activo en la temporada y suelta a "
                    + "quien ya no esta en el club. Reanudable.")
    @PostMapping("/jugadores/completos/{temporada}")
    ResponseEntity<String> syncJugadoresCompletos(@PathVariable Integer temporada,
                                                  @RequestParam(name = "soloDestacadas", defaultValue = "false")
                                                  boolean soloDestacadas,
                                                  @RequestParam(name = "maxHoras", defaultValue = "0") int maxHoras);

    @Operation(summary = "Sincronizar Perfiles de Jugadores Pendientes",
            description = "Backfill: recorre los jugadores con perfil_verificado=false y completa altura, peso, dorsal, posición y lugar de nacimiento vía /players/profiles. Proceso asíncrono.")
    @PostMapping("/perfiles-jugadores")
    ResponseEntity<String> sincronizarPerfilesJugadores();

    @Operation(summary = "Trofeos de los entrenadores",
            description = "Rellena el palmares de los entrenadores, que hoy esta vacio: la "
                    + "ingesta masiva recorre primero los 510.032 jugadores y nunca llega al "
                    + "bloque de entrenadores. Son 27.123 entrenadores a una peticion cada uno. "
                    + "Solo pide los que no tienen ningun trofeo guardado, asi que se puede "
                    + "cortar y relanzar. Proceso asincrono.")
    @PostMapping("/trofeos-entrenadores")
    ResponseEntity<String> sincronizarTrofeosEntrenadores();

    @Operation(summary = "Rellenar los nombres completos de los jugadores",
            description = "Recorre el catalogo de /players/profiles por paginas de 250 fichas y "
                    + "completa el nombre sin abreviar de los jugadores que ya tenemos. Medido: "
                    + "267.689 de 510.032 jugadores tienen el nombre abreviado (\"D. Barrow\") y "
                    + "solo 29.000 tienen guardado el completo. Uno a uno serian 481.000 "
                    + "peticiones; asi son unas 2.000. Se puede cortar y retomar con "
                    + "'desdePagina'. Proceso asincrono.")
    @PostMapping("/nombres-completos")
    ResponseEntity<String> sincronizarNombresCompletos(
            @RequestParam(name = "desdePagina", defaultValue = "1") int desdePagina,
            @RequestParam(name = "maxPaginas", defaultValue = "200") int maxPaginas);

    @Operation(summary = "Sincronizar partidos en vivo",
            description = "Llama a API-Football y actualiza los fixtures marcados como en vivo en este momento.")
    @PostMapping("/sync-en-vivo")
    ResponseEntity<String> sincronizarEnVivo();

    @Operation(summary = "Estado de la ingesta automática",
            description = "Cuántos partidos recién terminados esperan sus estadísticas y cuánta cuota de API-Football queda hoy.")
    @GetMapping("/estado-ingesta")
    ResponseEntity<EstadoIngestaDTO> estadoIngesta();

    @Operation(summary = "Recogida del día",
            description = "Lanza a mano la repesca de partidos acabados hace poco que siguen sin estadísticas. Es lo mismo que hace sola una vez al día.")
    @PostMapping("/recogida-dia")
    ResponseEntity<String> recogidaDelDia(@RequestParam(name = "horas", defaultValue = "30") int horas,
                                          @RequestParam(name = "max", defaultValue = "1500") int max);

    @Operation(summary = "Sincronizar Eventos Históricos (Masivo)",
            description = "Backfill: descarga los eventos (goles, tarjetas, sustituciones) de los partidos con stats pero sin eventos. Proceso asíncrono.")
    @PostMapping("/eventos-historicos")
    ResponseEntity<String> syncEventosHistoricos();
}