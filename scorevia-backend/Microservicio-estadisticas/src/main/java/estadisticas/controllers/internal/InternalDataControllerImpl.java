package estadisticas.controllers.internal;

import estadisticas.dto.internal.EventoPartidoInternoDTO;
import estadisticas.dto.internal.FormaPrediccionDTO;
import estadisticas.services.internal.InternalDataService;
import estadisticas.services.partidos.PartidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InternalDataControllerImpl implements InternalDataController {

    private final InternalDataService internalDataService;
    private final PartidoService partidoService;

    public InternalDataControllerImpl(InternalDataService internalDataService, PartidoService partidoService) {
        this.internalDataService = internalDataService;
        this.partidoService = partidoService;
    }

    @Override
    public ResponseEntity<FormaPrediccionDTO> getContextoPrediccion(Integer idPartido) {
        return ResponseEntity.ok(internalDataService.generarContextoPorPartido(idPartido));
    }

    @Override
    public ResponseEntity<List<EventoPartidoInternoDTO>> getEventosPartido(Integer idPartido) {
        return ResponseEntity.ok(partidoService.getEventosPartidoInterno(idPartido));
    }

}