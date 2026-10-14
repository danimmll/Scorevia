package estadisticas.services.temporadas;

import estadisticas.dto.equipos.TemporadaEquipoDTO;
import estadisticas.entities.entrenadores.HistorialEntrenador;
import estadisticas.entities.equipos.Equipo;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.exception.NotFoundException;
import estadisticas.repositories.entrenadores.HistorialEntrenadorRepository;
import estadisticas.repositories.equipos.EquipoRepository;
import estadisticas.repositories.ligas.ClasificacionRepository;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import estadisticas.repositories.partidos.PartidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * La temporada completa de un equipo, montada a partir de lo que ya hay en la base.
 *
 * <p>Tres fuentes, ninguna nueva:</p>
 * <ul>
 *   <li>{@code estadisticas_jugador_partido} para la plantilla y lo que hizo cada uno</li>
 *   <li>{@code clasificaciones} para las competiciones jugadas y los titulos</li>
 *   <li>{@code historial_entrenadores} para quien la dirigio</li>
 * </ul>
 *
 * <p>La plantilla sale de los partidos que se jugaron de verdad y no del historial de
 * dorsales: asi entra cualquiera que llegase a jugar aunque no figure en la plantilla
 * registrada, que es lo que pasa con los canteranos que suben sueltos.</p>
 *
 * <p>Cuesta <b>cero</b> peticiones a API-Football.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class TemporadaEquipoServiceImpl implements TemporadaEquipoService {

    /** Un primer puesto en la clasificacion final es un titulo ganado. */
    private static final int PUESTO_DE_CAMPEON = 1;

    private final EquipoRepository equipoRepository;
    private final PartidoRepository partidoRepository;
    private final ClasificacionRepository clasificacionRepository;
    private final HistorialEntrenadorRepository historialEntrenadorRepository;
    private final EstadisticaJugadorPartidoRepository estadisticaRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "plantillaTemporada", key = "'temporadas-' + #idEquipo")
    public List<Integer> temporadasDisponibles(Integer idEquipo) {
        return partidoRepository.temporadasDeEquipo(idEquipo);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "plantillaTemporada", key = "#idEquipo + '-' + #temporada")
    public TemporadaEquipoDTO detalle(Integer idEquipo, Integer temporada) {
        Equipo equipo = equipoRepository.findById(idEquipo)
                .orElseThrow(() -> new NotFoundException("Equipo no encontrado"));

        List<TemporadaEquipoDTO.JugadorTemporadaDTO> plantilla =
                estadisticaRepository.resumenPlantillaDeTemporada(idEquipo, temporada).stream()
                        .map(this::aJugador)
                        .toList();

        List<TemporadaEquipoDTO.CompeticionTemporadaDTO> competiciones =
                clasificacionRepository
                        .findByEquipo_IdEquipoAndTemporadaOrderByPosicionAsc(idEquipo, temporada).stream()
                        .map(this::aCompeticion)
                        .toList();

        return TemporadaEquipoDTO.builder()
                .idEquipo(idEquipo)
                .equipo(equipo.getNombre())
                .escudo(equipo.getEscudoUrl())
                .temporada(temporada)
                .entrenadores(entrenadoresDe(idEquipo, temporada))
                .competiciones(competiciones)
                // El titulo no sale de la tabla `trofeos`: esa cuelga de jugadores y
                // entrenadores, nunca de equipos. Un primer puesto en la clasificacion final
                // de una competicion es la misma informacion y ya la tenemos.
                .titulos(competiciones.stream()
                        .filter(c -> c.getPosicion() != null && c.getPosicion() == PUESTO_DE_CAMPEON)
                        .toList())
                .plantilla(plantilla)
                .totales(sumar(plantilla))
                .build();
    }

    /**
     * Quien dirigia al equipo en esa temporada.
     *
     * <p>No basta con buscar la temporada exacta. El historial guarda la temporada en que
     * <b>empieza</b> cada etapa, no todas las que cubre: comprobado con el Real Madrid, hay
     * fila de 2021 (Ancelotti) y la siguiente es de 2025, asi que preguntar por 2024 no
     * devolvia a nadie aunque Ancelotti siguiera en el banquillo. Se coge la etapa vigente,
     * que es la de temporada mas alta de las que no superan la pedida.</p>
     *
     * <p>Puede haber mas de un entrenador: en una temporada con destitucion aparecen los dos,
     * y por eso se devuelven todos los del mismo ano y no solo el primero.</p>
     */
    private List<TemporadaEquipoDTO.EntrenadorTemporadaDTO> entrenadoresDe(Integer idEquipo, Integer temporada) {
        List<HistorialEntrenador> etapas = historialEntrenadorRepository
                .findByEquipo_IdEquipoAndTemporadaLessThanEqualOrderByTemporadaDesc(idEquipo, temporada);
        if (etapas.isEmpty()) {
            return List.of();
        }

        Integer temporadaVigente = etapas.get(0).getTemporada();
        return etapas.stream()
                .filter(h -> h.getEntrenador() != null && temporadaVigente.equals(h.getTemporada()))
                .map(h -> TemporadaEquipoDTO.EntrenadorTemporadaDTO.builder()
                        .idEntrenador(h.getEntrenador().getIdEntrenador())
                        .nombre(h.getEntrenador().getNombre())
                        .foto(h.getEntrenador().getFoto())
                        .temporadaRegistro(h.getTemporada())
                        .build())
                .sorted(Comparator.comparing(TemporadaEquipoDTO.EntrenadorTemporadaDTO::getNombre,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private TemporadaEquipoDTO.CompeticionTemporadaDTO aCompeticion(Clasificacion c) {
        return TemporadaEquipoDTO.CompeticionTemporadaDTO.builder()
                .idLiga(c.getLiga() != null ? c.getLiga().getIdLiga() : null)
                .nombre(c.getLiga() != null ? c.getLiga().getNombre() : null)
                .logo(c.getLiga() != null ? c.getLiga().getLogo() : null)
                .bandera(c.getLiga() != null ? c.getLiga().getBandera() : null)
                .posicion(c.getPosicion())
                .puntos(c.getPuntos())
                .partidosJugados(c.getPartidosJugados())
                .grupo(c.getGrupo())
                .build();
    }

    private TemporadaEquipoDTO.JugadorTemporadaDTO aJugador(Object[] f) {
        return TemporadaEquipoDTO.JugadorTemporadaDTO.builder()
                .idJugador(entero(f[0]))
                .nombre(texto(f[1]))
                .foto(texto(f[2]))
                .posicion(texto(f[3]))
                .partidos(entero(f[4]))
                .minutos(entero(f[5]))
                .goles(entero(f[6]))
                .asistencias(entero(f[7]))
                .amarillas(entero(f[8]))
                .rojas(entero(f[9]))
                .valoracionMedia(redondear(f[10]))
                .titularidades(entero(f[11]))
                .build();
    }

    private TemporadaEquipoDTO.TotalesDTO sumar(List<TemporadaEquipoDTO.JugadorTemporadaDTO> plantilla) {
        return TemporadaEquipoDTO.TotalesDTO.builder()
                .jugadoresUtilizados(plantilla.size())
                .goles(plantilla.stream().mapToInt(j -> valor(j.getGoles())).sum())
                .asistencias(plantilla.stream().mapToInt(j -> valor(j.getAsistencias())).sum())
                .amarillas(plantilla.stream().mapToInt(j -> valor(j.getAmarillas())).sum())
                .rojas(plantilla.stream().mapToInt(j -> valor(j.getRojas())).sum())
                .build();
    }

    /** Un SUM sobre columnas todas nulas vuelve null, no cero: aqui se trata como cero. */
    private int valor(Integer numero) {
        return numero == null ? 0 : numero;
    }

    private Integer entero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : null;
    }

    private String texto(Object valor) {
        return valor instanceof String cadena ? cadena : null;
    }

    /**
     * La valoracion media, a dos decimales.
     *
     * <p>Sin redondear sale un 7.214285714285714 que ni aporta ni cabe en la tabla. Vuelve
     * null en las competiciones que no publican valoraciones, que son muchas.</p>
     */
    private Double redondear(Object valor) {
        if (!(valor instanceof Number numero)) {
            return null;
        }
        return BigDecimal.valueOf(numero.doubleValue()).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
