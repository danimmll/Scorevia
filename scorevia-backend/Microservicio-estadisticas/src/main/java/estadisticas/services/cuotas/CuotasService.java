package estadisticas.services.cuotas;

import estadisticas.dto.partidos.CuotasPartidoDTO;

/**
 * Cuotas de casas de apuestas para un partido.
 *
 * @author Daniel
 * @version 1.0
 */
public interface CuotasService {

    /**
     * Las cuotas de un partido, pedidas a API-Football solo si merece la pena.
     *
     * <p>Va bajo demanda y cacheado, no ingerido: las cuotas solo se miran cuando alguien abre
     * la ficha del partido, y traerlas de antemano para todos los partidos del día sería
     * pagar por lo que nadie va a ver. La caché dura lo mismo que tardan en moverse (la API
     * las refresca cada 3 horas).</p>
     *
     * @throws estadisticas.exception.NotFoundException si el partido no existe.
     */
    CuotasPartidoDTO getCuotasPartido(Integer idPartido);
}
