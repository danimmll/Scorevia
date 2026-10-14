package estadisticas.controllers.arbitros;

import estadisticas.dto.arbitros.ArbitroDetalleDTO;
import estadisticas.dto.partidos.PartidoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Árbitros", description = "Ficha de un árbitro y partidos que ha dirigido")
public interface ArbitroController {

    @Operation(summary = "Obtener la ficha de un árbitro",
            description = "Nombre, país, partidos dirigidos y tarjetas mostradas. La media de amarillas "
                    + "se calcula solo sobre los partidos suyos de los que constan eventos.")
    ResponseEntity<ArbitroDetalleDTO> getArbitroById(@PathVariable("id_arbitro") String idString);

    @Operation(summary = "Partidos dirigidos por un árbitro, del más reciente al más antiguo")
    ResponseEntity<Page<PartidoDTO>> getPartidosDelArbitro(
            @PathVariable("id_arbitro") String idString,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);
}
