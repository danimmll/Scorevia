package estadisticas.services.internal;

import estadisticas.dto.internal.FormaPrediccionDTO;

public interface InternalDataService {

    FormaPrediccionDTO generarContexto(Long partidoId, Long localId, Long visitanteId, Long ligaId, Integer temporada);

    FormaPrediccionDTO generarContextoPorPartido(Integer idPartido);

}