package estadisticas.config;

import estadisticas.services.external.ApiFootballClient;
import estadisticas.services.external.ColaPostPartido;
import estadisticas.repositories.partidos.PartidoRepository;
import estadisticas.services.external.EstadisticasIngestService;
import estadisticas.services.external.MaestrosIngestService;
import estadisticas.services.external.PartidoIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import estadisticas.entities.partidos.Partido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Todo lo que la ingesta hace sola, sin que nadie lance un script.
 *
 * <p>Son siete trabajos con ritmos muy distintos:</p>
 *
 * <ol>
 *   <li><b>En vivo</b> (cada 30s): la lista de partidos en directo. Un tick cuesta
 *       <b>1 petición</b> para todos los partidos del mundo, timeline incluido. La cadencia es
 *       de 30s y no menos porque la documentación de API-Football recomienda una llamada por
 *       minuto para el directo y avisa de bloqueos por tráfico anómalo.
 *       Esto estuvo mucho tiempo apagado porque la primera versión pedía el timeline de todos
 *       los partidos en cada tick: una tarde de sábado con 1.500 partidos simultáneos se iba a
 *       13 millones de peticiones diarias, 87 veces la cuota del plan.</li>
 *
 *   <li><b>Post-partido</b> (cada 30s): atiende la cola de partidos que acaban de terminar
 *       para traerse sus estadísticas de equipo y de jugadores. Ver {@link ColaPostPartido}.</li>
 *
 *   <li><b>Recogida del día</b> (una vez al día): repesca los partidos recientes que se
 *       quedaron sin estadísticas porque la cola vive en memoria y el servicio se reinició, o
 *       porque la API tardó más de la cuenta en publicarlas.</li>
 *
 *   <li><b>Calendario</b> (una vez al día): trae los partidos de los próximos días para que
 *       la agenda esté puesta antes de que se jueguen. Cuesta una petición por fecha.</li>
 *
 *   <li><b>Clasificaciones</b> (dos veces al día): rehace la tabla de las competiciones donde
 *       se ha jugado algo, que son las únicas que pueden haber cambiado.</li>
 *
 *   <li><b>Reconciliación</b> (a mediodía): repasa ayer y hoy y arregla lo que se quedó a
 *       medias mientras el servicio estaba vivo.</li>
 *
 *   <li><b>Puesta al día</b> (al arrancar y cada seis horas): lo mismo, pero para los días en
 *       los que el servicio <b>no</b> estaba vivo. Un cron que no dispara no deja rastro, y
 *       al día siguiente la ventana de la reconciliación ya ha pasado de largo: sin esto,
 *       una noche con la máquina apagada deja esa jornada rota para siempre.</li>
 * </ol>
 *
 * <p>Cada uno se enciende y se apaga con su propia propiedad, y ninguno depende de los otros:
 * antes toda esta clase colgaba de {@code scorevia.ingesta.vivo.habilitada}, así que apagar el
 * directo apagaba de paso cualquier otra cosa programada aquí dentro.</p>
 *
 * <p><b>Todos vienen apagados por defecto.</b> Mientras el rellenado histórico siga corriendo a
 * mano hace falta hasta la última petición del día, y un trabajo automático encendido solo le
 * resta cuota. Se van encendiendo (variables {@code INGESTA_*_HABILITADA}) cuando el rellenado
 * esté al día.</p>
 *
 * @author Daniel
 * @version 3.0
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class IngestaScheduler {

    private final PartidoIngestService partidoIngestService;
    private final EstadisticasIngestService estadisticasIngestService;
    private final ColaPostPartido colaPostPartido;
    private final MaestrosIngestService maestrosIngestService;
    private final PartidoRepository partidoRepository;
    private final ApiFootballClient apiFootballClient;

    /**
     * Fechas que esta instancia ya ha releido, para no volver a pedir lo mismo en
     * cada ciclo.
     *
     * <p>Vive en memoria y se pierde al reiniciar a proposito: ver
     * {@link #ponerseAlDiaTrasUnParon()}.</p>
     */
    private final Set<LocalDate> fechasYaRelidas = ConcurrentHashMap.newKeySet();

    /**
     * Tablas de posiciones ({@code liga-temporada}) que esta instancia ya ha rehecho, por lo
     * mismo: sin la marca, cada ciclo de seis horas volveria a pedir las quinientas.
     */
    private final Set<String> tablasYaRefrescadas = ConcurrentHashMap.newKeySet();

    @Value("${scorevia.ingesta.vivo.habilitada:false}")
    private boolean vivoHabilitada;

    @Value("${scorevia.ingesta.post-partido.habilitada:false}")
    private boolean postPartidoHabilitada;

    @Value("${scorevia.ingesta.recogida.habilitada:false}")
    private boolean recogidaHabilitada;

    /** Ventana hacia atrás de la recogida diaria. 30h cubre el día entero más el margen. */
    @Value("${scorevia.ingesta.recogida.horas:30}")
    private int recogidaHoras;

    /** Tope de partidos por recogida. A 3 peticiones cada uno, 1.500 son 4.500 peticiones. */
    @Value("${scorevia.ingesta.recogida.max-partidos:1500}")
    private int recogidaMaxPartidos;

    @Value("${scorevia.ingesta.calendario.habilitada:false}")
    private boolean calendarioHabilitado;

    /** Días hacia delante que se traen del calendario, además de ayer y hoy. */
    @Value("${scorevia.ingesta.calendario.dias:3}")
    private int calendarioDias;

    @Value("${scorevia.ingesta.clasificaciones.habilitada:false}")
    private boolean clasificacionesHabilitadas;

    /**
     * Tope de partidos en vivo a los que se les pide el once en cada tick.
     *
     * <p>A veinte por peticion, 60 son 3 peticiones. Con el directo cada 30 segundos eso son
     * 360 al dia como mucho, y en la practica muchas menos: solo se piden los que faltan, y
     * un partido solo falta una vez.</p>
     */
    @Value("${scorevia.ingesta.vivo.max-alineaciones:60}")
    private int maxAlineacionesEnVivo;

    /**
     * Cuanto se adelanta a los partidos que aun no han empezado.
     *
     * <p>Tres horas cubren de sobra la ventana en la que se publica el once (20-60 minutos
     * antes) con margen para un retraso de la API, y como solo se piden los que faltan, un
     * partido ya resuelto no vuelve a costar nada.</p>
     */
    @Value("${scorevia.ingesta.alineaciones.horas-antes:3}")
    private int horasAntesAlineacion;

    @Value("${scorevia.temporada-actual:2026}")
    private Integer temporadaActual;

    @Value("${scorevia.ingesta.plantillas.habilitada:false}")
    private boolean plantillasHabilitada;

    /**
     * Cuantas horas tiene que llevar una plantilla sin mirarse para volver a pedirla.
     *
     * <p>20 y no 24 para que la pasada de manana no se salte un club por unos minutos: el cron
     * no cae siempre a la misma hora exacta y con 24 justas se iban quedando equipos fuera un
     * dia si y otro no.</p>
     */
    @Value("${scorevia.ingesta.plantillas.max-horas:20}")
    private int plantillasMaxHoras;

    @Value("${scorevia.ingesta.reconciliacion.habilitada:false}")
    private boolean reconciliacionHabilitada;

    /**
     * Cuantas horas tiene que llevar empezado un partido que sigue en EN_JUEGO para darlo por
     * colgado. Seis cubren 90 minutos, descuentos, prorroga, penaltis y una interrupcion larga.
     */
    @Value("${scorevia.ingesta.reconciliacion.horas-colgado:6}")
    private int horasPartidoColgado;

    /** Tope de partidos que la reconciliacion encola de una vez. A 20 por peticion, 600 son 30. */
    @Value("${scorevia.ingesta.reconciliacion.max-partidos:600}")
    private int reconciliacionMaxPartidos;

    @Value("${scorevia.ingesta.puesta-al-dia.habilitada:false}")
    private boolean puestaAlDiaHabilitada;

    /**
     * Cuantos dias hacia atras se miran al arrancar.
     *
     * <p>Siete cubren el caso normal -un fin de semana con el equipo apagado, unas
     * vacaciones cortas- sin arrastrar el atraso historico, que es cosa de los scripts.
     * Un paron mas largo se recupera igual, a siete dias por arranque.</p>
     */
    @Value("${scorevia.ingesta.puesta-al-dia.dias:7}")
    private int puestaAlDiaDias;

    /** Tope de fechas por pasada. Una peticion cada una, asi que el gasto esta acotado. */
    @Value("${scorevia.ingesta.puesta-al-dia.max-fechas:7}")
    private int puestaAlDiaMaxFechas;

    /**
     * Tope de tablas de posiciones por pasada.
     *
     * <p>Quinientas cubren de una vez todas las competiciones que juegan en una semana
     * (medido: 497 el 2026-09-10) y son el 0,3% de la cuota diaria. Un atraso mayor se
     * termina en el ciclo siguiente.</p>
     */
    @Value("${scorevia.ingesta.puesta-al-dia.max-tablas:500}")
    private int puestaAlDiaMaxTablas;

    /** Ventana de la que se miran los partidos jugados para saber qué tablas han cambiado. */
    @Value("${scorevia.ingesta.clasificaciones.horas:24}")
    private int clasificacionesHoras;

    // =================================================================================
    // 1. PARTIDOS EN VIVO
    // =================================================================================
    @Scheduled(fixedRateString = "${scorevia.ingesta.vivo.cadencia-ms:30000}")
    public void sincronizarPartidosEnVivo() {
        if (!vivoHabilitada) {
            return;
        }

        // El guardia de cuota está también dentro del cliente, pero comprobarlo aquí evita
        // llenar el log de excepciones cada 10 segundos cuando se llega al suelo duro.
        if (!apiFootballClient.hayCuotaDisponible()) {
            log.warn("Ingesta en vivo pausada: cuota de API-Football en la reserva intocable ({} restantes).",
                    apiFootballClient.getPeticionesRestantes());
            return;
        }

        try {
            partidoIngestService.ingestarEnVivo();
        } catch (Exception e) {
            log.error("Fallo al sincronizar partidos en vivo: {}", e.getMessage());
        }

    }

    // =================================================================================
    // 1b. ALINEACIONES: LAS DEL DIRECTO Y LAS DE ANTES DEL PITIDO
    // =================================================================================
    /**
     * Trae el once de los partidos que estan jugandose y de los que estan a punto de empezar.
     *
     * <p>Va aparte del tick del directo por dos motivos. Uno: el feed de {@code live=all}
     * <b>no trae alineaciones</b> (sus claves son fixture, league, teams, goals, score y
     * events), asi que hay que pedirlas igualmente. Y dos, el importante: <b>el once se
     * publica entre 20 y 60 minutos ANTES del pitido inicial</b>, que es justo cuando la
     * gente lo quiere ver. Colgado del directo solo aparecia con el partido ya empezado.</p>
     *
     * <p><b>Por que cada 15 minutos y no cada hora.</b> El coste es el mismo en la practica:
     * se piden de veinte en veinte y solo los que faltan, y medido sobre la base hay 13
     * partidos en las tres horas siguientes -- <b>una peticion</b>. Con una hora de cadencia,
     * un once publicado justo despues de un ciclo tardaria una hora en verse, y para entonces
     * el partido ya ha empezado. A 15 minutos son ~200 peticiones al dia de las 150.000.</p>
     */
    @Scheduled(fixedRateString = "${scorevia.ingesta.alineaciones.cadencia-ms:900000}")
    public void sincronizarAlineaciones() {
        if (!vivoHabilitada || !apiFootballClient.hayCuotaDisponible()) {
            return;
        }
        try {
            estadisticasIngestService.guardarAlineacionesPendientes(
                    maxAlineacionesEnVivo, horasAntesAlineacion);
        } catch (Exception e) {
            log.error("Fallo al traer las alineaciones: {}", e.getMessage());
        }
    }

    // =================================================================================
    // 2. PARTIDOS RECIÉN TERMINADOS
    // =================================================================================
    /**
     * Cada 30 segundos, no cada 10 como el directo: aquí no hay nada que enseñar en tiempo
     * real —los partidos de la cola ya han terminado— y cada partido atendido cuesta tres
     * peticiones, mientras que un tick del directo cuesta una para todos.
     */
    @Scheduled(fixedRateString = "${scorevia.ingesta.post-partido.cadencia-ms:30000}")
    public void atenderPartidosTerminados() {
        if (!postPartidoHabilitada) {
            return;
        }

        try {
            int completados = colaPostPartido.procesarRonda();
            if (completados > 0) {
                log.info("Post-partido: {} partidos completados, {} siguen en cola.",
                        completados, colaPostPartido.tamano());
            }
        } catch (Exception e) {
            log.error("Fallo atendiendo la cola de partidos terminados: {}", e.getMessage());
        }
    }

    // =================================================================================
    // 3. RECOGIDA DEL DÍA
    // =================================================================================
    /**
     * A las 5:10 UTC por defecto: bien entrada la madrugada europea, con los partidos
     * americanos ya terminados y publicados, y lejos del resto de trabajos pesados de la noche.
     */
    @Scheduled(cron = "${scorevia.ingesta.recogida.cron:0 10 5 * * *}", zone = "UTC")
    public void recogerPartidosSinEstadisticas() {
        if (!recogidaHabilitada) {
            return;
        }

        try {
            estadisticasIngestService.ingestarEstadisticasRecientes(recogidaHoras, recogidaMaxPartidos);
        } catch (Exception e) {
            log.error("Fallo en la recogida diaria de estadísticas: {}", e.getMessage());
        }
    }

    // =================================================================================
    // 3.b PLANTILLAS DE LOS CLUBES GRANDES
    // =================================================================================
    /**
     * Quien esta hoy en cada club de las competiciones destacadas.
     *
     * <h2>Por que hace falta un cron</h2>
     * {@code jugadores.id_equipo} es lo que la ficha de un club ensena como "plantilla actual",
     * y solo se actualiza cuando se vuelve a pedir ese club. La pasada completa son 11.508
     * equipos, asi que en la practica se lanzaba a mano cada varios meses -- y entre medias el
     * dato se pudria en silencio: el 2026-09-08 el Real Madrid figuraba con 45 jugadores,
     * Kroos entre ellos, retirado hacia dos anos.
     *
     * <p>Acotada a los clubes de las 22 destacadas son unas <b>400 peticiones</b> de las
     * 150.000 del dia, o sea el 0,3%. A ese precio no tiene sentido que lo lance nadie a mano.</p>
     *
     * <p>A las 3:20 UTC: lejos de la recogida de estadisticas (5:10).</p>
     */
    @Scheduled(cron = "${scorevia.ingesta.plantillas.cron:0 20 3 * * *}", zone = "UTC")
    public void refrescarPlantillasDestacadas() {
        if (!plantillasHabilitada || !apiFootballClient.hayCuotaDisponible()) {
            return;
        }

        try {
            maestrosIngestService.ingestarJugadoresCompletosPorEquipo(
                    temporadaActual, true, plantillasMaxHoras);
        } catch (Exception e) {
            log.error("Fallo en el refresco diario de plantillas: {}", e.getMessage());
        }
    }

    // =================================================================================
    // 4. CALENDARIO
    // =================================================================================
    /**
     * Trae los partidos de ayer, hoy y los próximos días.
     *
     * <p>Ayer entra a propósito: es lo que recoge los aplazamientos y las correcciones de
     * resultado que la API publica después. Y el listado por fecha es lo único que da de alta
     * un partido <b>antes</b> de jugarse: el feed en vivo solo lo ve cuando ya ha empezado, así
     * que sin esto la agenda estaría siempre vacía.</p>
     */
    @Scheduled(cron = "${scorevia.ingesta.calendario.cron:0 30 4 * * *}", zone = "UTC")
    public void sincronizarCalendario() {
        if (!calendarioHabilitado) {
            return;
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("UTC"));
        for (int desplazamiento = -1; desplazamiento <= calendarioDias; desplazamiento++) {
            if (!apiFootballClient.hayCuotaDisponible()) {
                log.warn("Sincronización de calendario detenida por cuota.");
                return;
            }

            String fecha = hoy.plusDays(desplazamiento).toString();
            try {
                int procesados = partidoIngestService.ingestarPorFecha(fecha);
                log.info("Calendario {}: {} partidos sincronizados.", fecha, procesados);
            } catch (Exception e) {
                // Un día que falla no debe cortar los demás: cada fecha es independiente.
                log.error("Fallo al sincronizar el calendario de {}: {}", fecha, e.getMessage());
            }
        }
    }

    // =================================================================================
    // 5. CLASIFICACIONES
    // =================================================================================
    /**
     * Rehace la tabla de las competiciones en las que se ha jugado algo.
     *
     * <p>Dos veces al día (madrugada y noche, hora UTC): la de la noche deja al día la
     * jornada de tarde, que es cuando alguien va a mirar la tabla, y la de la madrugada
     * recoge lo que se juega de noche en América.</p>
     *
     * <p>Solo las competiciones con partidos recientes. Pedir las 1.244 de la base serían más
     * de mil peticiones diarias para reescribir tablas que no se han movido; así son unas
     * 330 un sábado cargado y ocho un martes.</p>
     */
    @Scheduled(cron = "${scorevia.ingesta.clasificaciones.cron:0 40 5,22 * * *}", zone = "UTC")
    public void refrescarClasificaciones() {
        if (!clasificacionesHabilitadas) {
            return;
        }

        LocalDateTime desde = LocalDateTime.now(ZoneId.of("UTC")).minusHours(clasificacionesHoras);
        List<Object[]> ligas = partidoRepository.findLigasYTemporadasConPartidosDesde(desde);
        log.info("Refresco de clasificaciones: {} competiciones con partidos en las últimas {}h.",
                ligas.size(), clasificacionesHoras);

        int refrescadas = 0;
        for (Object[] fila : ligas) {
            // Mantenimiento, no urgencia: para antes que el directo, dejándole su colchón.
            if (!apiFootballClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Refresco de clasificaciones detenido por cuota tras {} competiciones.", refrescadas);
                return;
            }
            try {
                maestrosIngestService.ingestarClasificaciones((Integer) fila[0], (Integer) fila[1]);
                refrescadas++;
            } catch (Exception e) {
                // Una competición que falla no puede cortar las otras trescientas.
                log.error("Fallo al refrescar la clasificación de la liga {} temporada {}: {}",
                        fila[0], fila[1], e.getMessage());
            }
        }
        log.info("Refresco de clasificaciones terminado: {} tablas actualizadas.", refrescadas);
    }

    // =================================================================================
    // 6. RECONCILIACIÓN DEL DÍA
    // =================================================================================
    /**
     * A mediodía, repasa lo de ayer y lo de hoy y arregla lo que se quedó a medias.
     *
     * <p>Los otros cinco trabajos hacen su parte bien mientras todo va bien. Este existe para
     * cuando no: el contenedor reiniciado en mitad de una jornada, un rato sin cuota, la API
     * que deja de devolver un partido en {@code live=all} antes de darlo por terminado. Cada
     * uno de esos huecos deja el mismo rastro —partidos clavados en EN_JUEGO, partidos
     * acabados sin timeline y sin estadísticas— y nada los volvía a mirar.</p>
     *
     * <p>Hace tres cosas, de más barata a más cara:</p>
     * <ol>
     *   <li><b>Reingerir ayer y hoy por fecha</b>: dos peticiones. El listado por fecha trae
     *       el estado real de cada partido, así que esto solo ya destraba los colgados en
     *       EN_JUEGO y recoge aplazamientos y resultados corregidos.</li>
     *   <li><b>Encolar los de ayer sin estadísticas</b>, gateado por la cobertura de la
     *       competición para no pedir lo que la API no tiene.</li>
     *   <li><b>Encolar los de ayer sin eventos</b>, con su propia bandera de cobertura.</li>
     * </ol>
     *
     * <p>Lo encolado lo atiende {@link ColaPostPartido}, que agrupa de veinte en veinte con
     * {@code /fixtures?ids=}: los 600 partidos del tope son 30 peticiones, no 600. Un día
     * normal no llega ni a eso, porque solo entra lo que de verdad falta.</p>
     *
     * <p>Mediodía UTC a propósito: los partidos de la noche americana llevan horas
     * terminados y publicados, y no se pisa con ningún otro trabajo (4:30 calendario, 5:10
     * recogida, 5:40 y 22:40 clasificaciones).</p>
     */
    @Scheduled(cron = "${scorevia.ingesta.reconciliacion.cron:0 0 12 * * *}", zone = "UTC")
    public void reconciliarPartidosDelDia() {
        if (!reconciliacionHabilitada) {
            return;
        }

        LocalDate hoy = LocalDate.now(ZoneId.of("UTC"));
        for (String fecha : List.of(hoy.minusDays(1).toString(), hoy.toString())) {
            if (!apiFootballClient.hayCuotaDisponible()) {
                log.warn("Reconciliación detenida por cuota antes de {}.", fecha);
                return;
            }
            try {
                log.info("Reconciliación {}: {} partidos releídos.",
                        fecha, partidoIngestService.ingestarPorFecha(fecha));
            } catch (Exception e) {
                log.error("Fallo reingiriendo {} en la reconciliación: {}", fecha, e.getMessage());
            }
        }

        // Los que sigan colgados después de releer la fecha ya no son un despiste puntual:
        // la API no los da por terminados. Se dejan en el log para poder mirarlos, sin
        // tocarlos a mano —inventar un FINALIZADO que la fuente no confirma sería peor.
        List<Partido> colgados = partidoRepository.findEnJuegoColgados(
                LocalDateTime.now(ZoneId.of("UTC")).minusHours(horasPartidoColgado));
        if (!colgados.isEmpty()) {
            log.warn("{} partidos siguen en EN_JUEGO tras releer su fecha; el más antiguo es el {} del {}.",
                    colgados.size(), colgados.get(0).getIdPartido(), colgados.get(0).getFecha());
        }

        LocalDateTime desde = hoy.minusDays(1).atStartOfDay();
        LocalDateTime hasta = LocalDateTime.now(ZoneId.of("UTC"));
        Pageable tope = PageRequest.of(0, reconciliacionMaxPartidos);

        int encolados = 0;
        encolados += encolarTodos(partidoRepository.findPartidosRecientesPendientesDeStats(desde, hasta, tope));
        encolados += encolarTodos(partidoRepository.findPartidosRecientesSinEventos(desde, hasta, tope));
        log.info("Reconciliación terminada: {} partidos encolados para completar (cola: {}).",
                encolados, colaPostPartido.tamano());
    }

    // =================================================================================
    // 7. PUESTA AL DIA DESPUES DE UN PARON
    // =================================================================================
    /**
     * Al arrancar, y cada pocas horas, busca dias recientes que se quedaron a medias y los
     * relee enteros.
     *
     * <h2>Por que no bastaba la reconciliacion de mediodia</h2>
     * Un cron solo dispara si el proceso esta vivo a esa hora exacta, y el de las 12:00 UTC
     * ademas solo mira ayer y hoy. En una maquina que se apaga por la noche -o un contenedor
     * parado un fin de semana- ese repaso sencillamente no ocurre, y al dia siguiente su
     * ventana ya ha dejado atras el dia perdido. Ese dia se queda roto <b>para siempre</b>:
     * partidos jugados hace semanas que la aplicacion sigue enseñando como PROGRAMADO, sin
     * resultado, sin estadisticas y sin timeline.
     *
     * <p>No es un supuesto. El 2026-09-10, con los siete trabajos encendidos, la base tenia
     * 233 de los 336 partidos del dia anterior clavados en PROGRAMADO y 9 mas del anterior;
     * hubo que releer las fechas a mano con {@code scripts/backfill.py}. Esto es ese arreglo
     * manual, hecho solo y en cuanto el servicio levanta.</p>
     *
     * <h2>Lo que cuesta</h2>
     * Una peticion por dia afectado, con tope. Un dia entero -cientos de partidos- se arregla
     * con esa sola peticion, porque {@code fixtures?date=} devuelve la jornada completa;
     * pedirlos de uno en uno seria el mismo dato a doscientas veces el precio. Y si no hay
     * ningun dia roto no gasta nada: se va con una consulta a la base.
     *
     * <h2>Y las tablas de posiciones</h2>
     * Mismo problema, distinto sintoma: el refresco de clasificaciones mira las competiciones
     * con partidos en las ultimas 24 horas, asi que un dia sin disparar se le escapa entero y
     * la tabla se queda mal hasta que esa competicion vuelva a jugar -en una liga, una semana-.
     * Por eso {@link #refrescarTablasAtrasadas(LocalDate)} va en el mismo trabajo pero
     * <b>sin depender</b> de que falte ningun resultado: el 2026-09-07 no faltaba ninguno y
     * las tablas de La Liga y la Serie A daban un partido de menos a ocho equipos.
     *
     * <h2>Cada fecha se relee una vez por arranque</h2>
     * Siempre quedan partidos que la propia API deja en NS para siempre: competiciones
     * menores cuyo resultado no publica nunca (el 2026-09-10 quedaron 19 asi entre los dos
     * dias recuperados). Sin esa marca esos fantasmas harian releer sus fechas en cada
     * pasada, para nada. Al reiniciar el proceso la marca se pierde, que es justo cuando
     * vuelve a interesar mirar.
     */
    @Scheduled(initialDelayString = "${scorevia.ingesta.puesta-al-dia.retardo-ms:120000}",
            fixedDelayString = "${scorevia.ingesta.puesta-al-dia.cadencia-ms:21600000}")
    public void ponerseAlDiaTrasUnParon() {
        if (!puestaAlDiaHabilitada) {
            return;
        }

        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("UTC"));
        LocalDate primerDia = ahora.toLocalDate().minusDays(puestaAlDiaDias);

        // Las dos mitades son independientes a proposito. Una tabla se queda atrasada aunque
        // no falte ni un resultado -paso el 2026-09-07 con La Liga-, asi que encadenarlas
        // dejaria fuera justo el caso que hay que arreglar.
        releerFechasSinCerrar(ahora, primerDia);
        refrescarTablasAtrasadas(primerDia);
    }

    /**
     * Relee entera cada fecha reciente que tenga partidos ya jugados y sin cerrar, y encola
     * lo recuperado para que se le pidan estadisticas y timeline.
     */
    private void releerFechasSinCerrar(LocalDateTime ahora, LocalDate primerDia) {
        // El limite superior importa: un partido que empezo hace dos horas puede estar
        // jugandose de verdad, y ahi no hay nada que recuperar.
        LocalDateTime limite = ahora.minusHours(horasPartidoColgado);
        fechasYaRelidas.removeIf(fecha -> fecha.isBefore(primerDia));

        List<LocalDate> pendientes;
        try {
            pendientes = partidoRepository
                    .findFechasConPartidosSinCerrar(primerDia.atStartOfDay(), limite).stream()
                    .map(LocalDate::parse)
                    .filter(fecha -> !fechasYaRelidas.contains(fecha))
                    .limit(puestaAlDiaMaxFechas)
                    .toList();
        } catch (Exception e) {
            // Si esto se escapara, Spring dejaria de reprogramar la tarea y la puesta al dia
            // quedaria muerta hasta el siguiente reinicio -- justo lo contrario de para lo
            // que existe.
            log.error("Fallo buscando fechas sin cerrar: {}", e.getMessage());
            return;
        }

        if (pendientes.isEmpty()) {
            return;
        }

        log.info("Puesta al dia: {} fechas con partidos sin cerrar {}.", pendientes.size(), pendientes);

        LocalDate masAntiguaRecuperada = null;
        for (LocalDate fecha : pendientes) {
            if (!apiFootballClient.hayCuotaDisponible()) {
                log.warn("Puesta al dia detenida por cuota antes de {}.", fecha);
                break;
            }
            try {
                int procesados = partidoIngestService.ingestarPorFecha(fecha.toString());
                // La marca va aqui y no antes: si la lectura falla, la fecha sigue pendiente
                // para el siguiente ciclo en vez de darse por recuperada sin serlo.
                fechasYaRelidas.add(fecha);
                if (masAntiguaRecuperada == null) {
                    masAntiguaRecuperada = fecha;   // la consulta las devuelve ordenadas
                }
                log.info("Puesta al dia {}: {} partidos releidos.", fecha, procesados);
            } catch (Exception e) {
                // Una fecha que falla no puede cortar las demas: cada dia es independiente.
                log.error("Fallo releyendo {} en la puesta al dia: {}", fecha, e.getMessage());
            }
        }

        // Las tablas van aparte a proposito: se quedan atrasadas aunque no falte ningun
        // resultado, asi que esto NO cuelga de que se haya recuperado alguna fecha.
        if (masAntiguaRecuperada == null) {
            return;
        }

        // Los partidos recien recuperados han pasado a FINALIZADO sin que nadie los viera
        // terminar, asi que no estan en la cola ni tienen stats ni eventos. Se encolan igual
        // que en la reconciliacion, pero con la ventana entera que se acaba de recuperar y no
        // solo con la de ayer.
        Pageable tope = PageRequest.of(0, reconciliacionMaxPartidos);
        LocalDateTime inicioVentana = masAntiguaRecuperada.atStartOfDay();
        try {
            int encolados = encolarTodos(partidoRepository
                    .findPartidosRecientesPendientesDeStats(inicioVentana, ahora, tope))
                    + encolarTodos(partidoRepository
                    .findPartidosRecientesSinEventos(inicioVentana, ahora, tope));
            log.info("Puesta al dia terminada: {} partidos encolados para completar (cola: {}).",
                    encolados, colaPostPartido.tamano());
        } catch (Exception e) {
            // Los resultados ya estan guardados, que es lo importante; lo que falta aqui son
            // las stats y el timeline, y de eso ya se encarga la recogida diaria.
            log.error("Fallo encolando lo recuperado en la puesta al dia: {}", e.getMessage());
        }
    }

    /**
     * Rehace las tablas de posiciones de las competiciones que jugaron mientras el servicio
     * no estaba.
     *
     * <h2>Por que no basta con el refresco de las 5:40 y las 22:40</h2>
     * Ese cron mira las competiciones con partidos en las <b>ultimas 24 horas</b>, asi que si
     * no dispara (proceso apagado) esa jornada se le escapa, y al dia siguiente su ventana ya
     * ha pasado de largo. La tabla queda mal hasta que esa competicion vuelva a jugar, que en
     * una liga puede ser una semana entera.
     *
     * <p>Medido el 2026-09-10: La Liga y la Serie A daban 4 y 2 partidos jugados a ocho
     * equipos que ya habian jugado uno mas -el del 7 de septiembre, con la maquina apagada-.
     * Comprobado contra la API: alli el dato estaba bien, el atraso era nuestro.</p>
     *
     * <p>Se cuenta desde el mismo dia que la puesta al dia de partidos, y cada tabla se rehace
     * una sola vez por arranque. Una peticion por tabla.</p>
     */
    private void refrescarTablasAtrasadas(LocalDate primerDia) {
        if (!clasificacionesHabilitadas) {
            return;
        }

        List<Object[]> ligas;
        try {
            ligas = partidoRepository.findLigasYTemporadasConPartidosDesde(primerDia.atStartOfDay());
        } catch (Exception e) {
            log.error("Fallo buscando competiciones con tabla atrasada: {}", e.getMessage());
            return;
        }

        int refrescadas = 0;
        int saltadas = 0;
        for (Object[] fila : ligas) {
            String clave = fila[0] + "-" + fila[1];
            if (tablasYaRefrescadas.contains(clave)) {
                saltadas++;
                continue;
            }
            if (refrescadas >= puestaAlDiaMaxTablas) {
                log.info("Puesta al dia: tope de {} tablas alcanzado, el resto en el proximo ciclo.",
                        puestaAlDiaMaxTablas);
                break;
            }
            // Mantenimiento, no urgencia: para antes que el directo, dejandole su colchon.
            if (!apiFootballClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Puesta al dia de tablas detenida por cuota tras {}.", refrescadas);
                break;
            }
            try {
                maestrosIngestService.ingestarClasificaciones((Integer) fila[0], (Integer) fila[1]);
                tablasYaRefrescadas.add(clave);
                refrescadas++;
            } catch (Exception e) {
                // Una competicion que falla no puede cortar las otras quinientas.
                log.error("Fallo rehaciendo la tabla de la liga {} temporada {}: {}",
                        fila[0], fila[1], e.getMessage());
            }
        }

        if (refrescadas > 0 || saltadas > 0) {
            log.info("Puesta al dia de tablas: {} rehechas, {} ya estaban al dia en este arranque.",
                    refrescadas, saltadas);
        }
    }

    private int encolarTodos(Page<Partido> partidos) {
        partidos.forEach(p -> colaPostPartido.encolar(p.getIdPartido()));
        return (int) partidos.getNumberOfElements();
    }
}
