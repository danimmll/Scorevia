package estadisticas.services.temporadas;

import estadisticas.dto.jugadores.TemporadaJugadorDTO;
import estadisticas.repositories.partidos.EstadisticaJugadorPartidoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * El historial por temporadas de un jugador, agregado de sus partidos.
 *
 * <p>Se calcula al vuelo y se cachea en vez de mantener una tabla de resumen: los datos ya
 * estan en {@code estadisticas_jugador_partido}, y una tabla paralela habria que rehacerla
 * cada vez que entra un partido nuevo, con el riesgo de quedarse desfasada sin que se note.</p>
 *
 * @author Daniel
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class TemporadasJugadorServiceImpl implements TemporadasJugadorService {

    private final EstadisticaJugadorPartidoRepository estadisticaRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "temporadasJugador", key = "#idJugador")
    public List<TemporadaJugadorDTO> porTemporadas(Integer idJugador) {
        return estadisticaRepository.resumenPorTemporada(idJugador).stream()
                .map(this::aDto)
                .toList();
    }

    private TemporadaJugadorDTO aDto(Object[] f) {
        return TemporadaJugadorDTO.builder()
                .temporada(entero(f[0]))
                .idLiga(entero(f[1]))
                .liga(texto(f[2]))
                .logoLiga(texto(f[3]))
                .banderaLiga(texto(f[4]))
                .idEquipo(entero(f[5]))
                .equipo(texto(f[6]))
                .escudoEquipo(texto(f[7]))
                .partidos(entero(f[8]))
                .minutos(entero(f[9]))
                .goles(entero(f[10]))
                .asistencias(entero(f[11]))
                .amarillas(entero(f[12]))
                .rojas(entero(f[13]))
                .valoracionMedia(redondear(f[14]))
                .titularidades(entero(f[15]))
                .build();
    }

    /**
     * Los SUM() de JPA vuelven como Long y los COUNT() tambien, pero las columnas de origen son
     * enteras y ninguna cifra se acerca a desbordar un int: goles, minutos o tarjetas de una
     * temporada caben de sobra. Un SUM sobre columnas todas nulas vuelve null, no cero.
     */
    private Integer entero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : null;
    }

    private String texto(Object valor) {
        return valor instanceof String cadena ? cadena : null;
    }

    /**
     * La valoracion media, a dos decimales.
     *
     * <p>Sin redondear sale un 7.214285714285714 que ni aporta ni cabe en la tabla. Vuelve null
     * en las competiciones que no publican valoraciones, que son muchas.</p>
     */
    private Double redondear(Object valor) {
        if (!(valor instanceof Number numero)) {
            return null;
        }
        return BigDecimal.valueOf(numero.doubleValue()).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
