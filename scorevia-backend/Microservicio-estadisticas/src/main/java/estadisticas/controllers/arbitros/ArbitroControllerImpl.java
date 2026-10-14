package estadisticas.controllers.arbitros;

import estadisticas.config.ValidationUtils;
import estadisticas.dto.arbitros.ArbitroDetalleDTO;
import estadisticas.dto.partidos.PartidoDTO;
import estadisticas.services.arbitros.ArbitroService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/arbitros")
@RequiredArgsConstructor
public class ArbitroControllerImpl implements ArbitroController {

    private static final Sort MAS_RECIENTES_PRIMERO = Sort.by("fecha").descending();
    // Mismo tope que el resto de listados paginados del servicio: sin él, un size enorme
    // obliga a materializar en memoria de golpe todo lo que pida el cliente.
    private static final int TAMANIO_PAGINA_MAXIMO = 100;

    private final ArbitroService service;

    @Override
    @GetMapping("/{id_arbitro}")
    public ResponseEntity<ArbitroDetalleDTO> getArbitroById(@PathVariable("id_arbitro") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(service.getArbitroById(id));
    }

    @Override
    @GetMapping("/{id_arbitro}/partidos")
    public ResponseEntity<Page<PartidoDTO>> getPartidosDelArbitro(
            @PathVariable("id_arbitro") String idString,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(service.getPartidosDelArbitro(
                id, PageRequest.of(page, Math.min(size, TAMANIO_PAGINA_MAXIMO), MAS_RECIENTES_PRIMERO)));
    }
}
