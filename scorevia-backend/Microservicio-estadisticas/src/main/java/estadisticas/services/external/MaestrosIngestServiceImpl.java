package estadisticas.services.external;

import com.fasterxml.jackson.databind.JsonNode;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.entrenadores.HistorialEntrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.equipos.Estadio;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.entities.ligas.Liga;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.entrenadores.HistorialEntrenadorRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.equipos.EstadioRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.ligas.LigaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaestrosIngestServiceImpl implements MaestrosIngestService {

    private static final String RESPONSE = "response";
    private static final String GOALS = "goals";
    private static final String TEAM = "team";
    private static final String VENUE = "venue";
    private static final String LEAGUE = "league";
    private static final String STANDINGS = "standings";
    private static final String ALL = "all";
    private static final String PLAYER = "player";
    private static final String NUMBER = "number";
    private static final String POSITION = "position";
    private static final String STATISTICS = "statistics";
    private static final String GAMES = "games";
    private static final String FLD_ID = "id";
    private static final String FLD_NAME = "name";
    private static final String FLD_LOGO = "logo";
    private static final String FLD_COUNTRY = "country";
    private static final String FLD_NATIONALITY = "nationality";

    // FIX SonarQube [java:S1192]: Constantes para literales repetidos
    private static final String FLD_BIRTH = "birth";
    private static final String FLD_PLACE = "place";

    @Value("${scorevia.temporada-actual:2026}")
    private Integer temporadaActual;

    private final ApiFootballClient apiClient;
    private final EquipoRepository equipoRepository;
    private final EstadioRepository estadioRepository;
    private final EntrenadorRepository entrenadorRepository;
    private final HistorialEntrenadorRepository historialEntrenadorRepository;
    private final ClasificacionRepository clasificacionRepository;
    private final LigaRepository ligaRepository;
    private final JugadorRepository jugadorRepository;
    private final estadisticas.services.ligas.LigasDestacadas ligasDestacadas;
    private final estadisticas.services.ligas.ClubesDestacados clubesDestacados;

    // =================================================================================
    // LIGAS
    // =================================================================================
    @Override
    @Transactional
    public void ingestarLigasMasivas(Integer temporada) {
        log.info("Sincronizando todas las ligas maestras para la temporada {}...", temporada);
        JsonNode response = apiClient.getLeagues(temporada);
        JsonNode leaguesArray = response.get(RESPONSE);

        if (esRespuestaInvalida(leaguesArray)) return;

        List<Liga> ligasGuardar = new ArrayList<>();

        for (JsonNode node : leaguesArray) {
            JsonNode leagueNode = node.get(LEAGUE);
            JsonNode countryNode = node.get(FLD_COUNTRY);

            if (leagueNode == null || !leagueNode.hasNonNull(FLD_ID)) continue;

            Integer idLiga = leagueNode.get(FLD_ID).asInt();
            Liga liga = ligaRepository.findById(idLiga).orElse(new Liga());

            liga.setIdLiga(idLiga);
            liga.setNombre(leagueNode.path(FLD_NAME).asText("Liga Desconocida"));
            liga.setLogo(leagueNode.path(FLD_LOGO).asText(null));
            // FIX: antes se pisaba temporadaActual con el parámetro "temporada" de esta
            // sincronización SIEMPRE, así que un resync histórico (p.ej. de datos de 2010)
            // dejaba marcada esa liga como si su temporada vigente fuera 2010. La API devuelve,
            // para cada temporada consultada, si esa temporada es la "current" de esa liga --
            // solo confiamos en el parámetro cuando la propia API lo confirma como vigente.
            if (esTemporadaVigente(node, temporada)) {
                liga.setTemporadaActual(temporada);
            }
            liga.setActiva(true);

            if (countryNode != null) {
                liga.setPais(countryNode.path(FLD_NAME).asText(null));
                liga.setBandera(countryNode.path("flag").asText(null));
            }

            aplicarCobertura(liga, node, temporada);

            ligasGuardar.add(liga);
        }

        if (!ligasGuardar.isEmpty()) {
            ligaRepository.saveAll(ligasGuardar);
            log.info("Se han guardado/actualizado {} ligas maestras.", ligasGuardar.size());
        }
    }

    /**
     * Guarda qué publica API-Football de esta competición.
     *
     * <p>Viene <b>gratis</b> dentro de la misma respuesta de {@code /leagues}, en
     * {@code seasons[].coverage.fixtures}, y es lo que permite que la ingesta automática de
     * partidos finalizados no gaste peticiones a ciegas: de los partidos de 2026 ya acabados
     * que hay en la base, 9 de cada 10 no tienen estadísticas porque su competición no las
     * da (amistosos de clubes, filiales, ligas regionales). Preguntar por ellos son dos
     * peticiones por partido para guardar cero filas.</p>
     *
     * <p>Solo se escribe cuando la respuesta trae de verdad el bloque de cobertura de la
     * temporada pedida: si no viene, se deja lo que hubiera (posiblemente null, que significa
     * "no se sabe" y hace que se intente igual) en vez de marcar la liga como sin cobertura
     * por un dato ausente.</p>
     */
    private void aplicarCobertura(Liga liga, JsonNode leagueEntryNode, Integer temporada) {
        JsonNode seasons = leagueEntryNode.get("seasons");
        if (seasons == null || !seasons.isArray()) return;

        for (JsonNode season : seasons) {
            if (season.path("year").asInt(-1) != temporada) continue;

            JsonNode coverage = season.path("coverage");
            JsonNode fixtures = coverage.path("fixtures");
            if (fixtures.isMissingNode() || !fixtures.isObject()) return;

            // standings cuelga de coverage, no de coverage.fixtures.
            liga.setCoberturaClasificacion(coverage.path("standings").asBoolean(false));
            liga.setCoberturaStatsPartido(fixtures.path("statistics_fixtures").asBoolean(false));
            liga.setCoberturaStatsJugadores(fixtures.path("statistics_players").asBoolean(false));
            liga.setCoberturaEventos(fixtures.path("events").asBoolean(false));
            liga.setCoberturaAlineaciones(fixtures.path("lineups").asBoolean(false));
            liga.setCoberturaActualizada(java.time.LocalDateTime.now());
            return;
        }
    }

    // La API, para el nodo de una liga, incluye "seasons": [{ "year": ..., "current": bool, ... }].
    // Al consultar /leagues?season=X ese array trae (normalmente) solo la temporada X, con su
    // propio flag "current" -- true si X es de verdad la temporada en curso de esa liga, false
    // si X es una temporada pasada que se está reingestando (backfill histórico).
    private boolean esTemporadaVigente(JsonNode leagueEntryNode, Integer temporada) {
        JsonNode seasons = leagueEntryNode.get("seasons");
        if (seasons == null || !seasons.isArray()) return true; // sin info de temporadas, no bloquear el primer guardado
        for (JsonNode season : seasons) {
            if (season.path("year").asInt(-1) == temporada) {
                return season.path("current").asBoolean(false);
            }
        }
        return false;
    }

    // =================================================================================
    // EQUIPOS Y ESTADIOS
    // =================================================================================
    @Override
    @Transactional
    public void ingestarEquiposYEstadiosDeLiga(Integer idLiga, Integer temporada) {
        log.info("Sincronizando equipos y estadios de la liga {} temporada {}", idLiga, temporada);
        JsonNode response = apiClient.getTeamsByLeagueAndSeason(idLiga, temporada);
        JsonNode equiposArray = response.get(RESPONSE);

        if (esRespuestaInvalida(equiposArray)) return;

        Liga liga = ligaRepository.findById(idLiga).orElse(null);
        equiposArray.forEach(node -> procesarEquipo(node, liga, temporada));
    }

    private void procesarEquipo(JsonNode node, Liga liga, Integer temporada) {
        Estadio estadio = guardarEstadio(node.get(VENUE));
        guardarEquipo(node.get(TEAM), estadio, liga, temporada);
    }

    private Estadio guardarEstadio(JsonNode venueNode) {
        if (venueNode == null || venueNode.isNull() || !venueNode.hasNonNull(FLD_ID)) return null;

        Integer idEstadio = venueNode.get(FLD_ID).asInt();
        Estadio estadio = estadioRepository.findById(idEstadio).orElse(new Estadio());

        estadio.setIdEstadio(idEstadio);
        estadio.setNombre(venueNode.path(FLD_NAME).asText(null));
        estadio.setCiudad(venueNode.path("city").asText(null));
        estadio.setCapacidad(venueNode.hasNonNull("capacity") ? venueNode.get("capacity").asInt() : 0);
        estadio.setSuperficie(venueNode.path("surface").asText(null));
        estadio.setFotoUrl(venueNode.path("image").asText(null));

        return estadioRepository.save(estadio);
    }

    private void guardarEquipo(JsonNode teamNode, Estadio estadio, Liga liga, Integer temporada) {
        if (teamNode == null || teamNode.isNull() || !teamNode.hasNonNull(FLD_ID)) return;

        Integer idEquipo = teamNode.get(FLD_ID).asInt();
        Equipo equipo = equipoRepository.findById(idEquipo).orElse(new Equipo());

        equipo.setIdEquipo(idEquipo);
        equipo.setNombre(teamNode.path(FLD_NAME).asText(null));
        equipo.setPais(teamNode.path(FLD_COUNTRY).asText(null));
        equipo.setEscudoUrl(teamNode.path(FLD_LOGO).asText(null));
        equipo.setCodigo(teamNode.path("code").asText(null));
        equipo.setFundacion(teamNode.hasNonNull("founded") ? teamNode.get("founded").asInt() : null);
        equipo.setEstadio(estadio);

        if (liga != null && temporada.equals(temporadaActual)) {
            equipo.setLiga(liga);
        }

        equipoRepository.save(equipo);
    }

    // =================================================================================
    // ENTRENADORES
    // =================================================================================
    @Override
    public void ingestarEntrenadoresPorEquipo() {
        log.info("Sincronizando entrenadores de todos los equipos en BD por lotes...");
        int numeroPagina = 0;
        int tamanioLote = 100;
        Page<Equipo> paginaEquipos;

        do {
            paginaEquipos = equipoRepository.findAll(PageRequest.of(numeroPagina, tamanioLote));

            for (Equipo equipo : paginaEquipos.getContent()) {
                if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                    log.warn("Cuota agotada. Deteniendo ingesta de entrenadores.");
                    return;
                }
                procesarEntrenadoresDeUnEquipo(equipo);
            }
            numeroPagina++;
        } while (paginaEquipos.hasNext());

        log.info("Ingesta de entrenadores finalizada.");
    }

    /**
     * Refresca los entrenadores solo de las competiciones destacadas.
     *
     * <p><b>Por que hace falta esta version.</b> {@link #ingestarEntrenadoresPorEquipo()}
     * recorre los <b>28.025 equipos</b> de la base, o sea 28.000 peticiones. Con ese coste no
     * se lanza nunca, y por eso el dato se pudre: el 2026-09-08 teniamos a Mourinho en el
     * Benfica cuando la API ya decia Real Madrid desde el 1 de julio -- su etapa en el Benfica
     * habia terminado en mayo.</p>
     *
     * <p>Acotado a las destacadas son <b>260 equipos</b>, una peticion cada uno. Eso si se
     * puede lanzar a diario sin pensarlo. La logica de dentro no cambia: {@code /coachs?team=X}
     * devuelve el historial completo del club y {@code guardarEntrenador} ya se encarga de
     * enlazar solo al que sigue ahi y de soltar al que ya no.</p>
     */
    @Override
    public int refrescarEntrenadoresDestacados() {
        // Por club y no por equipos.id_liga: esa FK la pisa la ultima ingesta y dejaba fuera a
        // los clubes grandes, que son justo los unicos que importan aqui (Mourinho).
        List<Equipo> equipos = equipoRepository.findAllById(clubesDestacados.ids());
        log.info("Entrenadores: refrescando {} equipos de las competiciones destacadas.", equipos.size());

        int procesados = 0;
        for (Equipo equipo : equipos) {
            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Entrenadores: cuota agotada tras {} equipos.", procesados);
                return procesados;
            }
            procesarEntrenadoresDeUnEquipo(equipo);
            procesados++;
        }
        log.info("Entrenadores: {} equipos destacados refrescados.", procesados);
        return procesados;
    }

    private void procesarEntrenadoresDeUnEquipo(Equipo equipo) {
        try {
            JsonNode response = apiClient.getCoachsByTeam(equipo.getIdEquipo());
            JsonNode dataArray = response.get(RESPONSE);

            if (dataArray != null && dataArray.isArray()) {
                dataArray.forEach(node -> guardarEntrenador(node, equipo));
            }
        } catch (Exception e) {
            log.error("Error al ingestar entrenadores del equipo {}: {}", equipo.getIdEquipo(), e.getMessage());
        }
    }

    private void guardarEntrenador(JsonNode node, Equipo equipo) {
        if (!node.hasNonNull(FLD_ID)) return;

        Integer idEntrenador = node.get(FLD_ID).asInt();
        Entrenador entrenador = entrenadorRepository.findById(idEntrenador).orElse(null);

        // FIX: /coachs?team=X devuelve el HISTORIAL completo de técnicos de ese equipo (Zidane,
        // Ancelotti...), no solo el actual. Antes se enlazaba a "equipo" cualquier entrenador de
        // esa lista sin comprobar si de verdad sigue ahí -- por eso salían entrenadores que ya no
        // están en el club. El propio nodo trae su "team" actual; si no coincide con el equipo
        // que se está ingiriendo, este entrenador ya no está aquí.
        boolean esActualEnEsteEquipo = node.path(TEAM).path(FLD_ID).asInt(-1) == equipo.getIdEquipo();

        if (!esActualEnEsteEquipo) {
            // Si ya teníamos un enlace (ahora obsoleto) a este equipo, se limpia -- su equipo
            // real, si tiene uno, se fijará solo cuando se ingiera ESE equipo.
            if (entrenador != null && entrenador.getEquipo() != null
                    && entrenador.getEquipo().getIdEquipo().equals(equipo.getIdEquipo())) {
                entrenador.setEquipo(null);
                entrenadorRepository.save(entrenador);
            }
            return;
        }

        if (entrenador == null) entrenador = new Entrenador();
        entrenador.setIdEntrenador(idEntrenador);

        String nombre = node.path(FLD_NAME).asText(null);
        entrenador.setNombre(nombre != null ? nombre : "Desconocido");
        entrenador.setEdad(node.hasNonNull("age") ? node.get("age").asInt() : null);
        entrenador.setNacionalidad(node.path(FLD_NATIONALITY).asText(null));
        entrenador.setFoto(node.path("photo").asText(null));
        entrenador.setEquipo(equipo);

        entrenadorRepository.save(entrenador);
        registrarHistorialEntrenador(entrenador, equipo);
    }

    // La tabla historial_entrenadores existía en el esquema pero nada la rellenaba todavía --
    // cada vez que se confirma que un técnico sigue en este equipo se deja constancia para la
    // temporada actual, así el historial de banquillo se construye hacia delante a partir de
    // ahora (no hay forma fiable de reconstruir el pasado sin el array "career" de la API, que
    // no se está parseando en este proyecto).
    private void registrarHistorialEntrenador(Entrenador entrenador, Equipo equipo) {
        boolean yaRegistrado = historialEntrenadorRepository
                .findByEntrenador_IdEntrenadorAndEquipo_IdEquipoAndTemporada(entrenador.getIdEntrenador(), equipo.getIdEquipo(), temporadaActual)
                .isPresent();
        if (yaRegistrado) return;

        HistorialEntrenador historial = new HistorialEntrenador();
        historial.setEntrenador(entrenador);
        historial.setEquipo(equipo);
        historial.setTemporada(temporadaActual);
        historialEntrenadorRepository.save(historial);
    }

    // =================================================================================
    // CLASIFICACIONES
    // =================================================================================
    @Override
    @Transactional
    public void ingestarClasificaciones(Integer idLiga, Integer temporada) {
        log.info("Sincronizando clasificación de la liga {} temporada {}", idLiga, temporada);
        JsonNode response = apiClient.getStandings(idLiga, temporada);
        JsonNode dataArray = response.get(RESPONSE);

        if (esRespuestaInvalida(dataArray)) return;

        JsonNode standingsArray = dataArray.get(0).path(LEAGUE).path(STANDINGS);
        if (esRespuestaInvalida(standingsArray)) return;

        // La API devuelve un array de GRUPOS (fase de grupos, conferencias, playoffs) y una
        // liga de tabla única solo trae un grupo, pero quedarse solo con el primero dejaba
        // fuera en silencio cualquier grupo adicional en competiciones con varios.
        //
        // El nombre del grupo que manda la API NO es único: hay competiciones que devuelven
        // dos bloques distintos etiquetados igual (el Catarinense manda dos veces
        // "Catarinense: Regular season"), y guardándolos con el mismo nombre vuelven a
        // quedar mezclados -- que es justo lo que la columna existe para evitar. Cuando un
        // nombre se repite, se le añade el número de bloque.
        Set<String> nombresVistos = new HashSet<>();
        for (int i = 0; i < standingsArray.size(); i++) {
            JsonNode bloque = standingsArray.get(i);
            if (bloque == null || !bloque.isArray() || bloque.isEmpty()) continue;
            guardarBloque(bloque, i, idLiga, temporada, nombresVistos);
        }
    }

    /**
     * Guarda un bloque de clasificación, partiéndolo si trae varios subgrupos dentro.
     *
     * <p>La API no siempre separa los grupos en bloques distintos: el Catarinense devuelve
     * UN array de 12 equipos, todos etiquetados "Catarinense: Regular season", con los
     * puestos {@code [1,2,3,4,5,6,1,2,3,4,5,6]} -- dos grupos de seis concatenados. Lo único
     * que los distingue es que la posición vuelve a empezar, así que se corta por ahí.</p>
     */
    private void guardarBloque(JsonNode bloque, int indiceBloque, Integer idLiga,
                               Integer temporada, Set<String> nombresVistos) {
        String nombreBase = bloque.get(0).path("group").asText(null);
        int posicionAnterior = Integer.MIN_VALUE;
        String nombreGrupo = null;
        int subgrupo = 0;

        for (JsonNode teamRow : bloque) {
            int posicion = teamRow.path("rank").asInt();

            // Una posición que no avanza es el comienzo de otro grupo.
            if (posicion <= posicionAnterior || posicionAnterior == Integer.MIN_VALUE) {
                subgrupo++;
                nombreGrupo = etiquetarGrupo(nombreBase, indiceBloque, subgrupo, nombresVistos);
            }
            posicionAnterior = posicion;

            guardarClasificacion(teamRow, idLiga, temporada, nombreGrupo);
        }
    }

    /** Etiqueta única para un grupo dentro de la competición. */
    private String etiquetarGrupo(String nombreBase, int indiceBloque, int subgrupo, Set<String> nombresVistos) {
        if (nombreBase == null || nombreBase.isBlank()) {
            // Una liga de tabla única no manda grupo: null para que la pantalla la pinte
            // como una sola tabla, sin cabecera.
            return indiceBloque == 0 && subgrupo == 1 ? null : "Grupo " + (nombresVistos.size() + 1);
        }
        if (nombresVistos.add(nombreBase)) return nombreBase;

        // Nombre ya usado: se numera. Se comprueba en bucle porque el sufijo también
        // podría chocar si la API repitiera la etiqueta más de dos veces.
        int sufijo = 2;
        while (!nombresVistos.add(nombreBase + " (" + sufijo + ")")) {
            sufijo++;
        }
        return nombreBase + " (" + sufijo + ")";
    }

    private void guardarClasificacion(JsonNode teamRow, Integer idLiga, Integer temporada, String nombreGrupo) {
        JsonNode teamNode = teamRow.get(TEAM);
        if (teamNode == null || !teamNode.hasNonNull(FLD_ID)) return;

        Integer idEquipo = teamNode.get(FLD_ID).asInt();
        Liga liga = ligaRepository.findById(idLiga).orElse(null);
        Equipo equipo = equipoRepository.findById(idEquipo).orElse(null);

        if (liga == null || equipo == null) {
            log.warn("Clasificación ignorada: liga {} o equipo {} no encontrados en BD.", idLiga, idEquipo);
            return;
        }

        Clasificacion clasificacion = clasificacionRepository
                .findByLiga_IdLigaAndEquipo_IdEquipoAndTemporada(idLiga, idEquipo, temporada)
                .orElse(new Clasificacion());

        clasificacion.setLiga(liga);
        clasificacion.setEquipo(equipo);
        clasificacion.setTemporada(temporada);
        clasificacion.setPosicion(teamRow.path("rank").asInt());
        clasificacion.setPuntos(teamRow.path("points").asInt());
        clasificacion.setEstadoCompeticion(teamRow.path("description").asText(null));
        // El grupo lo manda la API en cada fila y no se estaba leyendo. Sin él, una
        // competición con fase de grupos queda como una lista de equipos con la posición
        // repetida una vez por grupo, imposible de mostrar como clasificación.
        clasificacion.setGrupo(nombreGrupo);

        extraerEstadisticasClasificacion(clasificacion, teamRow);

        clasificacionRepository.save(clasificacion);
    }

    private void extraerEstadisticasClasificacion(Clasificacion clasificacion, JsonNode teamRow) {
        JsonNode allNode = teamRow.path(ALL);
        if (allNode.isMissingNode() || allNode.isNull()) return;

        clasificacion.setPartidosJugados(allNode.path("played").asInt());
        clasificacion.setPartidosGanados(allNode.path("win").asInt());
        clasificacion.setPartidosEmpatados(allNode.path("draw").asInt());
        clasificacion.setPartidosPerdidos(allNode.path("lose").asInt());

        JsonNode goalsNode = allNode.path(GOALS);
        if (!goalsNode.isMissingNode() && !goalsNode.isNull()) {
            int golesFavor = goalsNode.path("for").asInt();
            int golesContra = goalsNode.path("against").asInt();
            clasificacion.setGolesFavor(golesFavor);
            clasificacion.setGolesContra(golesContra);
            // La API-Football no manda una diferencia de goles ya calculada en este nodo (a
            // diferencia de "for"/"against") -- se queda null si no se deriva aquí, columna DG
            // vacía en la tabla de clasificación.
            clasificacion.setDiferenciaGoles(golesFavor - golesContra);
        }
    }

    // =================================================================================
    // JUGADORES (Completos y Perfiles)
    // =================================================================================
    /** Cada cuántos equipos se deja una línea de progreso en el log. */
    private static final int EQUIPOS_ENTRE_AVISOS = 250;

    /** Cuántos equipos pendientes se piden de golpe. */
    private static final int LOTE_EQUIPOS = 200;

    /**
     * Baja la plantilla de cada equipo activo en la temporada.
     *
     * <p>Es reanudable: cada equipo terminado se anota en {@code plantillas_sincronizadas},
     * y al relanzar se piden solo los que faltan. Sin eso, cada corte —una cuota agotada, o
     * simplemente un despliegue a media ingesta— obligaba a repetir las ~17.000 peticiones
     * desde el primer equipo.</p>
     *
     * <p>Se piden lotes en vez de paginar: la lista de pendientes encoge a medida que se
     * procesa, así que paginar sobre ella con offsets iría saltándose equipos.</p>
     */
    @Override
    public void ingestarJugadoresCompletosPorEquipo(Integer temporada) {
        ingestarJugadoresCompletosPorEquipo(temporada, false, 0);
    }

    @Override
    public void ingestarJugadoresCompletosPorEquipo(Integer temporada, boolean soloDestacadas, int maxHoras) {
        long arranque = System.currentTimeMillis();
        int procesados = 0;
        int sinRespuesta = 0;

        // Cada equipo, una vez por pasada. El bucle se apoyaba en que marcar el equipo lo
        // sacaba de la consulta del lote siguiente, y eso deja de ser cierto en cuanto la
        // consulta ignora la marca: con maxHoras < 0 ("repásalos todos") el mismo lote de 200
        // volvía una y otra vez, y la ingesta se quedó dando vueltas gastando peticiones hasta
        // que la paré a mano. Llevar la cuenta aquí no depende de qué filtre la consulta.
        Set<Integer> yaVistos = new HashSet<>();

        List<Equipo> lote = siguienteLotePendiente(temporada, soloDestacadas, maxHoras, yaVistos);
        log.info("Sincronizando plantillas de la temporada {} ({}, se refresca: {}): "
                        + "{} pendientes en este lote.",
                temporada, soloDestacadas ? "solo destacadas" : "todas las competiciones",
                descripcionRefresco(maxHoras), lote.size());

        while (!lote.isEmpty()) {
            for (Equipo equipo : lote) {
                if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                    log.warn("Cuota agotada tras {} equipos. Al relanzar se retoma donde se quedó.", procesados);
                    return;
                }
                yaVistos.add(equipo.getIdEquipo());
                // Solo se anota como hecho lo que de verdad se ha traido: ver el javadoc de
                // procesarPlantillaSeguro.
                if (procesarPlantillaSeguro(equipo, temporada)) {
                    equipoRepository.marcarPlantillaSincronizada(equipo.getIdEquipo(), temporada);
                    procesados++;
                } else {
                    sinRespuesta++;
                }

                // Sin esto no había forma de saber por dónde iba: solo quedaba estimar el
                // avance midiendo el consumo de cuota desde fuera.
                if (procesados % EQUIPOS_ENTRE_AVISOS == 0) {
                    log.info("Plantillas {}: {} equipos en {} min.",
                            temporada, procesados, (System.currentTimeMillis() - arranque) / 60000);
                }
            }
            lote = siguienteLotePendiente(temporada, soloDestacadas, maxHoras, yaVistos);
        }

        log.info("Plantillas {} terminadas: {} equipos procesados en esta pasada, en {} min"
                        + "{}.",
                temporada, procesados, (System.currentTimeMillis() - arranque) / 60000,
                sinRespuesta > 0 ? " (" + sinRespuesta + " sin respuesta de la API, sin marcar)" : "");
    }

    /**
     * El siguiente lote de equipos a los que pedir plantilla.
     *
     * <p>Ya no se cuenta antes cuántos equipos activos hay: ese {@code count(*)} con un EXISTS
     * sobre 1,4M de partidos tardaba 80 segundos, disparaba el detector de fugas de conexión
     * de Hikari y solo servía para una línea de log.</p>
     */
    private String descripcionRefresco(int maxHoras) {
        if (maxHoras < 0) return "todos, tengan la marca que tengan";
        if (maxHoras == 0) return "nunca: solo los que no se han mirado";
        return "más de " + maxHoras + " h";
    }

    private List<Equipo> siguienteLotePendiente(Integer temporada, boolean soloDestacadas,
                                               int maxHoras, Set<Integer> yaVistos) {
        // Se pide un lote más grande de la cuenta y se descartan los ya hechos, en vez de
        // pasarle la lista de vistos a la consulta: con 11.508 equipos, ese NOT IN acabaría
        // siendo una lista de miles de ids en cada vuelta.
        List<Equipo> lote = soloDestacadas
                ? equipoRepository.findEquiposPendientesDePlantillaEnClubes(
                        temporada, clubesDestacados.ids(), maxHoras, LOTE_EQUIPOS + yaVistos.size())
                : equipoRepository.findEquiposPendientesDePlantilla(
                        temporada, maxHoras, LOTE_EQUIPOS + yaVistos.size());
        return lote.stream()
                .filter(e -> !yaVistos.contains(e.getIdEquipo()))
                .limit(LOTE_EQUIPOS)
                .toList();
    }

    /**
     * Baja la plantilla de un equipo.
     *
     * @return true si la API devolvio plantilla. False significa "no lo se" (respuesta vacia,
     *         error de red, limite por minuto), y entonces el equipo NO debe marcarse como
     *         sincronizado: marcarlo igualmente es lo que dejo a Kroos, Bale, Hazard y Dudek en
     *         el Real Madrid el 2026-09-09 despues de una pasada que sí recorrio el club. La
     *         peticion se perdio, el equipo quedo anotado como hecho, y con la marca puesta ya
     *         no se volvia a intentar.
     */
    private boolean procesarPlantillaSeguro(Equipo equipo, Integer temporada) {
        try {
            int pagina = 1;
            int totalPaginas = 1;
            boolean completa = true;
            Set<Integer> enLaPlantilla = new HashSet<>();

            do {
                JsonNode response = apiClient.getPlayersByTeamAndSeason(equipo.getIdEquipo(), temporada, pagina);
                JsonNode dataArray = response.get(RESPONSE);

                if (esRespuestaInvalida(dataArray)) {
                    completa = false;
                    break;
                }

                if (pagina == 1) {
                    totalPaginas = extraerTotalPaginas(response);
                }

                enLaPlantilla.addAll(guardarJugadoresDePagina(dataArray, equipo, temporada));

                pagina++;

            } while (pagina <= totalPaginas);

            if (enLaPlantilla.isEmpty()) {
                log.warn("La API no ha devuelto plantilla de {} ({}): se reintentara.",
                        equipo.getNombre(), equipo.getIdEquipo());
                return false;
            }

            // Una plantilla A MEDIAS es tan peligrosa como una vacía, y menos evidente: el
            // 2026-09-09 la página 2 del Real Madrid se perdió, y soltar "a los que no vienen
            // en la lista" dejó al club con los 20 de la página 1. Si falta alguna página no
            // se suelta a nadie y el equipo se queda pendiente para la próxima pasada.
            if (!completa) {
                log.warn("Plantilla de {} incompleta (llegaron {} de {} páginas): no se suelta a nadie.",
                        equipo.getNombre(), pagina - 1, totalPaginas);
                return false;
            }

            if (temporada.equals(temporadaActual)) {
                soltarALosQueYaNoEstan(equipo, enLaPlantilla);
            }
            return true;

        } catch (Exception e) {
            log.error("Error al ingestar la plantilla del equipo {}: {}", equipo.getIdEquipo(), e.getMessage());
            return false;
        }
    }

    /**
     * Quita el equipo a quien ya no aparece en su plantilla.
     *
     * <p>Este paso faltaba entero. `jugador.equipo` se usa como "equipo actual" pero solo se
     * ASIGNABA: nada lo quitaba nunca, asi que la plantilla de un club acababa siendo todo el
     * que hubiera pasado por el. Medido el 2026-09-08: <b>Toni Kroos seguia en el Real
     * Madrid</b>, con `fecha_equipo_actualizado` del 2024-05-08 -- su ultimo partido antes de
     * retirarse -- y el Madrid figuraba con 46 jugadores.</p>
     *
     * <p>Se hace solo con la temporada en curso y solo si la API devolvio plantilla: una
     * respuesta vacia significa "no lo se", no "no queda nadie", y vaciar el club entero por
     * un fallo de la API seria mucho peor que la lista larga.</p>
     */
    private void soltarALosQueYaNoEstan(Equipo equipo, Set<Integer> enLaPlantilla) {
        if (enLaPlantilla.isEmpty()) {
            return;
        }
        int soltados = jugadorRepository.desvincularLosQueYaNoEstan(
                equipo.getIdEquipo(), new ArrayList<>(enLaPlantilla));
        if (soltados > 0) {
            log.info("Plantilla de {}: {} jugadores ya no estan y se han desvinculado.",
                    equipo.getNombre(), soltados);
        }
    }

    private boolean esRespuestaInvalida(JsonNode dataArray) {
        return dataArray == null || !dataArray.isArray() || dataArray.isEmpty();
    }

    private int extraerTotalPaginas(JsonNode response) {
        if (response.hasNonNull("paging")) {
            return response.get("paging").path("total").asInt(1);
        }
        return 1;
    }

    /** Guarda la pagina y devuelve los ids que venian en ella. */
    private Set<Integer> guardarJugadoresDePagina(JsonNode dataArray, Equipo equipo, Integer temporada) {
        List<Jugador> jugadoresGuardar = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();

        for (JsonNode playerRow : dataArray) {
            Jugador jug = mapearJugador(playerRow, equipo, temporada);
            if (jug != null) {
                jugadoresGuardar.add(jug);
                vistos.add(jug.getIdJugador());
            }
        }

        if (!jugadoresGuardar.isEmpty()) {
            jugadorRepository.saveAll(jugadoresGuardar);
        }
        return vistos;
    }

    private Jugador mapearJugador(JsonNode playerRow, Equipo equipo, Integer temporada) {
        JsonNode playerInfo = playerRow.get(PLAYER);

        if (playerInfo == null || !playerInfo.hasNonNull(FLD_ID)) return null;

        Integer idJug = playerInfo.get(FLD_ID).asInt();
        Jugador jugador = jugadorRepository.findById(idJug).orElse(new Jugador());

        jugador.setIdJugador(idJug);

        if (playerInfo.hasNonNull(FLD_NAME)) {
            jugador.setNombre(playerInfo.get(FLD_NAME).asText());
        } else if (jugador.getNombre() == null) {
            jugador.setNombre("Desconocido");
        }

        // "nombre" de la API a veces viene ya abreviado ("L. Messi") -- firstname/lastname
        // vienen sin abreviar por separado, cuando la API los trae.
        String nombrePila = playerInfo.path("firstname").asText(null);
        String apellidos = playerInfo.path("lastname").asText(null);
        String completo = (nombrePila != null ? nombrePila : "") + (nombrePila != null && apellidos != null ? " " : "") + (apellidos != null ? apellidos : "");
        if (!completo.isBlank()) {
            jugador.setNombreCompleto(completo.trim());
        }

        if (playerInfo.hasNonNull("height")) jugador.setAltura(playerInfo.get("height").asText());
        if (playerInfo.hasNonNull("weight")) jugador.setPeso(playerInfo.get("weight").asText());
        if (playerInfo.hasNonNull(FLD_NATIONALITY)) jugador.setNacionalidad(playerInfo.get(FLD_NATIONALITY).asText());

        jugador.setFotoUrl("https://media.api-sports.io/football/players/" + idJug + ".png");

        if (temporada.equals(temporadaActual)) {
            jugador.setEquipo(equipo);
            asignarEstadisticasJugador(jugador, playerRow.get(STATISTICS));
        }

        asignarFechaNacimiento(jugador, playerInfo.get(FLD_BIRTH));

        return jugador;
    }

    private void asignarFechaNacimiento(Jugador jugador, JsonNode birthNode) {
        if (birthNode != null && birthNode.hasNonNull("date")) {
            try {
                jugador.setFechaNacimiento(LocalDate.parse(birthNode.get("date").asText()));
            } catch (DateTimeParseException e) {
                log.trace("La fecha de nacimiento del jugador {} no tiene un formato parseable.", jugador.getIdJugador());
            }
        }
    }

    private void asignarEstadisticasJugador(Jugador jugador, JsonNode statsArray) {
        if (statsArray == null || !statsArray.isArray() || statsArray.isEmpty()) return;

        JsonNode stats = statsArray.get(0).get(GAMES);
        if (stats != null) {
            if (stats.hasNonNull(POSITION)) {
                jugador.setPosicion(stats.get(POSITION).asText());
            }
            if (stats.hasNonNull(NUMBER) && !stats.get(NUMBER).isNull()) {
                jugador.setDorsal(stats.get(NUMBER).asInt());
            }
        }
    }

    /**
     * Sin {@code @Transactional} a proposito: esto son hasta 2.000 llamadas a la API con sus
     * esperas, y meterlas todas en una transaccion tendria una conexion de Postgres ocupada
     * durante horas para nada. Cada pagina guarda lo suyo por su cuenta, que ademas es lo que
     * hace que el trabajo se pueda cortar sin perder lo ya hecho.
     */
    @Override
    public int ingestarNombresCompletos(int desdePagina, int maxPaginas) {
        int actualizados = 0;
        int pagina = Math.max(1, desdePagina);
        int ultima = pagina + Math.max(1, maxPaginas) - 1;

        log.info("Catalogo de jugadores: recorriendo desde la pagina {} (250 fichas por peticion). "
                + "Cada ficha rellena el perfil entero, no solo el nombre.", pagina);

        while (pagina <= ultima) {
            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Catalogo de jugadores: cuota agotada en la pagina {}. Retomar desde ahi.", pagina);
                return actualizados;
            }

            JsonNode respuesta;
            try {
                respuesta = apiClient.getPlayerProfilesPagina(pagina);
            } catch (Exception e) {
                // Una pagina que falla no corta la tanda: se salta y se sigue.
                log.error("Catalogo de jugadores: fallo en la pagina {}: {}", pagina, e.getMessage());
                pagina++;
                continue;
            }

            JsonNode fichas = respuesta == null ? null : respuesta.get(RESPONSE);
            if (fichas == null || !fichas.isArray() || fichas.isEmpty()) {
                log.info("Catalogo de jugadores: la pagina {} vino vacia, se termina aqui.", pagina);
                return actualizados;
            }

            actualizados += aplicarFichasDeLaPagina(fichas);

            int totalPaginas = respuesta.path("paging").path("total").asInt(0);
            if (totalPaginas > 0 && pagina >= totalPaginas) {
                log.info("Catalogo de jugadores: terminado en la pagina {} de {}. {} jugadores actualizados.",
                        pagina, totalPaginas, actualizados);
                return actualizados;
            }
            pagina++;
        }

        log.info("Catalogo de jugadores: tanda terminada. {} jugadores actualizados. Siguiente pagina: {}.",
                actualizados, pagina);
        return actualizados;
    }

    /**
     * Aplica la ficha entera de una pagina del catalogo a los jugadores que ya tenemos.
     *
     * <p>Solo se tocan los que estan en nuestra base: el catalogo de la API trae mas fichas de
     * las que nos interesan, y dar de alta jugadores sueltos aqui llenaria la tabla de gente
     * sin equipo ni partidos. Y solo se escribe si de verdad cambia algo, para no generar
     * un UPDATE por cada una de las 250 filas de cada pagina.</p>
     *
     * <p><b>Por que lee la ficha entera y no solo el nombre.</b> Esto antes se llamaba
     * "aplicarNombresDeLaPagina" y de las trece claves que trae cada ficha leia dos
     * ({@code firstname} y {@code lastname}), tirando las once restantes. Mientras tanto,
     * {@link #procesarPerfilIndividual(Jugador)} iba pidiendo esos mismos datos jugador a
     * jugador a <b>una peticion cada uno</b>. Medido sobre la base: 200.509 jugadores sin
     * verificar, o sea 200.509 peticiones, mas de un dia entero de cuota. El catalogo son
     * 2.721 paginas: <b>74 veces menos peticiones para exactamente los mismos campos</b>,
     * porque {@code /players/profiles} devuelve el mismo objeto con y sin el parametro
     * {@code player}.</p>
     *
     * <p>Por eso aqui se marca tambien {@code perfilVerificado}: la ficha del catalogo no es
     * una version pobre del perfil, es el perfil.</p>
     */
    private int aplicarFichasDeLaPagina(JsonNode fichas) {
        Map<Integer, JsonNode> fichasPorId = new HashMap<>();
        for (JsonNode ficha : fichas) {
            JsonNode info = ficha.path(PLAYER);
            int id = info.path(FLD_ID).asInt(0);
            if (id != 0) {
                fichasPorId.put(id, info);
            }
        }
        if (fichasPorId.isEmpty()) {
            return 0;
        }

        List<Jugador> nuestros = jugadorRepository.findAllById(fichasPorId.keySet());
        List<Jugador> cambiados = new ArrayList<>();
        for (Jugador jugador : nuestros) {
            if (aplicarFicha(jugador, fichasPorId.get(jugador.getIdJugador()))) {
                cambiados.add(jugador);
            }
        }
        if (!cambiados.isEmpty()) {
            jugadorRepository.saveAll(cambiados);
        }
        return cambiados.size();
    }

    /**
     * Vuelca una ficha del catalogo sobre un jugador. Devuelve si ha cambiado algo.
     *
     * <p>Salvo el nombre completo, los campos solo se rellenan <b>si estan vacios</b>. No es
     * timidez: {@code dorsal} y {@code posicion} tambien los escribe
     * {@link #asignarEstadisticasJugador}, que los saca de las estadisticas del equipo en la
     * temporada en curso y por tanto sabe que el 9 juega de delantero <i>este anho</i>. La
     * ficha del catalogo es generica y mas vieja; dejarla pisar eso seria cambiar un dato
     * bueno por uno peor.</p>
     */
    private boolean aplicarFicha(Jugador jugador, JsonNode info) {
        if (info == null) {
            return false;
        }
        boolean cambiado = false;

        // El nombre completo si se pisa: el catalogo es su fuente canonica, y es el unico
        // sitio donde la API no lo manda abreviado ("R. Burki" -> "Roman Burki").
        String completo = componerNombreCompleto(info);
        if (completo != null && !completo.equals(jugador.getNombreCompleto())) {
            jugador.setNombreCompleto(completo);
            cambiado = true;
        }

        // La altura y el peso llegan del catalogo como numero pelado ("187"), mientras que
        // por el endpoint de un jugador suelto vienen con unidad ("187 cm"). Los 324.852
        // jugadores ya verificados estan guardados con unidad, asi que sin esto la tabla
        // acabaria con las dos formas mezcladas y el front pintando "187" al lado de
        // "180 cm".
        cambiado |= rellenarSiVacio(jugador::getAltura, jugador::setAltura,
                conUnidad(texto(info, "height"), "cm"));
        cambiado |= rellenarSiVacio(jugador::getPeso, jugador::setPeso,
                conUnidad(texto(info, "weight"), "kg"));
        cambiado |= rellenarSiVacio(jugador::getNacionalidad, jugador::setNacionalidad,
                texto(info, FLD_NATIONALITY));
        cambiado |= rellenarSiVacio(jugador::getPosicion, jugador::setPosicion,
                texto(info, POSITION));
        cambiado |= rellenarSiVacio(jugador::getFotoUrl, jugador::setFotoUrl,
                texto(info, "photo"));

        JsonNode birth = info.get(FLD_BIRTH);
        if (birth != null) {
            cambiado |= rellenarSiVacio(jugador::getLugarNacimiento, jugador::setLugarNacimiento,
                    texto(birth, FLD_PLACE));
            if (jugador.getFechaNacimiento() == null && birth.hasNonNull("date")) {
                try {
                    jugador.setFechaNacimiento(LocalDate.parse(birth.get("date").asText()));
                    cambiado = true;
                } catch (DateTimeParseException e) {
                    log.trace("Fecha de nacimiento no parseable en el catalogo para el jugador {}.",
                            jugador.getIdJugador());
                }
            }
        }

        if (jugador.getDorsal() == null && info.hasNonNull(NUMBER)) {
            jugador.setDorsal(info.get(NUMBER).asInt());
            cambiado = true;
        }

        if (!Boolean.TRUE.equals(jugador.getPerfilVerificado())) {
            jugador.setPerfilVerificado(true);
            cambiado = true;
        }
        return cambiado;
    }

    /** Escribe solo si el campo estaba vacio y el valor nuevo aporta algo. */
    private boolean rellenarSiVacio(Supplier<String> lector, Consumer<String> escritor, String valor) {
        String actual = lector.get();
        if (valor == null || (actual != null && !actual.isBlank())) {
            return false;
        }
        escritor.accept(valor);
        return true;
    }

    /** Texto limpio de un campo, o null si viene vacio, "null" o en blanco. */
    private String texto(JsonNode nodo, String campo) {
        if (nodo == null || !nodo.hasNonNull(campo)) {
            return null;
        }
        String valor = nodo.get(campo).asText().trim();
        return (valor.isEmpty() || "null".equalsIgnoreCase(valor)) ? null : valor;
    }

    /** "187" -> "187 cm". Si ya trae unidad se deja como esta. */
    private String conUnidad(String valor, String unidad) {
        if (valor == null) {
            return null;
        }
        return valor.matches("\\d+") ? valor + " " + unidad : valor;
    }

    /** {@code firstname} + {@code lastname}, que es lo que la API no abrevia. */
    private String componerNombreCompleto(JsonNode info) {
        String nombrePila = info.path("firstname").asText(null);
        String apellidos = info.path("lastname").asText(null);
        String completo = ((nombrePila != null ? nombrePila : "") + " "
                + (apellidos != null ? apellidos : "")).trim();
        return completo.isBlank() ? null : completo;
    }

    // FIX SonarQube [java:S3776]: Reducción de la Complejidad Cognitiva extrayendo el procesamiento a un método privado
    @Transactional
    @Override
    public void ingestarPerfilesPendientes() {
        log.info("Iniciando escaneo de perfiles incompletos de jugadores...");
        Page<Jugador> jugadoresPendientes;

        do {
            // Siempre pedimos la página 0: findJugadoresPendientesDePerfil filtra por
            // perfilVerificado, campo que este mismo bucle va marcando a true. Si en
            // vez de eso avanzáramos el número de página, el OFFSET se aplicaría sobre
            // un conjunto que se encoge en cada vuelta y saltaríamos un lote entero de
            // jugadores sin procesar por cada página consultada.
            jugadoresPendientes = jugadorRepository.findJugadoresPendientesDePerfil(PageRequest.of(0, 50));

            for (Jugador jugador : jugadoresPendientes) {
                if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                    log.warn("Cuota agotada al actualizar perfiles de jugadores.");
                    return;
                }
                procesarPerfilIndividual(jugador);
            }

            if (!jugadoresPendientes.isEmpty()) {
                jugadorRepository.saveAll(jugadoresPendientes.getContent());
                log.info("Guardado lote de perfiles.");
            }

        } while (jugadoresPendientes.hasNext());

        log.info("Sincronización de perfiles de jugadores completada.");
    }

    private void procesarPerfilIndividual(Jugador jugador) {
        try {
            JsonNode response = apiClient.getPlayerProfile(jugador.getIdJugador());
            if (response != null && response.has(RESPONSE) && !response.get(RESPONSE).isEmpty()) {
                JsonNode playerData = response.get(RESPONSE).get(0).get(PLAYER);
                JsonNode birthNode = playerData.get(FLD_BIRTH);

                if (birthNode != null && birthNode.hasNonNull(FLD_PLACE)) {
                    jugador.setLugarNacimiento(birthNode.get(FLD_PLACE).asText());
                }
            }
            jugador.setPerfilVerificado(true);
        } catch (Exception e) {
            log.error("Fallo al pedir el profile de jugador {}: {}", jugador.getIdJugador(), e.getMessage());
        }
    }
}