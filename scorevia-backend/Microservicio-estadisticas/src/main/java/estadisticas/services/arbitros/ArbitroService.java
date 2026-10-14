package estadisticas.services.arbitros;

import estadisticas.dto.arbitros.ArbitroDetalleDTO;
import estadisticas.dto.partidos.PartidoDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ArbitroService {

    /**
     * Ficha de un árbitro: quién es y cómo arbitra, medido en tarjetas.
     *
     * @throws estadisticas.exception.NotFoundException si el árbitro no existe
     */
    ArbitroDetalleDTO getArbitroById(Integer idArbitro);

    /** Partidos que ha dirigido, de más reciente a más antiguo. */
    Page<PartidoDTO> getPartidosDelArbitro(Integer idArbitro, Pageable pageable);
}
