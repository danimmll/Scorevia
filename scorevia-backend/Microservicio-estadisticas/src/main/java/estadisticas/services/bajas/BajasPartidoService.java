package estadisticas.services.bajas;

import estadisticas.dto.partidos.BajaPartidoDTO;

import java.util.List;

/**
 * Bajas y dudas de un partido concreto.
 *
 * @author Daniel
 * @version 1.0
 */
public interface BajasPartidoService {

    /**
     * Quién se pierde el partido y quién es duda, según {@code /injuries}.
     *
     * <p>Va bajo demanda y cacheado, como las cuotas: es un dato de la ficha del partido que
     * solo interesa mientras el partido está cerca, y traerlo por adelantado para todos sería
     * pagar por lo que nadie mira. Devuelve lista vacía —nunca error— cuando no hay datos o la
     * API falla: es un extra de la pantalla, no su contenido.</p>
     *
     * @throws estadisticas.exception.NotFoundException si el partido no existe.
     */
    List<BajaPartidoDTO> getBajasPartido(Integer idPartido);
}
