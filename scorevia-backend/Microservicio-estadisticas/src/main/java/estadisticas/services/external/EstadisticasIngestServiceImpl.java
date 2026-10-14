package estadisticas.services.external;

import estadisticas.dto.external.ApiFootballFixturesResponseDTO;
import estadisticas.dto.external.ApiPlayerStatsResponseDTO;
import estadisticas.dto.external.FixtureItemDTO;
import estadisticas.dto.external.GoalsInfoDTO;
import estadisticas.dto.external.LineupDTO;
import estadisticas.dto.external.ScoreDTO;
import estadisticas.dto.external.ApiTeamStatsResponseDTO;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;
import estadisticas.entities.partidos.Partido;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class EstadisticasIngestServiceImpl implements EstadisticasIngestService {

    private static final int MAX_INTENTOS_INGESTA = 3;

    /**
     * Tandas seguidas sin una sola respuesta antes de dar la pasada por perdida.
     *
     * <p>Tres y no una: una tanda puede fallar por si misma (un id raro, un corte de red de un
     * segundo) y parar ahi seria dejar trabajo bueno sin hacer. Tres seguidas ya no es mala
     * suerte, es que no hay nadie al otro lado.</p>
     */
    private static final int TANDAS_FALLIDAS_PARA_PARAR = 3;

    private final ApiFootballClient apiClient;
    private final PartidoRepository partidoRepository;
    private final EstadisticaEquipoPartidoRepository estEquipoRepository;
    private final JugadorRepository jugadorRepository;
    private final EstadisticaJugadorPartidoRepository estJugadorRepository;
    private final EventoPartidoIngestService eventoPartidoIngestService;

    /**
     * Días que tiene que tener un partido para que se le monte el once desde la alineación
     * cuando no hay estadísticas. Ver mereceGuardarAlineacionSinEstadisticas.
     */
    @org.springframework.beans.factory.annotation.Value("${scorevia.ingesta.alineacion-sin-stats.dias-minimos:7}")
    private int diasMinimosAlineacionSinStats = 7;

    @Override
    public int ingestarEstadisticasPendientes() {
        int procesadosTotales = 0;
        int tamanoLote = 500;
        int pagina = 0;

        log.info("Iniciando motor de ingesta masiva de estadísticas...");

        boolean hayPendientes = true;
        Set<Integer> partidosIgnoradosEnEstaRonda = new HashSet<>();

        while (hayPendientes && apiClient.hayCuotaDisponibleParaBackfill()) {

            // ZoneId.of("UTC") explícito, no systemDefault(): p.fecha se guarda en UTC
            // (ver PartidoIngestServiceImpl.parsearFecha), así que comparar contra la hora
            // local del host desplazaría este período de gracia según su zona horaria.
            LocalDateTime limiteGracia = LocalDateTime.now(ZoneId.of("UTC")).minusHours(3);
            Pageable limit = PageRequest.of(pagina, tamanoLote, Sort.by("fecha").ascending());
            List<Partido> lote = partidoRepository.findPartidosPendientesDeStats(limiteGracia, limit);

            List<Partido> pendientes = lote.stream()
                    .filter(p -> !partidosIgnoradosEnEstaRonda.contains(p.getIdPartido()))
                    .toList();

            if (pendientes.isEmpty()) {
                if (lote.size() == tamanoLote) {
                    // La página vino llena pero todos sus partidos ya se intentaron en esta
                    // ronda (fallaron y quedaron ignorados). Puede haber más partidos
                    // pendientes en páginas posteriores que todavía no se han consultado
                    // (la paginación fija en 0 los dejaría varados), así que avanzamos de
                    // página en vez de dar la ronda por finalizada.
                    pagina++;
                } else {
                    log.info("¡Todo al día (o bloqueados temporalmente)! No hay más partidos pendientes de ingesta.");
                    hayPendientes = false;
                }
            } else {
                procesadosTotales += procesarLote(pendientes, partidosIgnoradosEnEstaRonda);
            }
        }

        log.info("Ronda de ingesta completada. Partidos descargados con éxito hoy: {}", procesadosTotales);
        return procesadosTotales;
    }

    @Override
    public ResultadoIngestaPartido ingestarEstadisticasDePartido(Integer idPartido) {
        return ingestarLoteDePartidos(List.of(idPartido))
                .getOrDefault(idPartido, ResultadoIngestaPartido.ERROR);
    }

    @Override
    public Map<Integer, ResultadoIngestaPartido> ingestarLoteDePartidos(List<Integer> idsPartidos) {
        // anotarFallo = false: por aquí entran los reintentos de la media hora siguiente al
        // pitido final. Si contaran para el contador de fallos, un partido cuyas estadísticas
        // la API publica con retraso quedaría marcado como "no las tiene" y ni la recogida
        // diaria ni ningún backfill volverían a mirarlo.
        return ingestarLoteDePartidos(idsPartidos, false);
    }

    /**
     * El corazón de la ingesta: hasta 20 partidos con <b>una sola petición</b>.
     *
     * <p>Pedir fixtures por id devuelve, de cada partido, sus eventos, sus alineaciones y sus
     * estadísticas de equipo y de jugador. Antes esto eran tres peticiones por partido; ahora
     * es una por cada veinte. Sobre los 42.000 partidos que hoy siguen sin estadísticas, la
     * diferencia es 85.000 peticiones frente a 2.100.</p>
     *
     * @param anotarFallo si una respuesta sin datos debe contar para el contador que acaba
     *                    marcando el partido como {@code statsNotFound}.
     */
    private Map<Integer, ResultadoIngestaPartido> ingestarLoteDePartidos(List<Integer> idsPartidos, boolean anotarFallo) {
        Map<Integer, ResultadoIngestaPartido> resultados = new LinkedHashMap<>();
        if (idsPartidos == null || idsPartidos.isEmpty()) {
            return resultados;
        }

        // findByIdsConLiga y no findAllById: hay que leer la cobertura de la competición y
        // Partido.liga es LAZY. Esto corre en el hilo del scheduler, sin sesión abierta, así
        // que un proxy sin inicializar reventaría al tocarlo.
        Map<Integer, Partido> porId = partidoRepository.findByIdsConLiga(idsPartidos).stream()
                .collect(Collectors.toMap(Partido::getIdPartido, p -> p, (a, b) -> a));

        List<Integer> aPedir = new ArrayList<>();
        for (Integer id : idsPartidos) {
            Partido partido = porId.get(id);
            if (partido == null) {
                log.warn("Se pidió ingerir el partido {}, que no está en la base.", id);
                resultados.put(id, ResultadoIngestaPartido.NO_ENCONTRADO);
            } else if (!hayCoberturaDeStats(partido)) {
                log.debug("Partido {} saltado: su competición no publica estadísticas.", id);
                resultados.put(id, ResultadoIngestaPartido.SIN_COBERTURA);
            } else {
                aPedir.add(id);
            }
        }

        for (List<Integer> tanda : particionar(aPedir, ApiFootballClient.MAX_FIXTURES_POR_PETICION)) {
            resultados.putAll(pedirYGuardarTanda(tanda, porId, anotarFallo));
        }
        return resultados;
    }

    private Map<Integer, ResultadoIngestaPartido> pedirYGuardarTanda(List<Integer> tanda,
                                                                     Map<Integer, Partido> porId,
                                                                     boolean anotarFallo) {
        Map<Integer, ResultadoIngestaPartido> resultados = new LinkedHashMap<>();
        Map<Integer, FixtureItemDTO> items;
        try {
            items = indexarPorId(apiClient.getFixturesByIds(tanda));
        } catch (Exception e) {
            // La petición es de toda la tanda: si falla, fallan los veinte. No se anota como
            // intento fallido de cada partido, porque el problema es la llamada, no los datos.
            log.warn("Falló la petición de la tanda {}: {}", tanda, e.getMessage());
            tanda.forEach(id -> resultados.put(id, ResultadoIngestaPartido.ERROR));
            return resultados;
        }

        for (Integer id : tanda) {
            Partido partido = porId.get(id);
            try {
                resultados.put(id, guardarDatosDePartido(partido, items.get(id), anotarFallo));
            } catch (Exception e) {
                // Que un partido reviente al guardarse no puede tirar los otros diecinueve,
                // por los que ya se ha pagado la petición.
                log.warn("Partido {} falló al guardar sus estadísticas: {}", id, e.getMessage());
                if (anotarFallo) {
                    registrarIntentoFallido(partido);
                }
                resultados.put(id, ResultadoIngestaPartido.ERROR);
            }
        }
        return resultados;
    }

    /**
     * Guarda lo que trae un fixture ya descargado: estadísticas de equipo, de jugador y
     * timeline.
     *
     * <p>El partido se escribe <b>una sola vez</b>, al final. No es una optimización: como
     * {@code Partido} lleva {@code @Version} y aquí llega desasociado de la sesión, dos
     * guardados seguidos del mismo objeto hacían que el segundo fuera con la versión vieja y
     * saltara {@code OptimisticLockingFailureException} — dando la ingesta por fallida justo
     * después de haber guardado bien los datos.</p>
     */
    private ResultadoIngestaPartido guardarDatosDePartido(Partido partido, FixtureItemDTO item, boolean anotarFallo) {
        boolean hayEquipo = item != null && item.getStatistics() != null && !item.getStatistics().isEmpty();
        boolean hayJugadores = item != null && item.getPlayers() != null && !item.getPlayers().isEmpty();

        if (!hayEquipo || !hayJugadores) {
            // La respuesta ya está pagada, así que antes de darla por perdida hay que quedarse
            // con lo que sí trae. En los partidos antiguos API-Football no publica
            // estadísticas pero SÍ el once: comprobado con partidos de 2015, 2018 y 2021, los
            // tres devolvían sus dos alineaciones (11 titulares y el banquillo) con
            // statistics y players vacíos. Antes se tiraban a la basura junto con el timeline.
            boolean algoSalvado = guardarLoQueSePueda(partido, item);

            log.warn("El partido {} no tiene estadísticas en la API{}.", partido.getIdPartido(),
                    algoSalvado ? ", pero se ha guardado su alineación y/o su timeline" : "");

            if (anotarFallo) {
                registrarIntentoFallido(partido);
            } else if (algoSalvado) {
                // registrarIntentoFallido ya guarda el partido; si no se llama, hay que
                // guardarlo igual para que las banderas de eventos que se acaban de tocar
                // no se queden solo en memoria.
                partidoRepository.save(partido);
            }
            return ResultadoIngestaPartido.SIN_DATOS;
        }

        procesarEstadisticasEquipo(partido, item.getStatistics());
        procesarEstadisticasJugadores(partido, item.getPlayers());
        // El timeline viene en la misma respuesta: guardarlo no cuesta otra petición.
        eventoPartidoIngestService.guardarEventosSinPersistirPartido(partido, item.getEvents());
        // Y las alineaciones oficiales y el marcador desglosado, por el mismo precio.
        procesarAlineaciones(partido, item.getLineups());
        aplicarMarcadorDesglosado(partido, item.getScore());

        partido.setIntentosIngesta(0);
        partido.setStatsNotFound(false);
        partidoRepository.save(partido);
        return ResultadoIngestaPartido.OK;
    }

    /**
     * De un partido sin estadísticas, guarda lo que la respuesta sí traiga.
     *
     * @return true si se ha guardado algo.
     */
    private boolean guardarLoQueSePueda(Partido partido, FixtureItemDTO item) {
        if (item == null) {
            return false;
        }

        // El timeline siempre: no depende de las estadísticas y no crea filas que puedan
        // hacer parecer completo a un partido que no lo está.
        boolean hayEventos = eventoPartidoIngestService.guardarEventosSinPersistirPartido(partido, item.getEvents());

        boolean hayAlineacion = false;
        if (mereceGuardarAlineacionSinEstadisticas(partido)) {
            hayAlineacion = guardarAlineacionSinEstadisticas(partido, item.getLineups());
        }

        return hayEventos || hayAlineacion;
    }

    /**
     * ¿Se puede montar el once de este partido a partir de la alineación, sin estadísticas?
     *
     * <p>Solo en partidos con unos días de antigüedad. El motivo es que las filas del once se
     * guardan en las mismas tablas que las estadísticas, y la consulta que busca partidos
     * pendientes pregunta justo por la existencia de esas filas: crearlas en un partido de
     * hace dos horas —al que la API todavía no le ha publicado las estadísticas, pero se las
     * publicará— lo dejaría marcado como completo y sus estadísticas de verdad no se pedirían
     * nunca. Pasada esa ventana, si no están es que no van a estar (la documentación habla de
     * hasta 48h de retraso en algunas competiciones).</p>
     */
    private boolean mereceGuardarAlineacionSinEstadisticas(Partido partido) {
        if (partido.getFecha() == null) {
            return false;
        }
        return partido.getFecha()
                .isBefore(LocalDateTime.now(ZoneId.of("UTC")).minusDays(diasMinimosAlineacionSinStats));
    }

    /**
     * Monta el once y el banquillo a partir del bloque {@code lineups}, sin estadísticas.
     *
     * <p>Lo que se puede guardar depende de lo viejo que sea el partido, y la diferencia es
     * grande: en 2015 la API da los 11 titulares y los suplentes con su dorsal, pero sin
     * formación, sin entrenador, sin posición y sin sitio en el campo; a partir de 2021 vienen
     * las cuatro cosas. Por eso aquí se guarda campo a campo lo que haya y se deja nulo lo que
     * no: la ficha del partido ya sabe caer a una lista cuando no hay líneas que pintar.</p>
     *
     * @return true si se ha guardado al menos un jugador.
     */
    private boolean guardarAlineacionSinEstadisticas(Partido partido, List<LineupDTO> alineaciones) {
        if (alineaciones == null || alineaciones.isEmpty()) {
            return false;
        }

        // Las filas que ya hubiera de este partido, para no duplicar ni pisar lo que exista.
        Map<Integer, EstadisticaJugadorPartido> existentes = new HashMap<>();
        estJugadorRepository.findByPartidoIdPartido(partido.getIdPartido())
                .forEach(est -> existentes.put(est.getJugador().getIdJugador(), est));

        // Todos los jugadores de las dos alineaciones en UNA consulta.
        //
        // Antes esto era un findById por jugador dentro del bucle: entre titulares y
        // banquillos son más de treinta consultas por partido, y con eso la ingesta se quedó
        // en 244 partidos por minuto gastando solo 6 peticiones a la API. O sea que no la
        // frenaba la API, la frenaba esto. El camino de las estadísticas ya lo hacía así.
        Map<Integer, Jugador> jugadoresExistentes = new HashMap<>();
        List<Integer> idsJugadores = alineaciones.stream()
                .flatMap(a -> Stream.concat(
                        (a.getStartXI() == null ? List.<LineupDTO.JugadorAlineadoDTO>of() : a.getStartXI()).stream(),
                        (a.getSubstitutes() == null ? List.<LineupDTO.JugadorAlineadoDTO>of() : a.getSubstitutes()).stream()))
                .map(LineupDTO.JugadorAlineadoDTO::getPlayer)
                .filter(Objects::nonNull)
                .map(LineupDTO.JugadorEnCampoDTO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!idsJugadores.isEmpty()) {
            jugadorRepository.findAllById(idsJugadores)
                    .forEach(j -> jugadoresExistentes.put(j.getIdJugador(), j));
        }

        List<Jugador> jugadoresParaGuardar = new ArrayList<>();
        List<EstadisticaJugadorPartido> filasParaGuardar = new ArrayList<>();

        for (LineupDTO alineacion : alineaciones) {
            if (alineacion.getTeam() == null || alineacion.getTeam().getId() == null) {
                continue;
            }
            Equipo equipo = obtenerEquipoDelPartido(partido, alineacion.getTeam().getId());
            if (equipo == null) {
                continue;
            }

            anotarJugadoresDeLaAlineacion(partido, equipo, alineacion.getStartXI(), true,
                    jugadoresExistentes, existentes, jugadoresParaGuardar, filasParaGuardar);
            anotarJugadoresDeLaAlineacion(partido, equipo, alineacion.getSubstitutes(), false,
                    jugadoresExistentes, existentes, jugadoresParaGuardar, filasParaGuardar);

            guardarFormacionYEntrenador(partido, equipo, alineacion);
        }

        if (filasParaGuardar.isEmpty()) {
            return false;
        }

        // Los jugadores primero: estadisticas_jugador_partido tiene FK a jugadores, y en un
        // partido viejo es normal que alguno no esté todavía en la base.
        if (!jugadoresParaGuardar.isEmpty()) {
            jugadorRepository.saveAll(jugadoresParaGuardar);
        }
        estJugadorRepository.saveAll(filasParaGuardar);
        return true;
    }

    private void anotarJugadoresDeLaAlineacion(Partido partido, Equipo equipo,
                                               List<LineupDTO.JugadorAlineadoDTO> alineados, boolean titular,
                                               Map<Integer, Jugador> jugadoresExistentes,
                                               Map<Integer, EstadisticaJugadorPartido> existentes,
                                               List<Jugador> jugadoresParaGuardar,
                                               List<EstadisticaJugadorPartido> filasParaGuardar) {
        if (alineados == null) {
            return;
        }

        for (LineupDTO.JugadorAlineadoDTO alineado : alineados) {
            LineupDTO.JugadorEnCampoDTO info = alineado.getPlayer();
            if (info == null || info.getId() == null || info.getName() == null) {
                continue;
            }

            Jugador jugador = jugadoresExistentes.get(info.getId());
            boolean esNuevo = jugador == null;
            if (esNuevo) {
                jugador = new Jugador();
                jugador.setIdJugador(info.getId());
                jugador.setNombre(info.getName());
                if (info.getNumber() != null) {
                    // El dorsal solo del jugador nuevo: el de su ficha es el actual, y el de
                    // un partido de 2015 no tiene por qué serlo.
                    jugador.setDorsal(info.getNumber());
                }
                jugadoresExistentes.put(info.getId(), jugador);
            }

            // Se guarda el jugador SOLO si de verdad cambia algo. En un partido antiguo casi
            // todos están ya en la base y con su club puesto: mandarlos igual al saveAll era
            // un merge (SELECT + UPDATE) por cada uno para no cambiar ni un campo.
            boolean cambiado = esNuevo;
            if (jugador.getNombre() == null) {
                jugador.setNombre(info.getName());
                cambiado = true;
            }
            // Mismo gate por fecha que la ingesta con estadísticas: un partido antiguo no
            // puede pisar el club actual del jugador.
            if (jugador.getFechaEquipoActualizado() == null) {
                jugador.setEquipo(equipo);
                jugador.setFechaEquipoActualizado(partido.getFecha());
                cambiado = true;
            }
            if (cambiado) {
                jugadoresParaGuardar.add(jugador);
            }

            EstadisticaJugadorPartido fila = existentes.get(info.getId());
            if (fila == null) {
                fila = new EstadisticaJugadorPartido();
                fila.setPartido(partido);
                fila.setJugador(jugador);
                fila.setEquipo(equipo);
                existentes.put(info.getId(), fila);
                filasParaGuardar.add(fila);
            }

            // Solo los campos de la alineación: minutos, goles y notas se quedan nulos porque
            // la API no los da para estos partidos, y machacarlos borraría datos buenos si la
            // fila ya venía de una ingesta con estadísticas.
            fila.setTitular(titular);
            if (info.getPos() != null) {
                fila.setPosicionCampo(info.getPos());
            }
            if (info.getGrid() != null) {
                fila.setPosicionGrid(info.getGrid());
            }
        }
    }

    /**
     * Guarda dibujo y entrenador cuando los hay, creando la fila de equipo si hace falta.
     *
     * <p>Si no viene ninguno de los dos —lo normal antes de 2020— no se crea nada: una fila de
     * estadísticas de equipo entera a nulos no aporta y encima haría parecer completo al
     * partido en la consulta de pendientes.</p>
     */
    private void guardarFormacionYEntrenador(Partido partido, Equipo equipo, LineupDTO alineacion) {
        Integer idEntrenador = alineacion.getCoach() != null ? alineacion.getCoach().getId() : null;
        String formacion = alineacion.getFormation();

        if ((formacion == null || formacion.isBlank()) && idEntrenador == null) {
            return;
        }

        EstadisticaEquipoPartido fila = estEquipoRepository
                .findByPartidoAndEquipo(partido, equipo)
                .orElseGet(EstadisticaEquipoPartido::new);

        if (fila.getId() == null) {
            fila.setPartido(partido);
            fila.setEquipo(equipo);
        }
        if (formacion != null && !formacion.isBlank()) {
            fila.setSistemaTactico(formacion);
        }
        if (idEntrenador != null) {
            fila.setIdEntrenador(idEntrenador);
        }
        estEquipoRepository.save(fila);
    }

    /**
     * Guarda la alineación oficial: dibujo, entrenador del partido y sitio de cada titular.
     *
     * <p>Es mejor dato que el que teníamos y no cuesta nada: viene dentro del mismo fixture.
     * El dibujo se deducía contando titulares por línea, y el entrenador salía del club (no
     * del partido) porque {@code historial_entrenadores} está vacía.</p>
     *
     * <p>Se escribe sobre las filas que acaban de guardar los dos métodos de arriba, así que
     * hay que llamarlo <b>después</b> de ellos.</p>
     */
    private void procesarAlineaciones(Partido partido, List<LineupDTO> alineaciones) {
        if (alineaciones == null || alineaciones.isEmpty()) {
            return;
        }

        for (LineupDTO alineacion : alineaciones) {
            if (alineacion.getTeam() == null || alineacion.getTeam().getId() == null) {
                continue;
            }
            Equipo equipo = obtenerEquipoDelPartido(partido, alineacion.getTeam().getId());
            if (equipo == null) {
                continue;
            }

            estEquipoRepository.findByPartidoAndEquipo(partido, equipo).ifPresent(fila -> {
                fila.setSistemaTactico(alineacion.getFormation());
                if (alineacion.getCoach() != null) {
                    fila.setIdEntrenador(alineacion.getCoach().getId());
                }
                estEquipoRepository.save(fila);
            });

            guardarPosicionesEnElCampo(partido, alineacion.getStartXI());
        }
    }

    /** El "fila:columna" de cada titular, sobre las filas de estadísticas ya guardadas. */
    private void guardarPosicionesEnElCampo(Partido partido, List<LineupDTO.JugadorAlineadoDTO> once) {
        if (once == null || once.isEmpty()) {
            return;
        }

        Map<Integer, String> gridPorJugador = new HashMap<>();
        for (LineupDTO.JugadorAlineadoDTO alineado : once) {
            LineupDTO.JugadorEnCampoDTO jugador = alineado.getPlayer();
            if (jugador != null && jugador.getId() != null && jugador.getGrid() != null) {
                gridPorJugador.put(jugador.getId(), jugador.getGrid());
            }
        }
        if (gridPorJugador.isEmpty()) {
            return;
        }

        List<EstadisticaJugadorPartido> filas = estJugadorRepository.findByPartidoIdPartido(partido.getIdPartido()).stream()
                .filter(fila -> fila.getJugador() != null && gridPorJugador.containsKey(fila.getJugador().getIdJugador()))
                .toList();

        filas.forEach(fila -> fila.setPosicionGrid(gridPorJugador.get(fila.getJugador().getIdJugador())));
        estJugadorRepository.saveAll(filas);
    }

    /**
     * Marcador al descanso y tanda de penaltis.
     *
     * <p>Solo se escribe lo que venga con valor: la API manda {@code null} en las fases que no
     * se jugaron, y machacar con null borraría un dato bueno de una ingesta anterior.</p>
     */
    private void aplicarMarcadorDesglosado(Partido partido, ScoreDTO score) {
        if (score == null) {
            return;
        }
        if (score.getHalftime() != null) {
            if (score.getHalftime().getHome() != null) {
                partido.setResultadoLocalDescanso(score.getHalftime().getHome());
            }
            if (score.getHalftime().getAway() != null) {
                partido.setResultadoVisitanteDescanso(score.getHalftime().getAway());
            }
        }
        aplicarTandaDePenaltis(partido, score.getPenalty());
    }

    /**
     * La tanda de penaltis, solo si de verdad la hubo.
     *
     * <p>API-Football manda {@code penalty: {home: 0, away: 0}} en partidos que se estan
     * jugando y que nunca van a llegar a una tanda. Guardarlo tal cual hacia que el front
     * pintara "(0 - 0 en penaltis)" debajo del marcador, porque para el la tanda existe en
     * cuanto los dos numeros no son null. Visto en produccion: un Eredivisie <b>3-3 en la
     * segunda parte</b> con "0-0 en penaltis" al lado.</p>
     *
     * <p>El filtro es el propio resultado: <b>una tanda no puede acabar en empate</b>, porque
     * se tira hasta que alguien gana. Si los dos numeros son iguales -- y el 0-0 es el caso
     * habitual -- no es una tanda, es la API rellenando huecos.</p>
     */
    private void aplicarTandaDePenaltis(Partido partido, GoalsInfoDTO penaltis) {
        if (penaltis == null || penaltis.getHome() == null || penaltis.getAway() == null) {
            return;
        }
        if (penaltis.getHome().equals(penaltis.getAway())) {
            return;
        }
        partido.setPenaltisLocal(penaltis.getHome());
        partido.setPenaltisVisitante(penaltis.getAway());
    }

    private Map<Integer, FixtureItemDTO> indexarPorId(ApiFootballFixturesResponseDTO respuesta) {
        if (respuesta == null || respuesta.getResponse() == null) {
            return Map.of();
        }
        return respuesta.getResponse().stream()
                .filter(i -> i.getFixture() != null && i.getFixture().getId() != null)
                .collect(Collectors.toMap(i -> i.getFixture().getId(), i -> i, (a, b) -> a));
    }

    private static <T> List<List<T>> particionar(List<T> lista, int tamano) {
        List<List<T>> tandas = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += tamano) {
            tandas.add(lista.subList(i, Math.min(lista.size(), i + tamano)));
        }
        return tandas;
    }

    @Override
    public int ingestarEstadisticasRecientes(int horas, int maxPartidos) {
        // UTC explícito: p.fecha se guarda en UTC (ver PartidoIngestServiceImpl.parsearFecha),
        // así que usar la hora local del host desplazaría la ventana según su zona horaria.
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("UTC"));
        // El mismo período de gracia que usa la ingesta por lotes: API-Football tarda un rato
        // en publicar las estadísticas definitivas después del pitido final, y preguntar antes
        // solo sirve para gastar el intento.
        LocalDateTime hasta = ahora.minusHours(3);
        LocalDateTime desde = ahora.minusHours(horas);

        Page<Partido> pendientes = partidoRepository.findPartidosRecientesPendientesDeStats(
                desde, hasta, PageRequest.of(0, maxPartidos, Sort.by("fecha").ascending()));

        if (pendientes.isEmpty()) {
            log.info("Recogida del día: no hay partidos recientes pendientes de estadísticas.");
            return 0;
        }

        log.info("Recogida del día: {} partidos acabados en las últimas {}h siguen sin estadísticas.",
                pendientes.getNumberOfElements(), horas);

        int procesados = procesarPorTandas(pendientes.getContent(), "Recogida del día");
        log.info("Recogida del día terminada: {} partidos completados.", procesados);
        return procesados;
    }

    /**
     * Procesa una lista de partidos en tandas de 20, parando si se acaba la cuota.
     *
     * <p>Aquí sí se anotan los intentos fallidos: estas pasadas corren horas después del
     * partido, cuando la API ya no tiene excusa para no haber publicado sus estadísticas, así
     * que un vacío repetido es motivo suficiente para dejar de preguntar por él.</p>
     */
    private int procesarPorTandas(List<Partido> partidos, String contexto) {
        List<Integer> ids = partidos.stream().map(Partido::getIdPartido).toList();
        int completados = 0;
        int erroresSeguidos = 0;

        for (List<Integer> tanda : particionar(ids, ApiFootballClient.MAX_FIXTURES_POR_PETICION)) {
            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("{} detenida por cuota: quedan partidos sin procesar.", contexto);
                break;
            }
            Map<Integer, ResultadoIngestaPartido> resultados = ingestarLoteDePartidos(tanda, true);
            long ok = resultados.values().stream().filter(r -> r == ResultadoIngestaPartido.OK).count();
            completados += (int) ok;

            // Con el circuito abierto, las tandas que quedan fallan al instante y sin llegar a
            // preguntar: recorrerlas solo sirve para que el log diga que fallaron 1.700
            // partidos cuando lo que pasa es que ya no se esta preguntando por ninguno.
            if (ok == 0 && resultados.values().stream().allMatch(r -> r == ResultadoIngestaPartido.ERROR)) {
                erroresSeguidos++;
                if (erroresSeguidos >= TANDAS_FALLIDAS_PARA_PARAR) {
                    log.warn("{} detenida tras {} tandas seguidas sin respuesta. Relanzar cuando la API "
                            + "vuelva: los partidos que quedan siguen marcados como pendientes.",
                            contexto, erroresSeguidos);
                    break;
                }
            } else {
                erroresSeguidos = 0;
            }
        }
        return completados;
    }

    /**
     * ¿Publica estadísticas la competición de este partido?
     *
     * <p>{@code null} significa "no se sabe" y se intenta igual: es lo que pasa con una liga que
     * todavía no ha pasado por una sincronización de {@code /leagues}.</p>
     */
    private boolean hayCoberturaDeStats(Partido partido) {
        return partido.getLiga() == null
                || !Boolean.FALSE.equals(partido.getLiga().getCoberturaStatsPartido());
    }

    private int procesarLote(List<Partido> pendientes, Set<Integer> ignorados) {
        List<Integer> ids = pendientes.stream().map(Partido::getIdPartido).toList();
        int procesadosEnLote = 0;

        for (List<Integer> tanda : particionar(ids, ApiFootballClient.MAX_FIXTURES_POR_PETICION)) {
            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Cuota agotada en medio de un lote. Parando la ingesta de forma segura.");
                break;
            }

            Map<Integer, ResultadoIngestaPartido> resultados = ingestarLoteDePartidos(tanda, true);
            for (Map.Entry<Integer, ResultadoIngestaPartido> entrada : resultados.entrySet()) {
                if (entrada.getValue() == ResultadoIngestaPartido.OK) {
                    procesadosEnLote++;
                } else {
                    // Se aparta para que la siguiente vuelta del bucle no lo reintente en la
                    // misma ronda: la consulta de pendientes lo seguiría devolviendo.
                    ignorados.add(entrada.getKey());
                }
            }
        }
        return procesadosEnLote;
    }

    private void registrarIntentoFallido(Partido partido) {
        int intentos = Optional.ofNullable(partido.getIntentosIngesta()).orElse(0) + 1;
        partido.setIntentosIngesta(intentos);

        if (intentos >= MAX_INTENTOS_INGESTA) {
            partido.setStatsNotFound(true);
            log.warn("Partido {} bloqueado tras {} intentos fallidos de ingesta de estadísticas",
                    partido.getIdPartido(), intentos);
        }

        try {
            partidoRepository.save(partido);
        } catch (OptimisticLockingFailureException e) {
            // Otra sincronización (scheduler en vivo, sync manual) tocó este mismo partido
            // a la vez: se descarta este intento fallido sin abortar el resto del lote, la
            // próxima ronda vuelve a evaluarlo con la versión más reciente.
            log.warn("Partido {} modificado concurrentemente al registrar intento fallido, se descarta esta ronda: {}",
                    partido.getIdPartido(), e.getMessage());
        }
    }

    private void procesarEstadisticasEquipo(Partido partido, List<ApiTeamStatsResponseDTO.TeamStatsWrapper> teamStatsList) {
        List<EstadisticaEquipoPartido> estEquiposParaGuardar = new ArrayList<>();

        // Por id de equipo, para no crear dos filas si la respuesta repite un equipo: la
        // consulta de abajo no puede encontrar lo que todavía no se ha guardado, así que sin
        // este mapa la segunda aparición se llevaría su propia fila nueva y reventaría el
        // UNIQUE (id_partido, id_equipo) — el mismo fallo que tenían las de jugador.
        Map<Integer, EstadisticaEquipoPartido> yaPreparadas = new HashMap<>();

        teamStatsList.forEach(wrapper -> {
            Integer idEquipo = wrapper.getTeam().getId();
            Equipo equipo = obtenerEquipoDelPartido(partido, idEquipo);

            if (equipo != null) {
                EstadisticaEquipoPartido est = yaPreparadas.computeIfAbsent(idEquipo, id ->
                        estEquipoRepository
                                .findByPartidoAndEquipo(partido, equipo)
                                .orElse(new EstadisticaEquipoPartido()));

                if (est.getId() == null) {
                    est.setPartido(partido);
                    est.setEquipo(equipo);
                }

                wrapper.getStatistics().stream()
                        .filter(item -> item.getValue() != null)
                        .forEach(item -> mapearItemEstadisticaEquipo(est, item));

                if (!estEquiposParaGuardar.contains(est)) {
                    estEquiposParaGuardar.add(est);
                }
            }
        });

        estEquipoRepository.saveAll(estEquiposParaGuardar);
    }

    private void procesarEstadisticasJugadores(Partido partido, List<ApiPlayerStatsResponseDTO.TeamPlayersWrapper> teamPlayersList) {
        List<Jugador> jugadoresParaGuardar = new ArrayList<>();
        List<EstadisticaJugadorPartido> estadisticasParaGuardar = new ArrayList<>();

        List<Integer> idsJugadores = teamPlayersList.stream()
                .flatMap(teamWrapper -> teamWrapper.getPlayers().stream())
                .map(playerWrapper -> playerWrapper.getPlayer().getId())
                .distinct()
                .toList();

        Map<Integer, Jugador> jugadoresExistentes = new HashMap<>();
        if (!idsJugadores.isEmpty()) {
            jugadorRepository.findAllById(idsJugadores)
                    .forEach(j -> jugadoresExistentes.put(j.getIdJugador(), j));
        }

        // Una única consulta para el partido completo en vez de un findByPartidoAndJugador
        // por cada jugador (evitaba una query SQL individual por cada uno de los
        // ~20-40 jugadores de cada partido procesado).
        Map<Integer, EstadisticaJugadorPartido> estadisticasExistentes = new HashMap<>();
        estJugadorRepository.findByPartidoIdPartido(partido.getIdPartido())
                .forEach(est -> estadisticasExistentes.put(est.getJugador().getIdJugador(), est));

        teamPlayersList.forEach(teamWrapper -> {
            Integer idEquipo = teamWrapper.getTeam().getId();
            Equipo equipo = obtenerEquipoDelPartido(partido, idEquipo);

            if (equipo != null) {
                teamWrapper.getPlayers().stream()
                        .filter(p -> p.getStatistics() != null && !p.getStatistics().isEmpty())
                        .forEach(playerWrapper -> prepararEstadisticaJugadorIndividual(
                                partido, equipo, playerWrapper, jugadoresExistentes, estadisticasExistentes,
                                jugadoresParaGuardar, estadisticasParaGuardar));
            }
        });

        jugadorRepository.saveAll(jugadoresParaGuardar);
        estJugadorRepository.saveAll(estadisticasParaGuardar);
    }

    private void prepararEstadisticaJugadorIndividual(
            Partido partido,
            Equipo equipo,
            ApiPlayerStatsResponseDTO.PlayerStatsWrapper playerWrapper,
            Map<Integer, Jugador> jugadoresExistentes,
            Map<Integer, EstadisticaJugadorPartido> estadisticasExistentes,
            List<Jugador> jugadoresParaGuardar,
            List<EstadisticaJugadorPartido> estadisticasParaGuardar) {

        ApiPlayerStatsResponseDTO.PlayerInfoDTO infoJugador = playerWrapper.getPlayer();
        ApiPlayerStatsResponseDTO.PlayerDetailStats stats = playerWrapper.getStatistics().getFirst();

        Jugador jugador = jugadoresExistentes.getOrDefault(infoJugador.getId(), new Jugador());

        jugador.setIdJugador(infoJugador.getId());
        jugador.setNombre(infoJugador.getName());
        jugador.setFotoUrl(infoJugador.getPhoto());

        // Gateado por fecha del partido: un backfill que procese partidos fuera de orden
        // cronológico no debe pisar el club ACTUAL correcto con uno de un partido más
        // antiguo procesado después. null = todavía no gateado (jugador nunca visto, o
        // datos de antes de este fix), se fija sin comparar.
        LocalDateTime fechaPartido = partido.getFecha();
        if (jugador.getFechaEquipoActualizado() == null
                || fechaPartido == null
                || !fechaPartido.isBefore(jugador.getFechaEquipoActualizado())) {
            jugador.setEquipo(equipo);
            if (fechaPartido != null) {
                jugador.setFechaEquipoActualizado(fechaPartido);
            }
        }

        // Sin dedupe aquí a propósito: `jugadoresExistentes` ya viene lleno con los jugadores
        // que estaban en la base, así que un putIfAbsent dejaría fuera del saveAll justo a
        // esos —y con ellos la actualización de su club—. Si un jugador sale dos veces en la
        // respuesta, se manda dos veces la MISMA instancia, que es inofensivo. Lo que sí
        // rompía era duplicar la fila de estadísticas, y eso se arregla abajo.
        jugadoresParaGuardar.add(jugador);

        EstadisticaJugadorPartido estJugador = estadisticasExistentes.get(infoJugador.getId());

        if (estJugador == null) {
            estJugador = new EstadisticaJugadorPartido();
            estJugador.setPartido(partido);
            estJugador.setJugador(jugador);
            estJugador.setEquipo(equipo);

            // Meterlo en el mapa AQUÍ es lo que evita el duplicado. API-Football lista a
            // veces al mismo jugador dos veces en el mismo partido, y antes cada aparición
            // se llevaba su propio `new EstadisticaJugadorPartido()`: dos filas con el mismo
            // (id_partido, id_jugador), que es justo el único de la tabla. El INSERT del lote
            // entero se caía con "duplicate key ... estadisticas_jugador_partido_id_partido_
            // id_jugador_key", el partido contaba un intento fallido y, a los tres, quedaba
            // marcado como "sin estadísticas" para siempre — teniéndolas la API.
            estadisticasExistentes.put(infoJugador.getId(), estJugador);
            estadisticasParaGuardar.add(estJugador);
        }

        mapearEstadisticasJugador(estJugador, stats);
    }

    private Equipo obtenerEquipoDelPartido(Partido partido, Integer idEquipo) {
        if (partido.getEquipoLocal() != null && partido.getEquipoLocal().getIdEquipo().equals(idEquipo)) {
            return partido.getEquipoLocal();
        } else if (partido.getEquipoVisitante() != null && partido.getEquipoVisitante().getIdEquipo().equals(idEquipo)) {
            return partido.getEquipoVisitante();
        }
        log.warn("El ID de equipo {} devuelto por la API no coincide con los del partido {}", idEquipo, partido.getIdPartido());
        return null;
    }

    private void mapearItemEstadisticaEquipo(EstadisticaEquipoPartido est, ApiTeamStatsResponseDTO.StatisticItem item) {
        String tipo = item.getType();

        if (item.getValue() == null) return;

        String valorStr = item.getValue().toString().replace("%", "").trim();

        try {
            switch (tipo) {
                case "Ball Possession" -> est.setPosesion(new BigDecimal(valorStr));
                case "Total Shots" -> est.setTiros(Integer.parseInt(valorStr));
                case "Shots on Goal" -> est.setTirosPuerta(Integer.parseInt(valorStr));
                case "Corner Kicks" -> est.setCorners(Integer.parseInt(valorStr));
                case "Fouls" -> est.setFaltas(Integer.parseInt(valorStr));
                case "Yellow Cards" -> est.setTarjetasAmarillas(Integer.parseInt(valorStr));
                case "Red Cards" -> est.setTarjetasRojas(Integer.parseInt(valorStr));
                case "expected_goals" -> est.setGolesEsperados(new BigDecimal(valorStr));
                default -> {
                    if (est.getEstadisticasExtra() == null) {
                        est.setEstadisticasExtra(new HashMap<>());
                    }
                    est.getEstadisticasExtra().put(tipo, valorStr);
                }
            }
        } catch (NumberFormatException ignored) {
            // EXCEPCION IGNORADA SIGUE FLUJO
        }
    }

    private void mapearEstadisticasJugador(EstadisticaJugadorPartido estJugador, ApiPlayerStatsResponseDTO.PlayerDetailStats stats) {
        if (stats.getGames() != null) {
            estJugador.setMinutosJugados(stats.getGames().getMinutes());
            estJugador.setValoracion(parsearRatingSeguro(stats.getGames().getRating()));
            // games.substitute nunca se leía: EstadisticaJugadorPartido.titular quedaba
            // siempre null pese a ser @NotNull en el DTO de salida, y GET /partidos/{id}/jugadores
            // nunca distinguía titulares de suplentes.
            Boolean suplente = stats.getGames().getSubstitute();
            estJugador.setTitular(suplente != null ? !suplente : null);
        }
        if (stats.getGoals() != null) {
            estJugador.setGoles(stats.getGoals().getTotal());
            estJugador.setAsistencias(stats.getGoals().getAssists());
        }
        if (stats.getCards() != null) {
            estJugador.setTarjetasAmarillas(stats.getCards().getYellow());
            estJugador.setTarjetasRojas(stats.getCards().getRed());
        }
    }

    private BigDecimal parsearRatingSeguro(String rating) {
        if (rating == null) return null;
        try {
            return new BigDecimal(rating);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Trae el once de los partidos en juego que todavia no lo tienen.
     *
     * <p><b>Por que hace falta una peticion aparte.</b> El feed del directo no trae
     * alineaciones: comprobado sobre la respuesta real de {@code fixtures?live=all}, sus
     * claves son fixture, league, teams, goals, score y events, y nada mas. Asi que un
     * partido en vivo se quedaba sin once hasta que pasaran siete dias y entrara por el
     * camino de {@code guardarLoQueSePueda} -- o sea, nunca cuando importa.</p>
     *
     * <p><b>Y por que se puede guardar ya, sin esperar esos siete dias.</b> El guardia
     * existia porque las filas del once se escriben en la misma tabla que las estadisticas
     * de jugador, y se temia marcar como completo un partido al que aun le faltan. Pero la
     * consulta de pendientes exige que existan las DOS tablas (equipo Y jugador), y aqui
     * solo se crean filas de jugador: el partido sigue contando como pendiente. Ademas esa
     * consulta solo mira partidos FINALIZADOS, y estos estan en juego.</p>
     *
     * <p><b>Tambien los que estan a punto de empezar.</b> El once se publica entre 20 y 60
     * minutos antes del pitido inicial, que es justo cuando la gente lo quiere ver. Mirando
     * solo los partidos ya en juego, la alineacion aparecia cuando ya no servia de nada.</p>
     *
     * <p>Se piden de veinte en veinte y solo los que faltan, asi que son una peticion por
     * cada veinte partidos y una sola vez por partido, no en cada tick. Medido: 13 partidos
     * en las tres horas siguientes, o sea <b>una peticion</b>.</p>
     */
    @Override
    public int guardarAlineacionesPendientes(int maxPartidos, int horasAntes) {
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("UTC"));
        List<Partido> sinAlineacion = partidoRepository.findSinAlineacion(
                ahora, ahora.plusHours(horasAntes), PageRequest.of(0, maxPartidos));
        if (sinAlineacion.isEmpty()) {
            return 0;
        }

        Map<Integer, Partido> porId = sinAlineacion.stream()
                .collect(Collectors.toMap(Partido::getIdPartido, p -> p, (a, b) -> a));

        int conAlineacion = 0;
        for (List<Integer> tanda : particionar(new ArrayList<>(porId.keySet()),
                ApiFootballClient.MAX_FIXTURES_POR_PETICION)) {
            try {
                Map<Integer, FixtureItemDTO> items = indexarPorId(apiClient.getFixturesByIds(tanda));
                for (Integer id : tanda) {
                    FixtureItemDTO item = items.get(id);
                    if (item != null && guardarAlineacionSinEstadisticas(porId.get(id), item.getLineups())) {
                        conAlineacion++;
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudieron traer las alineaciones de la tanda {}: {}", tanda, e.getMessage());
            }
        }
        // También se deja constancia cuando no viene ninguna. Sin esta segunda rama, el log
        // callaba en los dos casos que hay que poder distinguir: "he preguntado y todavía no
        // hay once publicado" y "no he preguntado por nadie". Vigilando el prepartido la
        // noche del 08/09 no había forma de saber en cuál de los dos estábamos.
        if (conAlineacion > 0) {
            log.info("Alineaciones: {} partidos de {} pedidos ya tienen su once.",
                    conAlineacion, sinAlineacion.size());
        } else {
            log.info("Alineaciones: preguntado por {} partidos, ninguno tiene once publicado todavía.",
                    sinAlineacion.size());
        }
        return conAlineacion;
    }
}
