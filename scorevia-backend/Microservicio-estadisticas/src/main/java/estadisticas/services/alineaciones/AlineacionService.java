package estadisticas.services.alineaciones;

import estadisticas.dto.alineaciones.AlineacionesPartidoDTO;

public interface AlineacionService {

    /**
     * Los dos onces de un partido, con banquillos, entrenadores y árbitro.
     *
     * @throws estadisticas.exception.NotFoundException si el partido no existe
     */
    AlineacionesPartidoDTO getAlineacionesPartido(Integer idPartido);
}
