package estadisticas.controllers.equipos;

import estadisticas.config.ValidationUtils;
import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.services.equipos.EquipoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import estadisticas.dto.equipos.RendimientoEquipoDTO;
import estadisticas.dto.equipos.TemporadaEquipoDTO;
import estadisticas.services.temporadas.TemporadaEquipoService;
import estadisticas.services.equipos.RendimientoEquipoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/equipos")
@RequiredArgsConstructor
public class EquipoControllerImpl implements EquipoController {

    private final EquipoService equipoService;
    private final RendimientoEquipoService rendimientoEquipoService;
    private final TemporadaEquipoService temporadaEquipoService;

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<EquipoDTO> obtenerPorId(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(equipoService.obtenerEquipoPorId(id));
    }

    @Override
    @GetMapping("/liga/{idLiga}")
    public ResponseEntity<List<EquipoDTO>> obtenerPorLiga(@PathVariable("idLiga") String idLigaString, @RequestParam("temporada") String temporadaString) {
        Integer idLiga = ValidationUtils.validarId(idLigaString);
        Integer temporada = ValidationUtils.validarId(temporadaString);
        return ResponseEntity.ok(equipoService.obtenerEquiposPorLiga(idLiga, temporada));
    }

    @Override
    @GetMapping("/{id}/traspasos")
    public ResponseEntity<List<TraspasoDTO>> obtenerTraspasos(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(equipoService.obtenerTraspasosPorEquipo(id));
    }

    @Override
    @GetMapping("/buscar")
    public ResponseEntity<List<EquipoDTO>> buscarEquipos(@RequestParam String nombre) {
        return ResponseEntity.ok(equipoService.buscarPorNombre(nombre, 30));
    }

    @Override
    @GetMapping("/{id}/rendimiento")
    public ResponseEntity<RendimientoEquipoDTO> obtenerRendimiento(@PathVariable("id") String idString,
                                                                    @RequestParam("liga") Integer idLiga,
                                                                    @RequestParam("temporada") Integer temporada) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(rendimientoEquipoService.getRendimiento(id, idLiga, temporada));
    }

    @Override
    @GetMapping("/{id}/temporadas")
    public ResponseEntity<List<Integer>> obtenerTemporadas(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(temporadaEquipoService.temporadasDisponibles(id));
    }

    @Override
    @GetMapping("/{id}/temporadas/{temporada}")
    public ResponseEntity<TemporadaEquipoDTO> obtenerTemporada(@PathVariable("id") String idString,
                                                                @PathVariable("temporada") Integer temporada) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(temporadaEquipoService.detalle(id, temporada));
    }
}
