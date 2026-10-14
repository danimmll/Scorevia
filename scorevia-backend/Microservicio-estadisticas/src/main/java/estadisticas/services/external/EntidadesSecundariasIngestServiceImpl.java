package estadisticas.services.external;

import estadisticas.dto.external.*;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.equipos.Traspaso;
import estadisticas.entities.jugadores.HistorialBaja;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.ligas.Trofeo;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.equipos.TraspasoRepository;
import estadisticas.repositories.jugadores.HistorialBajaRepository;
import estadisticas.repositories.jugadores.JugadorRepository;
import estadisticas.repositories.ligas.TrofeoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntidadesSecundariasIngestServiceImpl implements EntidadesSecundariasIngestService {

    private static final String UNKNOWN = "Unknown";
    private static final String TIPO_DESCONOCIDO = "UNKNOWN";
    private static final Pattern IMPORTE_CON_SIMBOLO_AL_FINAL =
            Pattern.compile("(\\d[\\d.,]*\\s*[KkMm]?)\\s*([€$£])");
    private static final String DESCONOCIDO = "Desconocido";
    private static final int LOTE_ENTRENADORES = 200;
    private static final int LOTE_JUGADORES = 500;
    private static final int LOTE_EQUIPOS = 100;

    private final ApiFootballClient apiClient;
    private final JugadorRepository jugadorRepository;
    private final EquipoRepository equipoRepository;
    private final EntrenadorRepository entrenadorRepository;
    private final TrofeoRepository trofeoRepository;
    private final HistorialBajaRepository historialBajaRepository;
    private final TraspasoRepository traspasoRepository;
    private final PeticionesEnParalelo enParalelo;

    @Override
    public void ingestarTrofeosYBajasMasivo() {
        log.info("Iniciando ingesta de Trofeos y Bajas de jugadores por lotes...");
        int pageJugadores = 0;
        Page<Jugador> paginaJugadores;
        do {
            paginaJugadores = jugadorRepository.findAll(PageRequest.of(pageJugadores, LOTE_JUGADORES));
            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Cuota agotada. Deteniendo ingesta de trofeos/bajas de jugadores.");
                return;
            }
            procesarLoteDeJugadores(paginaJugadores.getContent());
            pageJugadores++;
        } while (paginaJugadores.hasNext());

        // Los entrenadores tienen su propio camino desde el 2026-09-06 y ya no van detras de
        // los 510.000 jugadores, que es la cola por la que nunca les llegaba el turno. Aqui se
        // llama al mismo, que ademas sabe a quien ha preguntado ya.
        ingestarTrofeosDeEntrenadores();
    }

    /**
     * Un lote de jugadores: primero las lecturas (dos por jugador, todas a la vez) y despues
     * los guardados, de uno en uno y en este hilo.
     */
    private void procesarLoteDeJugadores(List<Jugador> jugadores) {
        LocalDateTime haceUnMes = LocalDateTime.now(ZoneId.of("UTC")).minusDays(30);
        List<Jugador> pendientes = jugadores.stream()
                .filter(j -> j.getUltimaSyncBajas() == null || !j.getUltimaSyncBajas().isAfter(haceUnMes))
                .toList();
        if (pendientes.isEmpty()) {
            return;
        }

        List<Callable<ApiTrophiesResponseDTO>> lecturasTrofeos = pendientes.stream()
                .map(j -> (Callable<ApiTrophiesResponseDTO>) () ->
                        apiClient.getTrophies(String.valueOf(j.getIdJugador())))
                .toList();
        List<Callable<ApiSidelinedResponseDTO>> lecturasBajas = pendientes.stream()
                .map(j -> (Callable<ApiSidelinedResponseDTO>) () ->
                        apiClient.getSidelined(String.valueOf(j.getIdJugador())))
                .toList();

        List<ApiTrophiesResponseDTO> trofeos = enParalelo.leer(lecturasTrofeos);
        List<ApiSidelinedResponseDTO> bajas = enParalelo.leer(lecturasBajas);

        for (int i = 0; i < pendientes.size(); i++) {
            guardarTrofeosYBajasDeJugador(pendientes.get(i), trofeos.get(i), bajas.get(i));
        }
    }

    private void guardarTrofeosYBajasDeJugador(Jugador jugador, ApiTrophiesResponseDTO trofeosResponse,
                                               ApiSidelinedResponseDTO bajasResponse) {
        // Si fallaron las DOS lecturas no hay nada que guardar y marcarlo como hecho seria
        // perderlo hasta dentro de un mes: se deja como estaba para que vuelva a caer.
        if (trofeosResponse == null && bajasResponse == null) {
            return;
        }
        try {
            if (trofeosResponse != null && trofeosResponse.getResponse() != null
                    && !trofeosResponse.getResponse().isEmpty()) {
                procesarTrofeosJugador(jugador, trofeosResponse.getResponse());
            }

            if (bajasResponse != null && bajasResponse.getResponse() != null
                    && !bajasResponse.getResponse().isEmpty()) {
                procesarBajasBatch(jugador, bajasResponse.getResponse());
            }

            jugador.setUltimaSyncBajas(LocalDateTime.now(ZoneId.of("UTC")));
            jugadorRepository.save(jugador);

        } catch (Exception e) {
            log.error("Error al ingestar datos del jugador ID: {}", jugador.getIdJugador(), e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Se recorre siempre la primera pagina de pendientes, no la 1, la 2, la 3: cada
     * entrenador procesado se marca y sale del resultado, asi que la pagina siguiente es
     * otra vez la primera. Paginar hacia adelante sobre una consulta cuyas filas se van
     * borrando del resultado se salta un lote de cada dos.</p>
     */
    @Override
    public int ingestarTrofeosDeEntrenadores() {
        long pendientes = entrenadorRepository.countByUltimaSyncTrofeosIsNull();
        log.info("Trofeos de entrenadores: {} sin preguntar todavia.", pendientes);

        int procesados = 0;
        // Un fallo de red no marca al entrenador, para poder reintentarlo en otra tanda. Pero
        // sin recordarlos aqui volverian a salir los primeros en la consulta siguiente y la
        // tanda se quedaria dando vueltas sobre el mismo lote.
        Set<Integer> fallidos = new HashSet<>();

        while (true) {
            List<Entrenador> lote = entrenadorRepository
                    .findPendientesDeTrofeos(PageRequest.of(0, LOTE_ENTRENADORES))
                    .getContent().stream()
                    .filter(e -> !fallidos.contains(e.getIdEntrenador()))
                    .toList();

            if (lote.isEmpty()) {
                log.info("Trofeos de entrenadores: terminado, {} procesados, {} fallidos.",
                        procesados, fallidos.size());
                return procesados;
            }

            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Trofeos de entrenadores: cuota agotada tras {} entrenadores. "
                        + "Relanzar sigue por donde se quedo.", procesados);
                return procesados;
            }

            // Dos ahorros distintos, y conviene no confundirlos:
            //   - de 20 en 20: son 20 veces MENOS peticiones (la API agrupa la respuesta).
            //   - en paralelo: son las mismas peticiones, pero sin esperar unas por otras.
            List<List<Entrenador>> tandas = particionar(lote, ApiFootballClient.MAX_IDS_POR_PETICION);
            List<ApiTrophiesPorEntrenadorDTO> respuestas = enParalelo.leer(tandas.stream()
                    .map(tanda -> (Callable<ApiTrophiesPorEntrenadorDTO>) () ->
                            apiClient.getTrophiesByCoachs(tanda.stream().map(Entrenador::getIdEntrenador).toList()))
                    .toList());

            for (int i = 0; i < tandas.size(); i++) {
                procesados += guardarTanda(tandas.get(i), respuestas.get(i), fallidos);
            }
        }
    }

    /**
     * Guarda el palmares de una tanda de hasta 20 entrenadores.
     *
     * <p>La respuesta viene agrupada por id, pero <b>no trae una entrada por cada id pedido</b>:
     * al que no ha ganado nada la API sencillamente no lo menciona. Ese silencio es una
     * respuesta y se marca igual, que es justo lo que hace que el relleno termine.</p>
     *
     * @return cuantos entrenadores de la tanda han quedado resueltos.
     */
    private int guardarTanda(List<Entrenador> tanda, ApiTrophiesPorEntrenadorDTO respuesta,
                             Set<Integer> fallidos) {
        // null es "la peticion fallo": no se marca ninguno, se reintentan en otra tanda.
        if (respuesta == null) {
            tanda.forEach(e -> fallidos.add(e.getIdEntrenador()));
            return 0;
        }

        Map<Integer, List<ApiTrophiesResponseDTO.TrophyDTO>> porEntrenador = new HashMap<>();
        if (respuesta.getResponse() != null) {
            for (ApiTrophiesPorEntrenadorDTO.EntrenadorConTrofeosDTO fila : respuesta.getResponse()) {
                if (fila != null && fila.getId() != null && fila.getTrophies() != null) {
                    porEntrenador.put(fila.getId(), fila.getTrophies());
                }
            }
        }

        int resueltos = 0;
        for (Entrenador entrenador : tanda) {
            List<ApiTrophiesResponseDTO.TrophyDTO> trofeos =
                    porEntrenador.getOrDefault(entrenador.getIdEntrenador(), List.of());
            if (guardarTrofeosDeEntrenador(entrenador, trofeos)) {
                resueltos++;
            } else {
                fallidos.add(entrenador.getIdEntrenador());
            }
        }
        return resueltos;
    }

    private static <T> List<List<T>> particionar(List<T> lista, int tamano) {
        List<List<T>> trozos = new ArrayList<>();
        for (int desde = 0; desde < lista.size(); desde += tamano) {
            trozos.add(lista.subList(desde, Math.min(desde + tamano, lista.size())));
        }
        return trozos;
    }

    /**
     * Guarda el palmares que ha contestado la API y marca al entrenador como preguntado.
     *
     * <p>La marca se pone tambien cuando la API contesta que no tiene ninguno, que es el caso
     * de dos de cada tres: sin ella, "entrenador sin trofeos" y "entrenador sin preguntar" son
     * indistinguibles y el relleno gastaba 17.500 peticiones en cada relanzamiento sin
     * avanzar.</p>
     *
     * @param trofeos lo que la API dijo de el: la lista vacia significa "no ha ganado nada",
     *                que es una respuesta valida y se marca igual.
     * @return true si queda resuelto; false si fallo el guardado y conviene reintentarlo.
     */
    private boolean guardarTrofeosDeEntrenador(Entrenador entrenador,
                                               List<ApiTrophiesResponseDTO.TrophyDTO> trofeos) {
        try {
            if (!trofeos.isEmpty()) {
                procesarTrofeosEntrenador(entrenador, trofeos);
            }

            entrenador.setUltimaSyncTrofeos(LocalDateTime.now(ZoneId.of("UTC")));
            entrenadorRepository.save(entrenador);
            return true;

        } catch (Exception e) {
            log.error("Error al guardar los trofeos del entrenador ID: {}", entrenador.getIdEntrenador(), e);
            return false;
        }
    }

    @Override
    public void ingestarTraspasosPorEquipo() {
        log.info("Iniciando ingesta de Traspasos por lotes...");
        int pageEquipos = 0;
        Page<Equipo> paginaEquipos;
        do {
            paginaEquipos = equipoRepository.findAll(PageRequest.of(pageEquipos, LOTE_EQUIPOS));
            if (!apiClient.hayCuotaDisponibleParaBackfill()) {
                log.warn("Cuota agotada. Deteniendo ingesta de traspasos.");
                return;
            }
            procesarLoteDeEquipos(paginaEquipos.getContent());
            pageEquipos++;
        } while (paginaEquipos.hasNext());
    }

    /** Las lecturas del lote a la vez; los guardados, de uno en uno y en este hilo. */
    private void procesarLoteDeEquipos(List<Equipo> equipos) {
        LocalDateTime haceUnMes = LocalDateTime.now(ZoneId.of("UTC")).minusDays(30);
        List<Equipo> pendientes = equipos.stream()
                .filter(e -> e.getUltimaSyncTraspasos() == null || !e.getUltimaSyncTraspasos().isAfter(haceUnMes))
                .toList();
        if (pendientes.isEmpty()) {
            return;
        }

        List<ApiTransfersResponseDTO> respuestas = enParalelo.leer(pendientes.stream()
                .map(e -> (Callable<ApiTransfersResponseDTO>) () -> apiClient.getTransfersByTeam(e.getIdEquipo()))
                .toList());

        for (int i = 0; i < pendientes.size(); i++) {
            guardarTraspasosDeEquipo(pendientes.get(i), respuestas.get(i));
        }
    }

    private void guardarTraspasosDeEquipo(Equipo equipo, ApiTransfersResponseDTO transfersResponse) {
        // null es que la peticion fallo: no se marca el equipo, para que vuelva a caer.
        if (transfersResponse == null) {
            return;
        }
        try {
            if (transfersResponse.getResponse() != null && !transfersResponse.getResponse().isEmpty()) {
                procesarTraspasos(transfersResponse.getResponse());
            }

            equipo.setUltimaSyncTraspasos(LocalDateTime.now(ZoneId.of("UTC")));
            equipoRepository.save(equipo);

        } catch (Exception e) {
            log.error("Error al ingestar traspasos del equipo: {}: {}", equipo.getIdEquipo(), e.getMessage());
        }
    }

    /**
     * Quita las filas resumen que API-Football mete en el palmares.
     *
     * <p>Ademas de una fila por cada titulo con su temporada, la respuesta trae <b>una fila sin
     * temporada por cada combinacion competicion+resultado</b>. No es un titulo mas ni un
     * titulo cuya fecha no conste: es un total. Cristiano llega con 5 "UEFA Champions League /
     * Winner" fechadas —sus 5 Champions, correctas— y una sexta sin fecha.</p>
     *
     * <p>Medido sobre la base entera: de las <b>108.788</b> filas sin temporada, <b>108.787</b>
     * tienen su gemela fechada, y nunca hay mas de una por grupo. Guardarlas ensuciaba la
     * linea del tiempo con un cajon de "sin temporada" que repetia lo que ya salia arriba.</p>
     *
     * <p>La que no tenga gemela fechada <b>si</b> se guarda: ahi la temporada falta de verdad
     * (en toda la base es un unico caso, pero tirarla seria perder un titulo).</p>
     */
    private List<ApiTrophiesResponseDTO.TrophyDTO> sinFilasResumen(List<ApiTrophiesResponseDTO.TrophyDTO> trofeosApi) {
        Set<List<String>> conTemporada = new HashSet<>();
        for (ApiTrophiesResponseDTO.TrophyDTO dto : trofeosApi) {
            if (tieneTemporada(dto)) {
                conTemporada.add(claveSinTemporada(dto));
            }
        }
        List<ApiTrophiesResponseDTO.TrophyDTO> utiles = new ArrayList<>();
        for (ApiTrophiesResponseDTO.TrophyDTO dto : trofeosApi) {
            if (tieneTemporada(dto) || !conTemporada.contains(claveSinTemporada(dto))) {
                utiles.add(dto);
            }
        }
        return utiles;
    }

    private boolean tieneTemporada(ApiTrophiesResponseDTO.TrophyDTO dto) {
        return dto.getSeason() != null && !dto.getSeason().isBlank();
    }

    /** Competicion + pais + resultado: lo que comparten la fila resumen y sus fechadas. */
    private List<String> claveSinTemporada(ApiTrophiesResponseDTO.TrophyDTO dto) {
        return List.of(
                dto.getLeague() != null ? dto.getLeague() : "",
                dto.getCountry() != null ? dto.getCountry() : "",
                dto.getPlace() != null ? dto.getPlace() : "");
    }

    private void procesarTrofeosJugador(Jugador jugador, List<ApiTrophiesResponseDTO.TrophyDTO> trofeosApiCrudos) {
        List<ApiTrophiesResponseDTO.TrophyDTO> trofeosApi = sinFilasResumen(trofeosApiCrudos);
        List<Trofeo> nuevosTrofeos = new ArrayList<>();
        // FIX: la comprobación de duplicados solo miraba la BD, y nada se escribe hasta el
        // saveAll del final -- así que si la respuesta de la API traía el MISMO título repetido
        // (cosa que hace: devuelve una entrada por cada club del jugador esa temporada), las N
        // copias pasaban el filtro y entraban las N. En la BD había palmarés con el mismo
        // título 5 y 6 veces. Este set descarta los repetidos dentro del propio lote.
        Set<List<String>> vistosEnEsteLote = new HashSet<>();
        for (ApiTrophiesResponseDTO.TrophyDTO dto : trofeosApi) {
            if (dto.getLeague() == null || dto.getPlace() == null) {
                continue;
            }
            if (!vistosEnEsteLote.add(claveTrofeo(dto))) {
                continue;
            }
            boolean yaExiste = trofeoRepository
                    .existeTrofeoJugador(
                            jugador.getIdJugador(), dto.getLeague(), dto.getSeason(), dto.getPlace(), dto.getCountry());
            if (!yaExiste) {
                nuevosTrofeos.add(crearInstanciaTrofeoJugador(dto, jugador));
            }
        }
        if (!nuevosTrofeos.isEmpty()) {
            trofeoRepository.saveAll(nuevosTrofeos);
        }
    }

    /** Misma clave natural que el índice único uq_trofeo_natural de la base de datos. */
    private List<String> claveTrofeo(ApiTrophiesResponseDTO.TrophyDTO dto) {
        return List.of(
                dto.getLeague(),
                dto.getCountry() != null ? dto.getCountry() : "",
                dto.getSeason() != null ? dto.getSeason() : "",
                dto.getPlace());
    }

    private Trofeo crearInstanciaTrofeoJugador(ApiTrophiesResponseDTO.TrophyDTO dto, Jugador jugador) {
        Trofeo trofeo = new Trofeo();
        trofeo.setJugador(jugador);
        trofeo.setCompeticion(dto.getLeague());
        trofeo.setPais(dto.getCountry());
        trofeo.setTemporada(dto.getSeason());
        trofeo.setResultado(dto.getPlace());
        return trofeo;
    }

    private void procesarTrofeosEntrenador(Entrenador entrenador, List<ApiTrophiesResponseDTO.TrophyDTO> trofeosApiCrudos) {
        List<ApiTrophiesResponseDTO.TrophyDTO> trofeosApi = sinFilasResumen(trofeosApiCrudos);
        List<Trofeo> nuevosTrofeos = new ArrayList<>();
        // Mismo filtro dentro del lote que en procesarTrofeosJugador (ver el porqué allí).
        Set<List<String>> vistosEnEsteLote = new HashSet<>();
        for (ApiTrophiesResponseDTO.TrophyDTO dto : trofeosApi) {
            if (dto.getLeague() == null || dto.getPlace() == null) {
                continue;
            }
            if (!vistosEnEsteLote.add(claveTrofeo(dto))) {
                continue;
            }
            boolean yaExiste = trofeoRepository
                    .existeTrofeoEntrenador(
                            entrenador.getIdEntrenador(), dto.getLeague(), dto.getSeason(), dto.getPlace(), dto.getCountry());
            if (!yaExiste) {
                nuevosTrofeos.add(crearInstanciaTrofeoEntrenador(dto, entrenador));
            }
        }
        if (!nuevosTrofeos.isEmpty()) {
            trofeoRepository.saveAll(nuevosTrofeos);
        }
    }

    private void procesarBajasBatch(Jugador jugador, List<ApiSidelinedResponseDTO.SidelinedDTO> bajasApi) {
        List<HistorialBaja> paraGuardar = new ArrayList<>();
        for (ApiSidelinedResponseDTO.SidelinedDTO dto : bajasApi) {
            HistorialBaja baja = construirBaja(dto, jugador);
            if (baja == null) {
                continue;
            }
            HistorialBaja existente = historialBajaRepository
                    .findByJugador_IdJugadorAndTipoAndFechaInicio(
                            jugador.getIdJugador(), baja.getTipo(), baja.getFechaInicio())
                    .orElse(null);
            if (existente != null) {
                existente.setFechaFin(baja.getFechaFin());
                existente.setActivo(baja.getActivo());
                paraGuardar.add(existente);
            } else {
                paraGuardar.add(baja);
            }
        }
        if (!paraGuardar.isEmpty()) {
            historialBajaRepository.saveAll(paraGuardar);
        }
    }

    private HistorialBaja construirBaja(ApiSidelinedResponseDTO.SidelinedDTO dto, Jugador jugador) {
        HistorialBaja baja = new HistorialBaja();
        baja.setJugador(jugador);
        baja.setTipo(dto.getType());

        parsearFechasBaja(baja, dto, jugador);

        if (baja.getFechaInicio() == null) {
            log.warn("Baja del jugador {} descartada: fecha de inicio ausente o inválida ('{}')",
                    jugador.getIdJugador(), dto.getStart());
            return null;
        }

        baja.setActivo(baja.getFechaFin() == null);
        return baja;
    }

    private void parsearFechasBaja(HistorialBaja baja, ApiSidelinedResponseDTO.SidelinedDTO dto, Jugador jugador) {
        if (dto.getStart() != null) {
            try {
                baja.setFechaInicio(LocalDate.parse(dto.getStart()));
            } catch (DateTimeParseException e) {
                log.trace("Fecha de inicio no parseable en baja del jugador {}: '{}'", jugador.getIdJugador(), dto.getStart());
            }
        }
        if (dto.getEnd() != null) {
            try {
                baja.setFechaFin(LocalDate.parse(dto.getEnd()));
            } catch (DateTimeParseException e) {
                log.trace("Fecha de fin no parseable (probablemente '{}') en baja del jugador {}", UNKNOWN, jugador.getIdJugador());
            }
        }
    }

    private void procesarTraspasos(List<ApiTransfersResponseDTO.TransferWrapperDTO> transferData) {
        List<Traspaso> traspasosParaGuardar = new ArrayList<>();
        // Nada se escribe hasta el saveAll del final, asi que las filas nuevas de este lote no
        // se ven al consultar la base. Se van acumulando por jugador para poder compararlas
        // entre si con la misma regla que contra la base (ver esLaMismaOperacion).
        Map<Integer, List<Traspaso>> nuevosPorJugador = new HashMap<>();

        for (ApiTransfersResponseDTO.TransferWrapperDTO data : transferData) {
            if (data.getPlayer() == null || data.getPlayer().getId() == null) {
                continue;
            }
            Jugador jugador = jugadorRepository.findById(data.getPlayer().getId())
                    .orElseGet(() -> crearJugadorBasico(data.getPlayer()));

            List<Traspaso> yaMapeados =
                    nuevosPorJugador.computeIfAbsent(jugador.getIdJugador(), id -> new ArrayList<>());

            for (ApiTransfersResponseDTO.TransferDetailDTO detail : data.getTransfers()) {
                Traspaso traspaso = mapearDetalleTraspaso(detail, jugador, yaMapeados);
                if (traspaso != null) {
                    yaMapeados.add(traspaso);
                    traspasosParaGuardar.add(traspaso);
                }
            }
        }
        if (!traspasosParaGuardar.isEmpty()) {
            traspasoRepository.saveAll(traspasosParaGuardar);
        }
    }

    /**
     * Si dos filas del mismo jugador y la misma fecha son la misma operacion.
     *
     * <p>Lo son salvo que se sepa que no: solo cuentan como operaciones distintas cuando
     * <b>los dos</b> extremos son conocidos y <b>los dos</b> difieren. Con eso:</p>
     *
     * <ul>
     *   <li>Girona -&gt; Atletico y (desconocido) -&gt; Atletico son la misma: es la fila que se
     *       ingirio desde el club que vende y la que se ingirio desde el que compra, con el
     *       origen a null porque ese club aun no estaba en nuestra tabla.</li>
     *   <li>Nottingham -&gt; Blackburn y Ashington -&gt; Blackburn tambien son la misma: un
     *       jugador no ficha dos veces por el mismo club el mismo dia, uno de los dos origenes
     *       es un dato malo de la API (se han medido 4.036 casos asi).</li>
     *   <li>X -&gt; Y y Y -&gt; X <b>no</b> lo son: es una cesion y su vuelta, y las dos son
     *       reales.</li>
     * </ul>
     */
    private boolean esLaMismaOperacion(Traspaso guardado, Equipo origen, Equipo destino) {
        boolean origenesDistintos = guardado.getEquipoOrigen() != null && origen != null
                && !guardado.getEquipoOrigen().getIdEquipo().equals(origen.getIdEquipo());
        boolean destinosDistintos = guardado.getEquipoDestino() != null && destino != null
                && !guardado.getEquipoDestino().getIdEquipo().equals(destino.getIdEquipo());
        if (origenesDistintos && destinosDistintos) {
            return false;
        }
        // Y tampoco lo son si juntarlas dejaria al jugador saliendo y entrando en el mismo club
        // el mismo dia. Pasaba al emparejar "(desconocido) -> Atletico" con "Atletico -> X":
        // como un extremo de cada una venia a null, la regla de arriba las daba por la misma y
        // el hueco se rellenaba con el club que ya estaba al otro lado.
        return !mismoClubAlUnirlas(guardado, origen, destino);
    }

    /** Si completar {@code guardado} con estos clubes dejaria origen y destino iguales. */
    private boolean mismoClubAlUnirlas(Traspaso guardado, Equipo origen, Equipo destino) {
        Integer origenFinal = guardado.getEquipoOrigen() != null
                ? guardado.getEquipoOrigen().getIdEquipo()
                : (origen != null ? origen.getIdEquipo() : null);
        Integer destinoFinal = guardado.getEquipoDestino() != null
                ? guardado.getEquipoDestino().getIdEquipo()
                : (destino != null ? destino.getIdEquipo() : null);

        if (origenFinal == null || !origenFinal.equals(destinoFinal)) {
            return false;
        }
        // Salvo que la fila guardada ya viniera asi de la API: los ascensos del filial se
        // publican con el mismo club a los dos lados y son legitimos.
        boolean guardadaYaEraDelMismoClub = guardado.getEquipoOrigen() != null
                && guardado.getEquipoDestino() != null;
        return !guardadaYaEraDelMismoClub;
    }

    /** La fila ya conocida que corresponde a esta operacion, mirando primero las del lote. */
    private Traspaso buscarMismaOperacion(Jugador jugador, LocalDate fecha, Equipo origen,
                                          Equipo destino, List<Traspaso> yaMapeados) {
        for (Traspaso candidato : yaMapeados) {
            if (fecha.equals(candidato.getFecha()) && esLaMismaOperacion(candidato, origen, destino)) {
                return candidato;
            }
        }
        for (Traspaso candidato : traspasoRepository.findByJugadorAndFecha(jugador, fecha)) {
            if (esLaMismaOperacion(candidato, origen, destino)) {
                return candidato;
            }
        }
        return null;
    }

    private Jugador crearJugadorBasico(ApiTransfersResponseDTO.PlayerInfoDTO info) {
        Jugador nuevoJugador = new Jugador();
        nuevoJugador.setIdJugador(info.getId());
        nuevoJugador.setNombre(info.getName() != null ? info.getName() : DESCONOCIDO);
        return jugadorRepository.save(nuevoJugador);
    }

    private Traspaso mapearDetalleTraspaso(ApiTransfersResponseDTO.TransferDetailDTO detail, Jugador jugador,
                                           List<Traspaso> yaMapeados) {
        if (detail.getDate() == null) {
            return null;
        }

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(detail.getDate());
        } catch (DateTimeParseException e) {
            log.trace("Traspaso con fecha no parseable para el jugador {}: '{}'", jugador.getIdJugador(), detail.getDate());
            return null;
        }

        Equipo equipoOrigen = null;
        Equipo equipoDestino = null;
        if (detail.getTeams() != null) {
            equipoOrigen = resolverEquipo(detail.getTeams().getOut());
            equipoDestino = resolverEquipo(detail.getTeams().getIn());
        }

        // La API mete en un mismo campo ("type") dos cosas distintas: la categoría de la
        // operación ("Loan", "Free agent", "N/A") o directamente el importe ("€ 55M", "2M €").
        // Guardarlo tal cual hacía que la MISMA operación quedara etiquetada de formas
        // distintas según cuándo se sincronizara, y la comprobación de duplicados compara el
        // tipo: nunca coincidían y se reinsertaba. Aquí se separa en un código estable (tipo) y
        // un importe (coste), con las mismas reglas que la migración 2026-09-03.
        String valorApi = detail.getType();
        String tipo = normalizarTipoTraspaso(valorApi);
        String coste = normalizarCosteTraspaso(valorApi);

        // Si ya esta guardado no se descarta sin mirar: se aprovecha para completarlo. La API
        // publica muchas operaciones como 'UNKNOWN' y las concreta despues, y a veces manda el
        // club de un extremo que la vez anterior no supimos resolver. Antes las dos cosas
        // entraban como una fila nueva en vez de arreglar la que ya habia.
        Traspaso yaGuardado = buscarMismaOperacion(jugador, fecha, equipoOrigen, equipoDestino, yaMapeados);
        if (yaGuardado != null) {
            actualizarSiMejora(yaGuardado, tipo, coste, equipoOrigen, equipoDestino);
            return null;
        }

        Traspaso traspaso = new Traspaso();
        traspaso.setJugador(jugador);
        traspaso.setFecha(fecha);
        traspaso.setTipo(tipo);
        traspaso.setCoste(coste);
        traspaso.setEquipoOrigen(equipoOrigen);
        traspaso.setEquipoDestino(equipoDestino);
        return traspaso;
    }

    private boolean esImporteMonetario(String valor) {
        return valor != null && valor.chars().anyMatch(Character::isDigit);
    }

    /**
     * Código estable para el tipo de operación. Se guarda el código y no la etiqueta traducida
     * para no atar el idioma de la interfaz a la base de datos: el texto visible lo pone el
     * frontend.
     */
    /**
     * Corrige un traspaso ya guardado cuando la API trae un dato mejor.
     *
     * <p>Solo se pisa lo que <b>no se sabia</b>: un 'UNKNOWN' que pasa a 'FREE' se corrige, y
     * un 'FREE' que llega como 'UNKNOWN' no. Al reves se perderia informacion cada vez que la
     * API tuviera un mal dia, que es peor que quedarse con el dato viejo.</p>
     */
    private static boolean esElMismoEquipo(Equipo uno, Equipo otro) {
        return uno != null && otro != null && uno.getIdEquipo().equals(otro.getIdEquipo());
    }

    private void actualizarSiMejora(Traspaso guardado, String tipoNuevo, String costeNuevo,
                                    Equipo origenNuevo, Equipo destinoNuevo) {
        boolean cambia = false;

        if (tipoNuevo != null && !TIPO_DESCONOCIDO.equals(tipoNuevo)
                && !tipoNuevo.equals(guardado.getTipo())
                && (guardado.getTipo() == null || TIPO_DESCONOCIDO.equals(guardado.getTipo()))) {
            guardado.setTipo(tipoNuevo);
            cambia = true;
        }
        if (costeNuevo != null && guardado.getCoste() == null) {
            guardado.setCoste(costeNuevo);
            cambia = true;
        }
        // Y los clubes que faltaban, que es lo que de verdad evita el duplicado: la segunda
        // ingesta de la misma operacion —la que llega desde el otro club— completa la fila en
        // vez de crear una al lado. Solo se rellena el hueco; un club ya sabido no se pisa.
        // La red de seguridad de la regla de emparejado: rellenar un hueco nunca puede dejar
        // al jugador saliendo y entrando en el mismo club el mismo dia.
        if (guardado.getEquipoOrigen() == null && origenNuevo != null
                && !esElMismoEquipo(origenNuevo, guardado.getEquipoDestino())) {
            guardado.setEquipoOrigen(origenNuevo);
            cambia = true;
        }
        if (guardado.getEquipoDestino() == null && destinoNuevo != null
                && !esElMismoEquipo(destinoNuevo, guardado.getEquipoOrigen())) {
            guardado.setEquipoDestino(destinoNuevo);
            cambia = true;
        }
        // Sin id todavia es una fila de este mismo lote: se corrige en memoria y la guarda el
        // saveAll del final. Un save() aqui la insertaria ahora y otra vez despues.
        if (cambia && guardado.getIdTraspaso() != null) {
            traspasoRepository.save(guardado);
        }
    }

    private String normalizarTipoTraspaso(String valorApi) {
        if (valorApi == null) return TIPO_DESCONOCIDO;
        String v = valorApi.trim();
        if (v.isEmpty() || "-".equals(v) || v.equalsIgnoreCase("N/A")) return TIPO_DESCONOCIDO;

        String minusculas = v.toLowerCase(Locale.ROOT);
        if (minusculas.contains("free")) return "FREE";
        if (minusculas.contains("back from loan") || minusculas.contains("return from loan")
                || minusculas.contains("end of loan")) return "LOAN_RETURN";
        if (minusculas.contains("loan")) return "LOAN";
        if (minusculas.contains("swap")) return "SWAP";
        if (minusculas.contains("raise")) return "PROMOTION";
        if (minusculas.contains("end of career")) return "RETIRED";
        if (minusculas.contains("transfer") || esImporteMonetario(v)) return "TRANSFER";
        return TIPO_DESCONOCIDO;
    }

    /** Devuelve el importe normalizado ("2M €" -> "€ 2M"), o null si el valor no es un importe. */
    private String normalizarCosteTraspaso(String valorApi) {
        if (!esImporteMonetario(valorApi)) return null;
        String v = valorApi.trim().replaceAll("\\s+", " ");
        Matcher sufijo = IMPORTE_CON_SIMBOLO_AL_FINAL.matcher(v);
        return sufijo.matches() ? sufijo.group(2) + " " + sufijo.group(1) : v;
    }

    private Equipo resolverEquipo(TeamInfoDTO teamInfo) {
        if (teamInfo == null || teamInfo.getId() == null) {
            return null;
        }
        return equipoRepository.findById(teamInfo.getId()).orElse(null);
    }

    private Trofeo crearInstanciaTrofeoEntrenador(ApiTrophiesResponseDTO.TrophyDTO dto, Entrenador entrenador) {
        Trofeo trofeo = new Trofeo();
        trofeo.setEntrenador(entrenador);
        trofeo.setCompeticion(dto.getLeague());
        trofeo.setPais(dto.getCountry());
        trofeo.setTemporada(dto.getSeason());
        trofeo.setResultado(dto.getPlace());
        return trofeo;
    }
}