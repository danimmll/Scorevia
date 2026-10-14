package estadisticas.services.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import estadisticas.dto.external.*;
import estadisticas.entities.arbitros.Arbitro;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.ligas.Liga;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.arbitros.ArbitroRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.ligas.LigaRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Value;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementacion del motor de ingesta de partidos.
 *
 * <p>Como Liga/Equipo/Partido usan como PK el mismo id que asigna API-Football
 * (sin {@code @GeneratedValue}), llamar a {@code repository.save(entidad)} con
 * ese id ya asignado hace de upsert automatico via JPA: si el id no existe,
 * inserta; si existe, actualiza.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartidoIngestServiceImpl implements PartidoIngestService {

    // Sustituimos ZONA_PARTIDOS por ZONA_UTC
    private static final ZoneId ZONA_UTC = ZoneId.of("UTC");

    // Código corto de API-Football para "descanso". Durante el descanso no hay
    // eventos nuevos que bajar, así que frenamos esa petición por partido en vez
    // de gastarla en cada tick del scheduler (cada eventos-de-partido es 1 llamada
    // extra a la API, aparte de la de fixtures en vivo).
    private static final String STATUS_DESCANSO = "HT";
    private static final Duration COOLDOWN_EVENTOS_DESCANSO = Duration.ofMinutes(16);

    // Códigos de API-Football para partido acabado. Además del tiempo reglamentario, la
    // prórroga y los penaltis, entran los dos que acaban sin jugarse del todo pero CON
    // resultado: AWD (pérdida por decisión) y WO (incomparecencia). Sin ellos, esos partidos
    // no se encolaban al terminar y se quedaban sin estadísticas para siempre.
    private static final Set<String> ESTADOS_FINALES = Set.of("FT", "AET", "PEN", "AWD", "WO");

    private final Map<Integer, Instant> proximoPermitidoEventosEnDescanso = new ConcurrentHashMap<>();

    /** Última huella (marcador + fase) vista por partido, para no bajar el timeline sin motivo. */
    private final Map<Integer, String> ultimaHuellaEnVivo = new ConcurrentHashMap<>();

    /** Cuándo se pidió por última vez el timeline de cada partido, para el refresco periódico. */
    private final Map<Integer, Instant> ultimaPeticionEventos = new ConcurrentHashMap<>();

    // Cada cuánto se baja el timeline de un partido en vivo aunque no se haya movido el
    // marcador. Es lo que recoge tarjetas, cambios y revisiones del VAR, que no son urgentes.
    // Subirlo ahorra cuota en tardes con muchísimos partidos; bajarlo da más frescura.
    //
    // Lleva valor inicial además del default del @Value a propósito: sin él, cualquier
    // contexto que no pase por la inyección de Spring (un test de Mockito, por ejemplo) lo
    // dejaría a 0, y un refresco "cada 0 segundos" significa pedir el timeline de todos los
    // partidos en todos los ticks -- justo el derroche que este campo existe para evitar.
    @Value("${api.football.refresco-eventos-segundos:300}")
    private long refrescoEventosSegundos = 300L;

    // A partir de cuántos partidos simultáneos se empieza a estirar ese refresco.
    //
    // El coste del refresco periódico es N/intervalo peticiones por segundo, o sea que crece
    // con el número de partidos en directo: con 1.500 a la vez (una tarde de sábado cargada)
    // y el refresco fijo en 300s son 5 peticiones por segundo -- 18.000 a la hora solo en
    // recoger tarjetas y cambios. Estirando el intervalo en proporción, ese gasto queda
    // clavado en ~1 petición/segundo pase lo que pase, y lo urgente no se toca: un gol o un
    // cambio de fase siguen bajando el timeline al instante, porque eso no depende de este
    // temporizador sino de la huella del marcador.
    @Value("${api.football.umbral-partidos-vivo:300}")
    private int umbralPartidosVivo = 300;

    /**
     * Competiciones que refrescan su timeline mas a menudo.
     *
     * <p>Por defecto las 22 que eligio el usuario: las cinco grandes, las tres competiciones
     * UEFA, Portugal, Paises Bajos, Turquia, Belgica, las segundas de Espana/Italia/Alemania
     * y la Championship, mas Brasil, Argentina, Libertadores, Sudamericana, MLS y Liga MX.</p>
     *
     * <p>El coste esta acotado: son unos pocos partidos simultaneos incluso un sabado, asi
     * que bajarles el refresco de 300 a 150 segundos no mueve la aguja de la cuota. Lo que
     * no se toca es lo urgente -- un gol o un cambio de fase siguen bajando el timeline al
     * instante, porque eso depende de la huella del marcador y no de este temporizador.</p>
     */
    @Value("${api.football.ligas-destacadas:2,3,11,13,39,40,61,71,78,79,88,94,128,135,136,140,141,144,203,253,262,848}")
    private String ligasDestacadasRaw = "2,3,11,13,39,40,61,71,78,79,88,94,128,135,136,140,141,144,203,253,262,848";

    /** Cada cuanto se refresca el timeline de una competicion destacada. */
    @Value("${api.football.refresco-eventos-destacadas-segundos:150}")
    private long refrescoEventosDestacadasSegundos = 150L;

    /** El texto de arriba ya partido, calculado una vez. */
    private volatile Set<Integer> ligasDestacadas;

    /** Cuántos partidos había en directo en el último tick. Lo fija ingestarEnVivo. */
    private volatile int partidosEnVivoUltimoTick = 0;

    /**
     * Minutos desde el inicio a partir de los cuales el minuto guardado deja de ser creible.
     *
     * <p>Son dos decisiones distintas y antes iban juntas. <b>Dar un partido por acabado</b>
     * conviene hacerlo tarde (las 4 horas de arriba): cerrar uno que sigue jugandose es peor
     * que tardar en limpiarlo. Pero <b>seguir afirmando "minuto 90"</b> no se sostiene desde
     * que la API deja de confirmarlo, y eso pasa mucho antes.</p>
     *
     * <p>Medido el 2026-09-08 sobre los partidos en juego: uno que empezo hace <b>212
     * minutos</b> seguia como EN_JUEGO en el minuto 90, y otros cuatro entre 152 y 182. En el
     * marcador se leian como partidos jugandose ahora mismo por el minuto 90.</p>
     *
     * <p>130 minutos cubren los 90 mas el descanso mas un descuento largo, asi que un partido
     * que de verdad este en juego no llega aqui. La prorroga y los penaltis pasan de 130, pero
     * esos partidos siguen apareciendo en el directo y por eso no entran en este filtro: solo
     * se limpia el minuto de los que la API <b>ya no devuelve</b>.</p>
     */
    private static final int MINUTOS_PARA_DUDAR_DEL_MINUTO = 130;

    private final ApiFootballClient apiFootballClient;
    private final LigaRepository ligaRepository;
    private final EquipoRepository equipoRepository;
    private final ArbitroRepository arbitroRepository;
    private final PartidoRepository partidoRepository;
    private final ObjectMapper objectMapper;
    private final EventoPartidoIngestService eventoPartidoIngestService;
    private final ColaPostPartido colaPostPartido;

    @Override
    public int ingestarPorFecha(String fecha) {
        ApiFootballFixturesResponseDTO respuesta = apiFootballClient.getFixturesByDate(fecha);
        return procesarRespuestaFixtures(respuesta, "fecha " + fecha, (item, partido) -> { });
    }

    @Override
    public int ingestarEnVivo() {
        ApiFootballFixturesResponseDTO respuesta = apiFootballClient.getLiveFixtures();
        // Se apunta antes de procesar: el refresco periódico de cada partido se calcula con
        // cuántos hay ahora mismo en directo, no con un valor fijo.
        partidosEnVivoUltimoTick = respuesta.getResponse() != null ? respuesta.getResponse().size() : 0;
        // Post-procesado extra solo para en vivo: cada fixture live puede traerse también su
        // timeline de eventos, pero solo cuando hace falta (ver ingestarEventosSiProcede).
        int procesados = procesarRespuestaFixtures(respuesta, "partidos en vivo", this::ingestarEventosSiProcede);
        Set<Integer> enVivoAhora = idsDe(respuesta);
        olvidarPartidosQueYaNoEstanEnVivo(enVivoAhora);
        borrarMinutoNoConfirmado(enVivoAhora);
        cerrarPartidosColgados(enVivoAhora);
        return procesados;
    }

    /**
     * Quita el minuto a los partidos que el directo ya no confirma.
     *
     * <p>El minuto solo vale mientras la API lo respalda. Cuando un partido desaparece de
     * {@code live=all}, el ultimo valor se queda congelado, y como {@code 90} es justo donde
     * se paran casi todos, el marcador acababa enseniando varios partidos "en el minuto 90"
     * que habian terminado hacia horas.</p>
     *
     * <p>Esto NO cambia el estado: de eso se encarga {@link #cerrarPartidosColgados}, que en
     * el mismo tick le pregunta a la API en que quedo. Este paso se mantiene porque esa
     * pregunta puede fallar (la API cae, se acaba la cuota), y entonces es mejor un partido
     * sin minuto que uno clavado en el 90. Si vuelve al directo, recupera su minuto.</p>
     */
    private void borrarMinutoNoConfirmado(Set<Integer> enVivoAhora) {
        LocalDateTime limite = LocalDateTime.now(ZoneOffset.UTC)
                .minusMinutes(MINUTOS_PARA_DUDAR_DEL_MINUTO);
        List<Partido> dudosos = partidoRepository.findEnJuegoColgados(limite).stream()
                .filter(p -> !enVivoAhora.contains(p.getIdPartido()))
                .filter(p -> p.getMinuto() != null)
                .toList();

        if (dudosos.isEmpty()) {
            return;
        }
        dudosos.forEach(p -> p.setMinuto(null));
        partidoRepository.saveAll(dudosos);
        log.info("{} partidos seguian ensenando minuto sin estar en el directo: minuto borrado "
                + "mientras se les pregunta por su estado real.", dudosos.size());
    }

    /**
     * Cierra los partidos que dicen estar jugandose pero que el directo ya no devuelve.
     *
     * <p>Un partido se queda clavado en EN_JUEGO cuando la ingesta no llega a verlo terminar:
     * el scheduler apagado un rato, un despliegue en mitad de la jornada, o la API que deja de
     * listarlo en {@code live=all} entre dos ticks. Medido tras un despliegue: 41 partidos de
     * verdad en directo y <b>260 fantasma</b>.</p>
     *
     * <p><b>Antes esto no cerraba nada, aunque el nombre lo prometiera.</b> Los encolaba en
     * {@link ColaPostPartido}, que trae estadisticas y timeline pero <b>no toca el estado</b>,
     * asi que el partido seguia EN_JUEGO hasta que pasara la reconciliacion: una vez al dia y
     * con seis horas de margen. Durante todo ese rato salia en el marcador como si se
     * estuviera jugando.</p>
     *
     * <p>Ahora se le pregunta a la API en que quedo, que cuesta una peticion por cada veinte.
     * Por eso tampoco hacen falta ya las cuatro horas de margen que habia: ese margen existia
     * para no <i>asumir</i> que un partido habia acabado, y aqui no se asume nada. Uno que
     * siguiera jugandose volveria con su estado en juego y no pasaria nada.</p>
     */
    private void cerrarPartidosColgados(Set<Integer> enVivoAhora) {
        LocalDateTime limite = LocalDateTime.now(ZoneOffset.UTC)
                .minusMinutes(MINUTOS_PARA_DUDAR_DEL_MINUTO);
        List<Partido> colgados = partidoRepository.findEnJuegoColgados(limite).stream()
                .filter(p -> !enVivoAhora.contains(p.getIdPartido()))
                .toList();

        if (colgados.isEmpty()) {
            return;
        }

        List<Integer> ids = colgados.stream().map(Partido::getIdPartido).toList();
        int cerrados = preguntarPorSuEstadoFinal(ids);

        // Ademas del estado, sus estadisticas y su timeline, que tampoco tenian.
        ids.forEach(colaPostPartido::encolar);
        log.info("{} partidos seguian EN_JUEGO sin estar en el directo: {} actualizados con su "
                + "estado real ({} peticiones, de veinte en veinte).",
                colgados.size(), cerrados, (colgados.size() + 19) / 20);
    }

    /**
     * Le pregunta a la API en que quedaron los partidos que ya no salen en el directo.
     *
     * <p><b>Antes esto no cerraba nada.</b> Se encolaban en {@link ColaPostPartido}, pero esa
     * cola solo trae estadisticas y timeline: no toca el estado. Asi que el partido se
     * quedaba en EN_JUEGO hasta que pasara la reconciliacion, que corre <b>una vez al dia</b>
     * y con seis horas de margen. Mientras tanto seguia saliendo en el marcador como si se
     * estuviera jugando.</p>
     *
     * <p>Preguntar por ellos cuesta una peticion por cada veinte y devuelve el estado de
     * verdad, asi que ya no hace falta esperar cuatro horas por si acaso: no se asume nada,
     * se consulta. Un partido que siguiera jugandose volveria con su estado en juego y aqui
     * no pasaria nada.</p>
     */
    private int preguntarPorSuEstadoFinal(List<Integer> ids) {
        int actualizados = 0;
        for (List<Integer> tanda : particionarIds(ids)) {
            try {
                ApiFootballFixturesResponseDTO respuesta = apiFootballClient.getFixturesByIds(tanda);
                actualizados += procesarRespuestaFixtures(respuesta, "cierre de colgados",
                        (item, partido) -> { });
            } catch (Exception e) {
                log.warn("No se pudo cerrar la tanda de colgados {}: {}", tanda, e.getMessage());
            }
        }
        return actualizados;
    }

    private List<List<Integer>> particionarIds(List<Integer> ids) {
        List<List<Integer>> tandas = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += ApiFootballClient.MAX_FIXTURES_POR_PETICION) {
            tandas.add(ids.subList(i, Math.min(i + ApiFootballClient.MAX_FIXTURES_POR_PETICION, ids.size())));
        }
        return tandas;
    }

    private Set<Integer> idsDe(ApiFootballFixturesResponseDTO respuesta) {
        return respuesta.getResponse().stream()
                .map(FixtureItemDTO::getFixture)
                .filter(Objects::nonNull)
                .map(FixtureDetailDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Guarda el timeline de este partido cuando aporta algo nuevo.
     *
     * <p>Antes se pedían los eventos de <b>todos</b> los partidos en vivo en <b>cada</b> tick.
     * Con el scheduler a 10s eso es 6 × (1 + N) peticiones por minuto, y en una tarde de
     * sábado con 1.500 partidos simultáneos salen más de 13 millones de peticiones al día:
     * 87 veces la cuota diaria del plan Mega. Por eso el scheduler estaba comentado.</p>
     *
     * <p>La clave es que el marcador, el estado <b>y el propio timeline</b> vienen todos en la
     * respuesta de {@code fixtures?live=all} — una sola petición para todos los partidos del
     * mundo. Durante mucho tiempo esto pedía además {@code /fixtures/events} por cada partido
     * movido, una petición por partido para traerse otra vez unos eventos que ya estaban en la
     * respuesta anterior. Ya no: se guardan los que vienen en el propio fixture, y por eso
     * abajo se decide <b>si escribir en la base</b>, no si gastar una petición:</p>
     *
     * <ul>
     *   <li><b>Cambia el marcador o la fase</b> (gol, descanso, prórroga, final) → se guarda ya,
     *       que es justo cuando el usuario está mirando.</li>
     *   <li><b>Refresco periódico</b> ({@code refresco-eventos}) → recoge lo que no mueve el
     *       marcador: tarjetas, cambios, VAR.</li>
     *   <li><b>Primera vez que se ve el partido</b> → hay que traer lo que ya llevara jugado.</li>
     * </ul>
     *
     * <p>Escribir cuesta un DELETE + INSERT del timeline entero, así que sigue habiendo motivo
     * para no hacerlo en cada tick de cada partido; lo que ya no hay es motivo de cuota.</p>
     *
     * <p>Cuando el partido termina, el cambio de estado a FT/AET/PEN dispara una última bajada
     * con el timeline completo y ahí queda cerrado: en los ticks siguientes ya no se vuelve a
     * pedir, y en cuanto API-Football deja de devolverlo en {@code live=all} se olvida por completo.</p>
     */
    private void ingestarEventosSiProcede(FixtureItemDTO item, Partido partido) {
        String estadoRaw = item.getFixture() != null && item.getFixture().getStatus() != null
                ? item.getFixture().getStatus().getShortStatus()
                : null;

        Integer idPartido = partido.getIdPartido();
        String huella = huellaDe(partido, estadoRaw);
        String huellaAnterior = ultimaHuellaEnVivo.put(idPartido, huella);

        boolean primeraVez = huellaAnterior == null;
        boolean haCambiado = !huella.equals(huellaAnterior);

        boolean acabaDeTerminar = estadoRaw != null && ESTADOS_FINALES.contains(estadoRaw) && haCambiado;
        if (acabaDeTerminar) {
            // El pitido final es el único momento en que se sabe que este partido ya tiene
            // estadísticas definitivas esperando en la API. Se apunta para pedirlas dentro de
            // unos minutos (ver ColaPostPartido): son otros dos endpoints distintos del
            // timeline, y son los que llenan la ficha del partido -- posesión, tiros, notas,
            // minutos y, con ellos, las alineaciones.
            colaPostPartido.encolar(idPartido);
        }

        // Partido ya terminado que sigue apareciendo un rato en el feed en vivo: se cerró en
        // el tick en el que cambió a estado final, no hay nada más que bajar.
        if (estadoRaw != null && ESTADOS_FINALES.contains(estadoRaw) && !haCambiado) {
            return;
        }

        // Durante el descanso no aparecen eventos nuevos, así que ni siquiera el refresco
        // periódico tiene sentido: se espera a que empiece la segunda parte.
        if (STATUS_DESCANSO.equals(estadoRaw) && !haCambiado) {
            Instant proximoPermitido = proximoPermitidoEventosEnDescanso.get(idPartido);
            if (proximoPermitido != null && Instant.now().isBefore(proximoPermitido)) {
                return;
            }
            proximoPermitidoEventosEnDescanso.put(idPartido, Instant.now().plus(COOLDOWN_EVENTOS_DESCANSO));
        } else if (!STATUS_DESCANSO.equals(estadoRaw)) {
            proximoPermitidoEventosEnDescanso.remove(idPartido);
        }

        Instant ahora = Instant.now();
        Instant ultimaPeticion = ultimaPeticionEventos.get(idPartido);
        boolean tocaRefresco = ultimaPeticion == null
                || ahora.isAfter(ultimaPeticion.plus(refrescoEventosDe(partido)));

        if (!primeraVez && !haCambiado && !tocaRefresco) {
            return;
        }

        ultimaPeticionEventos.put(idPartido, ahora);
        // Los eventos vienen dentro del propio fixture del feed en vivo: guardarlos no cuesta
        // ninguna petición. Comprobado contra la API el 2026-09-05: los 58 partidos en directo
        // de la respuesta traían su array "events".
        eventoPartidoIngestService.guardarEventos(partido, item.getEvents());
    }

    /** Marcador + fase: lo que hay que mirar para saber si el timeline se ha movido. */
    private String huellaDe(Partido partido, String estadoRaw) {
        return partido.getResultadoLocal() + "-" + partido.getResultadoVisitante() + "@" + estadoRaw;
    }

    /**
     * Cada cuanto se vuelve a bajar el timeline de ESTE partido.
     *
     * <p>Las competiciones destacadas van mas rapido: en un partido que la gente esta
     * mirando, enterarse de una tarjeta o de un cambio cinco minutos tarde se nota. En el
     * resto se mantiene el refresco general, que es lo que evita que una tarde con mil
     * partidos se coma la cuota.</p>
     */
    private Duration refrescoEventosDe(Partido partido) {
        if (partido.getLiga() != null && ligasDestacadas().contains(partido.getLiga().getIdLiga())) {
            return Duration.ofSeconds(refrescoEventosDestacadasSegundos);
        }
        return refrescoEventosPara(partidosEnVivoUltimoTick);
    }

    private Set<Integer> ligasDestacadas() {
        Set<Integer> actual = ligasDestacadas;
        if (actual == null) {
            actual = java.util.Arrays.stream(ligasDestacadasRaw.split(","))
                    .map(String::trim).filter(t -> !t.isEmpty())
                    .map(Integer::valueOf).collect(Collectors.toSet());
            ligasDestacadas = actual;
        }
        return actual;
    }

    /**
     * Cada cuánto se refresca el timeline de un partido, según cuántos haya en directo.
     *
     * <p>Hasta el umbral, el intervalo configurado tal cual. Por encima, se estira en la misma
     * proporción, de forma que el gasto total del refresco periódico —que es
     * {@code partidos / intervalo} peticiones por segundo— se queda plano en vez de crecer con
     * el número de partidos. Con los valores por defecto (300s, umbral 300) el techo es de una
     * petición por segundo, haya 300 partidos simultáneos o haya 3.000.</p>
     *
     * <p>Package-private para poder fijarlo en un test sin depender del reloj.</p>
     */
    Duration refrescoEventosPara(int partidosEnVivo) {
        if (umbralPartidosVivo <= 0 || partidosEnVivo <= umbralPartidosVivo) {
            return Duration.ofSeconds(refrescoEventosSegundos);
        }
        return Duration.ofSeconds(refrescoEventosSegundos * partidosEnVivo / umbralPartidosVivo);
    }

    /**
     * Suelta la memoria de los partidos que ya no están en vivo.
     *
     * <p>Sin esto, los tres mapas crecerían con cada partido visto desde que arrancó el
     * servicio y no se vaciarían nunca — un goteo lento pero seguro hasta el OutOfMemory.</p>
     */
    private void olvidarPartidosQueYaNoEstanEnVivo(Set<Integer> enVivoAhora) {
        ultimaHuellaEnVivo.keySet().retainAll(enVivoAhora);
        ultimaPeticionEventos.keySet().retainAll(enVivoAhora);
        proximoPermitidoEventosEnDescanso.keySet().retainAll(enVivoAhora);
    }

    private int procesarRespuestaFixtures(ApiFootballFixturesResponseDTO respuesta, String contexto, BiConsumer<FixtureItemDTO, Partido> postProcesado) {
        Map<Integer, Liga> ligasCache = new HashMap<>();
        Map<Integer, Equipo> equiposCache = new HashMap<>();
        int procesados = 0;

        for (FixtureItemDTO item : respuesta.getResponse()) {
            Integer idFixture = item.getFixture() != null ? item.getFixture().getId() : null;
            try {
                Partido partido = procesarFixture(item, ligasCache, equiposCache);
                postProcesado.accept(item, partido);
                procesados++;
            } catch (OptimisticLockingFailureException e) {
                // Dos sincronizaciones solapadas (manual + scheduler, o dos ticks
                // seguidos) escribiendo el mismo partido casi a la vez: la que pierde
                // la carrera se descarta sin más, la próxima ronda vuelve a traer el
                // dato al día. No es un fallo real, así que no ensucia el log en ERROR.
                log.warn("Fixture {} modificado concurrentemente, se descarta esta ronda: {}", idFixture, e.getMessage());
            } catch (Exception e) {
                log.error("No se pudo ingerir el fixture {}: {}", idFixture, e.getMessage(), e);
            }
        }

        log.info("Ingesta {} completada: {}/{} fixtures procesados", contexto, procesados, respuesta.getResults());
        return procesados;
    }

    private Partido procesarFixture(FixtureItemDTO item, Map<Integer, Liga> ligasCache, Map<Integer, Equipo> equiposCache) {
        if (item.getFixture() == null || item.getTeams() == null || item.getGoals() == null || item.getLeague() == null) {
            throw new IllegalArgumentException("Fixture recibido de API-Football con campos de nivel superior incompletos");
        }
        Liga liga = resolverLiga(item.getLeague(), ligasCache);
        Equipo local = resolverEquipo(item.getTeams().getHome(), liga, equiposCache);
        Equipo visitante = resolverEquipo(item.getTeams().getAway(), liga, equiposCache);
        Arbitro arbitro = resolverArbitro(item.getFixture().getReferee());

        Partido partido = partidoRepository.findById(item.getFixture().getId()).orElseGet(Partido::new);
        partido.setIdPartido(item.getFixture().getId());
        partido.setLiga(liga);
        partido.setTemporada(item.getLeague().getSeason());
        partido.setEquipoLocal(local);
        partido.setEquipoVisitante(visitante);
        partido.setArbitro(arbitro);
        partido.setFecha(parsearFecha(item.getFixture().getDate()));
        partido.setJornada(parsearJornada(item.getLeague().getRound()));
        // Además del número, el texto entero: en una eliminatoria es lo único que hay
        // ("Quarter-finals" no tiene jornada que sacar).
        partido.setRonda(item.getLeague().getRound());
        partido.setResultadoLocal(item.getGoals().getHome());
        partido.setResultadoVisitante(item.getGoals().getAway());
        aplicarEstadoYMinuto(partido, item.getFixture().getStatus());
        partido.setDatosApi(serializar(item));

        if (partido.getStatsNotFound() == null) {
            partido.setStatsNotFound(false);
        }
        if (partido.getIntentosIngesta() == null) {
            partido.setIntentosIngesta(0);
        }

        return partidoRepository.save(partido);
    }

    private Liga resolverLiga(LeagueInfoDTO dto, Map<Integer, Liga> cache) {
        return cache.computeIfAbsent(dto.getId(), id -> {
            Liga liga = ligaRepository.findById(id).orElseGet(Liga::new);
            liga.setIdLiga(id);
            liga.setNombre(dto.getName());
            liga.setPais(dto.getCountry());
            // FIX: no pisar temporadaActual con una temporada más antigua que la ya
            // guardada. Sin esto, un backfill de fechas históricas ejecutado después
            // de la temporada actual dejaba temporadaActual apuntando a un año viejo.
            if (liga.getTemporadaActual() == null || dto.getSeason() > liga.getTemporadaActual()) {
                liga.setTemporadaActual(dto.getSeason());
            }
            if (liga.getActiva() == null) {
                liga.setActiva(true);
            }
            return ligaRepository.save(liga);
        });
    }

    private Equipo resolverEquipo(TeamInfoDTO dto, Liga liga, Map<Integer, Equipo> cache) {
        return cache.computeIfAbsent(dto.getId(), id -> {
            Equipo equipo = equipoRepository.findById(id).orElseGet(Equipo::new);
            equipo.setIdEquipo(id);
            equipo.setNombre(dto.getName());
            equipo.setEscudoUrl(dto.getLogo());
            // FIX: aquí se hacía equipo.setLiga(liga) sin condición. Un equipo juega varias
            // competiciones a la vez (liga, copa, europea, amistosos), así que cada ingesta de
            // partidos reapuntaba el equipo a la ÚLTIMA competición sincronizada: el Real
            // Madrid acababa archivado en "International Champions Cup" y La Liga se quedaba
            // literalmente con cero equipos. La pertenencia real se deriva de Clasificacion
            // (ver EquipoServiceImpl.obtenerEquiposPorLiga), así que este campo solo sirve como
            // valor de respaldo y basta con rellenarlo la primera vez.
            if (equipo.getLiga() == null) {
                equipo.setLiga(liga);
            }
            if (equipo.getPais() == null) {
                equipo.setPais(liga.getPais());
            }
            return equipoRepository.save(equipo);
        });
    }

    private Arbitro resolverArbitro(String textoArbitro) {
        if (textoArbitro == null || textoArbitro.isBlank()) {
            return null;
        }
        String[] partes = textoArbitro.split(",", 2);
        String nombre = partes[0].trim();
        // "" en vez de null cuando la API no manda país: un UNIQUE(nombre, pais) de Postgres
        // trata cada NULL como distinto de cualquier otro NULL, así que con null el ON
        // CONFLICT de abajo nunca detectaría al mismo árbitro sin país en la siguiente
        // ingesta y crearía una fila duplicada cada vez.
        String pais = partes.length > 1 ? partes[1].trim() : "";

        return arbitroRepository.findByNombreAndPais(nombre, pais)
                .orElseGet(() -> {
                    // INSERT ... ON CONFLICT DO NOTHING (ver ArbitroRepository): si dos
                    // ingestas concurrentes ven el mismo árbitro nuevo a la vez, la que
                    // pierde la carrera contra el UNIQUE de nombre+país no lanza excepción,
                    // así que la relectura de abajo siempre encuentra la fila (la propia o
                    // la que acaba de ganar la otra ingesta) sin arrastrar una transacción
                    // abortada.
                    arbitroRepository.insertarSiNoExiste(nombre, pais);
                    return arbitroRepository.findByNombreAndPais(nombre, pais)
                            .orElseThrow(() -> new IllegalStateException(
                                    "No se pudo resolver el árbitro '" + nombre + "' tras el upsert."));
                });
    }

    private LocalDateTime parsearFecha(String fechaIso) {
        if (fechaIso == null) {
            return null;
        }
        return OffsetDateTime.parse(fechaIso).atZoneSameInstant(ZONA_UTC).toLocalDateTime();
    }

    /**
     * "Regular Season - 12" -> 12. Las rondas sin número ("Quarter-finals") quedan sin jornada,
     * y para esas está {@code Partido.ronda}, que guarda el texto entero.
     */
    private Integer parsearJornada(String round) {
        if (round == null) {
            return null;
        }

        String texto = round.trim();

        int i = texto.length() - 1;

        while (i >= 0 && Character.isDigit(texto.charAt(i))) {
            i--;
        }

        if (i == texto.length() - 1) {
            return null;
        }

        try {
            return Integer.valueOf(texto.substring(i + 1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Deja en el partido el estado agrupado, el codigo corto original y el minuto de juego.
     *
     * <p>Los tres salen de la misma respuesta, que ya se estaba pidiendo: no cuesta ninguna
     * peticion extra. {@code estado} es el que usa la logica de negocio (cuatro cajones),
     * {@code estadoCorto} el que necesita el marcador para distinguir un descanso de un
     * minuto 70, y {@code minuto} el numero que se pinta al lado.</p>
     *
     * <p>El minuto se borra en cuanto el partido deja de estar en juego. Si no, un partido
     * terminado se quedaria para siempre con el "90'" del ultimo tick del directo al lado
     * del resultado final, y eso se lee como que sigue jugandose.</p>
     */
    private void aplicarEstadoYMinuto(Partido partido, FixtureStatusDTO status) {
        String estado = mapearEstado(status);
        partido.setEstado(estado);
        partido.setEstadoCorto(status != null ? status.getShortStatus() : null);
        partido.setMinuto("EN_JUEGO".equals(estado) && status != null ? status.getElapsed() : null);
    }

    private String mapearEstado(FixtureStatusDTO status) {
        if (status == null || status.getShortStatus() == null) {
            return "DESCONOCIDO";
        }
        // El agrupado sigue la columna TYPE de la tabla de estados de la documentación de
        // API-Football, que es la que dice qué es cada código de verdad:
        //
        //  - SUSP e INT son *In Play*, no aplazados: el árbitro para el partido y se reanuda
        //    (INT, "en unos minutos") o se decide más tarde (SUSP). Marcándolos APLAZADO
        //    desaparecían del directo y no volvían nunca, porque la ingesta en vivo solo mira
        //    los que la API sigue devolviendo en live=all y el estado ya no cuadraba.
        //  - AWD (pérdida por decisión) y WO (incomparecencia) son partidos *terminados* con
        //    resultado, no aplazados. Como no caían en FT/AET/PEN, nunca se les pedían las
        //    estadísticas ni se encolaban al acabar.
        //  - ABD (suspendido definitivamente) sí se queda con los aplazados: puede que se
        //    repita otro día o que no se juegue, pero desde luego no terminó.
        return switch (status.getShortStatus()) {
            case "NS", "TBD" -> "PROGRAMADO";
            case "1H", "2H", "HT", "ET", "BT", "P", "LIVE", "SUSP", "INT" -> "EN_JUEGO";
            case "FT", "AET", "PEN", "AWD", "WO" -> "FINALIZADO";
            case "PST", "CANC", "ABD" -> "APLAZADO";
            // Un codigo que no conocemos NO se copia tal cual a `estado`. Ese `default ->
            // getShortStatus()` que habia aqui es lo que dejo 792 filas con 'Canc', 'Abd',
            // 'TBD' y 'Abandoned' metidos en la columna del estado agrupado, que solo
            // deberia tener cuatro valores: cualquier consulta por estado se los saltaba,
            // porque no son ninguno de los cuatro. El codigo crudo ya se guarda entero en
            // `estadoCorto`, asi que aqui no se pierde nada por caer en el cajon de
            // desconocidos; y el WARN es para enterarnos si la API estrena un codigo, en
            // vez de descubrirlo tres meses despues contando filas raras.
            default -> {
                log.warn("Codigo de estado no contemplado en la API: '{}'. Se guarda como "
                        + "DESCONOCIDO; el codigo original queda en estadoCorto.",
                        status.getShortStatus());
                yield "DESCONOCIDO";
            }
        };
    }

    private String serializar(FixtureItemDTO item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            log.warn("No se pudo serializar el fixture {} para el cache JSONB: {}",
                    item.getFixture() != null ? item.getFixture().getId() : "?", e.getMessage());
            return null;
        }
    }
}