package estadisticas.services.alineaciones;

import estadisticas.dto.alineaciones.AlineacionesPartidoDTO;
import estadisticas.dto.alineaciones.EquipoAlineacionDTO;
import estadisticas.dto.alineaciones.JugadorAlineacionDTO;
import estadisticas.dto.arbitros.ArbitroDTO;
import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.entities.arbitros.Arbitro;
import estadisticas.entities.entrenadores.Entrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.jugadores.Jugador;
import estadisticas.entities.partidos.EstadisticaJugadorPartido;
import estadisticas.entities.partidos.Partido;
import estadisticas.exception.NotFoundException;
import estadisticas.repositories.entrenadores.EntrenadorRepository;
import estadisticas.repositories.entrenadores.HistorialEntrenadorRepository;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import estadisticas.repositories.partidos.EstadisticaEquipoPartidoRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Monta los dos onces de un partido a partir de lo que ya hay en la base.
 *
 * <p>No cuesta ninguna petición: sale de lo que ya está en la base.</p>
 *
 * <p>Hay dos calidades de dato y se prefiere siempre la mejor. Si el partido se ingirió con la
 * <b>alineación oficial</b> (que viene dentro del fixture al pedirlo por id), se usan su dibujo
 * real, el entrenador de ese partido y el sitio exacto de cada jugador en el campo. Si no
 * —partidos antiguos, o competiciones que no publican alineaciones—, se cae a lo de siempre:
 * el once y el banquillo salen de {@code titular}, el dibujo se deduce contando titulares por
 * línea ({@code formacionDeducida = true}) y el entrenador, del club.</p>
 */
@Service
@RequiredArgsConstructor
public class AlineacionServiceImpl implements AlineacionService {

    /** Orden en el que se pinta un once: primero el portero, al final los delanteros. */
    private static final List<String> ORDEN_LINEAS = List.of("G", "D", "M", "F");

    /** Las líneas que forman el dibujo. El portero no cuenta: un 4-3-3 son diez jugadores. */
    private static final List<String> LINEAS_DEL_DIBUJO = List.of("D", "M", "F");

    /**
     * La posicion "de siempre" del jugador traducida a linea, para cuando el partido no dice
     * en que puesto jugo.
     *
     * <p>Es lo que rescata las alineaciones antiguas. La linea sale de
     * {@code estadisticas_jugador_partido.posicion_campo}, que es la posicion <b>en ese
     * partido</b>, y en 2.932.997 de 7.055.261 filas —un 42%— llega vacia: son los partidos
     * ingeridos de temporadas viejas, donde la API da el once y el banquillo pero no el
     * puesto de cada uno. Sin este respaldo, esos partidos ensenaban una lista de nombres sin
     * dibujo y sin poder colocar a nadie en el campo.</p>
     *
     * <p>La posicion natural es menos precisa —un lateral reconvertido a extremo sigue
     * fichado como defensa— pero es muchisimo mejor que nada, y en el 90% de los casos
     * coincide. Se marca igualmente {@code formacionDeducida = true}, asi que la interfaz
     * puede avisar de que el dibujo no es oficial.</p>
     */
    private static final Map<String, String> LINEA_POR_POSICION_NATURAL = Map.of(
            "GOALKEEPER", "G",
            "DEFENDER", "D",
            "MIDFIELDER", "M",
            "ATTACKER", "F",
            // 573 fichas usan "Forward" en vez de "Attacker"; son lo mismo.
            "FORWARD", "F");

    private final PartidoRepository partidoRepository;
    private final EstadisticaJugadorPartidoRepository estJugadorRepository;
    private final EntrenadorRepository entrenadorRepository;
    private final EstadisticaEquipoPartidoRepository estEquipoRepository;
    private final HistorialEntrenadorRepository historialEntrenadorRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "alineacionesPartido", key = "#idPartido")
    public AlineacionesPartidoDTO getAlineacionesPartido(Integer idPartido) {
        Partido partido = partidoRepository.findById(idPartido)
                .orElseThrow(() -> new NotFoundException("Partido no encontrado", "partido-no-encontrado"));

        List<EstadisticaJugadorPartido> stats = estJugadorRepository.findByPartidoIdPartido(idPartido);

        // Por id de equipo, no por nombre: aquí sí es fiable, porque id_equipo es NOT NULL en
        // estadisticas_jugador_partido (a diferencia de estadisticas_equipo_partido, donde el
        // frontend tiene que emparejar por nombre porque hay filas antiguas con la FK sin
        // enlazar).
        Map<Integer, List<EstadisticaJugadorPartido>> porEquipo = stats.stream()
                .filter(s -> s.getEquipo() != null)
                .collect(Collectors.groupingBy(s -> s.getEquipo().getIdEquipo()));

        return AlineacionesPartidoDTO.builder()
                .idPartido(idPartido)
                .hayDatos(!stats.isEmpty())
                .arbitro(aArbitroDTO(partido.getArbitro()))
                .local(aEquipoAlineacion(idPartido, partido.getEquipoLocal(), partido.getTemporada(), porEquipo))
                .visitante(aEquipoAlineacion(idPartido, partido.getEquipoVisitante(), partido.getTemporada(), porEquipo))
                .build();
    }

    private ArbitroDTO aArbitroDTO(Arbitro arbitro) {
        if (arbitro == null) {
            return null;
        }
        return ArbitroDTO.builder()
                .idArbitro(arbitro.getIdArbitro())
                .nombre(arbitro.getNombre())
                // El país es NOT NULL DEFAULT '' en la tabla: se manda null en vez de una
                // cadena vacía para que el cliente no tenga que distinguir los dos casos.
                .pais(arbitro.getPais() == null || arbitro.getPais().isBlank() ? null : arbitro.getPais())
                .build();
    }

    private EquipoAlineacionDTO aEquipoAlineacion(Integer idPartido, Equipo equipo, Integer temporada,
                                                  Map<Integer, List<EstadisticaJugadorPartido>> porEquipo) {
        if (equipo == null) {
            return null;
        }

        List<EstadisticaJugadorPartido> delEquipo = porEquipo.getOrDefault(equipo.getIdEquipo(), List.of());

        List<JugadorAlineacionDTO> titulares = delEquipo.stream()
                .filter(s -> Boolean.TRUE.equals(s.getTitular()))
                // Con la estadistica entera y no solo su posicion_campo: asi los partidos
                // antiguos, que no la traen, se ordenan por la posicion natural del jugador en
                // vez de salir todos al final con el portero en medio.
                .sorted(Comparator.comparingInt(this::indiceDeLinea))
                .map(this::aJugadorDTO)
                .toList();

        // Los suplentes se ordenan por minutos jugados descendente: los que entraron primero
        // arriba, y los que no llegaron a jugar (0 minutos o sin dato) al final del banquillo.
        List<JugadorAlineacionDTO> suplentes = delEquipo.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getTitular()))
                .sorted(Comparator.comparingInt((EstadisticaJugadorPartido s) ->
                        s.getMinutosJugados() == null ? 0 : s.getMinutosJugados()).reversed())
                .map(this::aJugadorDTO)
                .toList();

        // La alineación oficial, si se ingirió: trae el dibujo real y el entrenador de ESTE
        // partido, que es mejor que deducir el uno y suponer el otro.
        Optional<EstadisticaEquipoPartido> filaEquipo =
                estEquipoRepository.findByPartido_IdPartidoAndEquipo_IdEquipo(idPartido, equipo.getIdEquipo());

        String formacionOficial = filaEquipo.map(EstadisticaEquipoPartido::getSistemaTactico)
                .filter(f -> f != null && !f.isBlank())
                .orElse(null);

        Optional<Entrenador> entrenadorDelPartido = filaEquipo
                .map(EstadisticaEquipoPartido::getIdEntrenador)
                .flatMap(this::buscarEntrenadorPorId);
        Optional<Entrenador> entrenadorDeLaTemporada = entrenadorDelPartido.isPresent()
                ? entrenadorDelPartido
                : buscarEntrenadorDeLaTemporada(equipo.getIdEquipo(), temporada);
        Optional<Entrenador> entrenador = entrenadorDeLaTemporada.or(() -> buscarEntrenadorActual(equipo.getIdEquipo()));

        return EquipoAlineacionDTO.builder()
                .idEquipo(equipo.getIdEquipo())
                .nombre(equipo.getNombre())
                .escudo(equipo.getEscudoUrl())
                .formacion(formacionOficial != null ? formacionOficial : deducirFormacion(titulares))
                .formacionDeducida(formacionOficial == null)
                .entrenador(entrenador.map(this::aEntrenadorDTO).orElse(null))
                .entrenadorEsElActual(entrenador.isPresent() && entrenadorDeLaTemporada.isEmpty())
                .titulares(titulares)
                .suplentes(suplentes)
                .build();
    }

    /**
     * El entrenador que la alineación oficial dice que dirigió el partido.
     *
     * <p>Puede no estar en {@code entrenadores}: en la alineación llega con id, nombre y foto,
     * pero darlo de alta es otra ingesta. Si no está, se cae al camino de siempre en vez de
     * enseñar una ficha a medias.</p>
     */
    private Optional<Entrenador> buscarEntrenadorPorId(Integer idEntrenador) {
        return idEntrenador == null ? Optional.empty() : entrenadorRepository.findById(idEntrenador);
    }

    /**
     * Quién dirigía a este equipo en la temporada del partido, según {@code historial_entrenadores}.
     *
     * <p>Se mira esto antes que el entrenador actual del club porque, si no, la ficha de un
     * partido de hace diez años afirmaría que lo dirigió quien está hoy en el banquillo.</p>
     */
    private Optional<Entrenador> buscarEntrenadorDeLaTemporada(Integer idEquipo, Integer temporada) {
        if (temporada == null) {
            return Optional.empty();
        }
        return historialEntrenadorRepository.findByEquipo_IdEquipoAndTemporada(idEquipo, temporada).stream()
                .map(estadisticas.entities.entrenadores.HistorialEntrenador::getEntrenador)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    // Un club puede tener más de una fila en `entrenadores` (segundos entrenadores, o restos
    // de sincronizaciones anteriores). Se coge el primero, igual que hace InternalDataService
    // al validar participantes: no hay campo que distinga al primer entrenador del resto.
    private Optional<Entrenador> buscarEntrenadorActual(Integer idEquipo) {
        return entrenadorRepository.findByEquipo_IdEquipo(idEquipo).stream().findFirst();
    }

    private EntrenadorDTO aEntrenadorDTO(Entrenador entrenador) {
        return EntrenadorDTO.builder()
                .idEntrenador(entrenador.getIdEntrenador())
                .nombre(entrenador.getNombre())
                .edad(entrenador.getEdad())
                .nacionalidad(entrenador.getNacionalidad())
                .foto(entrenador.getFoto())
                .build();
    }

    private JugadorAlineacionDTO aJugadorDTO(EstadisticaJugadorPartido stat) {
        Jugador jugador = stat.getJugador();
        return JugadorAlineacionDTO.builder()
                .idJugador(jugador != null ? jugador.getIdJugador() : null)
                .nombre(jugador != null ? jugador.getNombre() : null)
                .foto(jugador != null ? jugador.getFotoUrl() : null)
                .dorsal(jugador != null ? jugador.getDorsal() : null)
                .linea(lineaDe(stat))
                .grid(stat.getPosicionGrid())
                .posicionNatural(jugador != null ? jugador.getPosicion() : null)
                .minutosJugados(stat.getMinutosJugados())
                .goles(stat.getGoles())
                .asistencias(stat.getAsistencias())
                .tarjetasAmarillas(stat.getTarjetasAmarillas())
                .tarjetasRojas(stat.getTarjetasRojas())
                .valoracion(stat.getValoracion())
                .build();
    }

    /**
     * El dibujo, contando titulares por línea: cuatro defensas, tres medios y tres delanteros
     * son un "4-3-3".
     *
     * <p>Devuelve null si no hay once que contar, o si ninguna línea de campo tiene gente
     * (partidos antiguos donde {@code posicion_campo} llegó vacía) — mejor no enseñar dibujo
     * que enseñar un "0-0-0".</p>
     */
    private String deducirFormacion(List<JugadorAlineacionDTO> titulares) {
        if (titulares.isEmpty()) {
            return null;
        }
        Map<String, Long> porLinea = titulares.stream()
                .filter(j -> j.getLinea() != null)
                .collect(Collectors.groupingBy(JugadorAlineacionDTO::getLinea, Collectors.counting()));

        List<Long> lineas = LINEAS_DEL_DIBUJO.stream().map(l -> porLinea.getOrDefault(l, 0L)).toList();
        if (lineas.stream().allMatch(n -> n == 0)) {
            return null;
        }
        return lineas.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }

    /**
     * En que linea colocar al jugador: lo que diga el partido y, si no lo dice, su posicion
     * natural.
     */
    private String lineaDe(EstadisticaJugadorPartido stat) {
        String delPartido = normalizarLinea(stat.getPosicionCampo());
        if (delPartido != null) {
            return delPartido;
        }
        Jugador jugador = stat.getJugador();
        if (jugador == null || jugador.getPosicion() == null) {
            return null;
        }
        return LINEA_POR_POSICION_NATURAL.get(jugador.getPosicion().trim().toUpperCase());
    }

    private String normalizarLinea(String posicionCampo) {
        if (posicionCampo == null) {
            return null;
        }
        String linea = posicionCampo.trim().toUpperCase();
        return ORDEN_LINEAS.contains(linea) ? linea : null;
    }

    /**
     * En que puesto de la lista va el jugador: portero, defensas, medios y delanteros.
     *
     * <p>Usa {@link #lineaDe} y no la posicion del partido a secas, para que los partidos
     * antiguos —que no la traen— se ordenen por la posicion natural en vez de amontonarse
     * todos al final con el portero perdido en medio. Los que no encajan en ninguna linea van
     * al final, nunca delante del portero.</p>
     */
    private int indiceDeLinea(EstadisticaJugadorPartido stat) {
        String linea = lineaDe(stat);
        int indice = linea == null ? -1 : ORDEN_LINEAS.indexOf(linea);
        return indice < 0 ? ORDEN_LINEAS.size() : indice;
    }
}
