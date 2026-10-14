package incidencias.controllers.incidencias;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import incidencias.dto.IncidenciaDTO;
import incidencias.exception.BadRequestException;
import incidencias.exception.NoContentException;
import incidencias.services.incidencias.IncidenciaServiceImpl;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/incidencias")
@RequiredArgsConstructor
public class IncidenciaControllerImpl implements IncidenciaController{

    private final IncidenciaServiceImpl incidenciaService;

    // Campos reales de la entidad Incidencia por los que se puede ordenar. "traza" se
    // excluye a propósito (texto libre largo, no tiene sentido como criterio de orden).
    private static final Set<String> CAMPOS_ORDENABLES = Set.of(
            "idIncidencia", "endpoint", "tipo", "clase", "metodo", "fecha", "usuarioContexto");

    @GetMapping
    public ResponseEntity<Page<IncidenciaDTO>> getAll(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "5") int size,
                                                       @RequestParam(defaultValue = "fecha") String sort,
                                                       @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = construirPageable(page, size, sort, dir);

        Page<IncidenciaDTO> incidencias = incidenciaService.getIncidenciasDTO(pageable);
        if (incidencias.isEmpty()) throw new NoContentException("Sin incidencias registradas");
        return ResponseEntity.ok(incidencias);
    }
    
    @GetMapping("/{id_incidencia}")
    public ResponseEntity<IncidenciaDTO> getById(@PathVariable("id_incidencia") String idString) {

        Integer id = validarId(idString);

        return ResponseEntity.ok(incidenciaService.getIncidenciaByIdDTO(id));
    }

    @GetMapping("/usuario/{usuario_contexto}")
    public ResponseEntity<Page<IncidenciaDTO>> getByUsuario(@PathVariable("usuario_contexto") String usuarioContexto,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "5") int size,
                                                             @RequestParam(defaultValue = "fecha") String sort,
                                                             @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = construirPageable(page, size, sort, dir);
        return ResponseEntity.ok(incidenciaService.getIncidenciasByUsuarioDTO(usuarioContexto, pageable));
    }

    @GetMapping("/clase/{clase}")
    public ResponseEntity<Page<IncidenciaDTO>> getByClase(@PathVariable String clase,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "5") int size,
                                                           @RequestParam(defaultValue = "fecha") String sort,
                                                           @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = construirPageable(page, size, sort, dir);
        return ResponseEntity.ok(incidenciaService.getIncidenciasByClaseDTO(clase, pageable));
    }

    @GetMapping("/metodo/{metodo}")
    public ResponseEntity<Page<IncidenciaDTO>> getByMetodo(@PathVariable String metodo,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "5") int size,
                                                            @RequestParam(defaultValue = "fecha") String sort,
                                                            @RequestParam(defaultValue = "desc") String dir) {
        Pageable pageable = construirPageable(page, size, sort, dir);
        return ResponseEntity.ok(incidenciaService.getIncidenciasByMetodoDTO(metodo, pageable));
    }

    private static final int TAMANIO_PAGINA_MAXIMO = 100;

    private Pageable construirPageable(int page, int size, String sort, String dir) {
        if (!CAMPOS_ORDENABLES.contains(sort)) {
            throw new BadRequestException("Campo de ordenación no válido: " + sort);
        }
        Sort.Direction direction = dir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, Math.min(size, TAMANIO_PAGINA_MAXIMO), Sort.by(direction, sort));
    }
    
    @PostMapping
    public ResponseEntity<IncidenciaDTO> crearIncidencia(@Valid @RequestBody IncidenciaDTO incidenciaDTO) {
        IncidenciaDTO creada = incidenciaService.crearIncidenciaDTO(incidenciaDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    private Integer validarId(String idString) {
		int id;
		try {
			id = Integer.parseInt(idString);
		} catch (NumberFormatException e) {
			throw new BadRequestException("ID no válido, debe ser un número entero", "id-invalido");
		}
		if (id <= 0)
			throw new BadRequestException("ID no válido, debe ser un número entero positivo", "id-invalido");
		return id;
	}

    
}
