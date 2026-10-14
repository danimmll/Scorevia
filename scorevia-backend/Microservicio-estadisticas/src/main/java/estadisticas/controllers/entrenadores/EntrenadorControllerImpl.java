package estadisticas.controllers.entrenadores;

import estadisticas.config.ValidationUtils;
import estadisticas.dto.entrenadores.EntrenadorDTO;
import estadisticas.dto.entrenadores.HistorialEntrenadorDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.services.entrenadores.EntrenadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entrenadores")
@RequiredArgsConstructor
public class EntrenadorControllerImpl implements EntrenadorController {

    private final EntrenadorService entrenadorService;

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<EntrenadorDTO> obtenerPorId(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(entrenadorService.obtenerEntrenadorPorId(id));
    }

    @Override
    @GetMapping("/equipo/{idEquipo}")
    public ResponseEntity<EntrenadorDTO> obtenerPorEquipo(@PathVariable("idEquipo") String idEquipoString) {
        Integer idEquipo = ValidationUtils.validarId(idEquipoString);
        return ResponseEntity.ok(entrenadorService.obtenerEntrenadorPorEquipo(idEquipo));
    }

    @Override
    @GetMapping("/{id}/trofeos")
    public ResponseEntity<List<TrofeoDTO>> obtenerTrofeos(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(entrenadorService.obtenerTrofeosEntrenador(id));
    }

    @Override
    @GetMapping("/liga/{idLiga}")
    public ResponseEntity<List<EntrenadorDTO>> obtenerPorLiga(@PathVariable("idLiga") String idLigaString, @RequestParam("temporada") String temporadaString) {
        Integer idLiga = ValidationUtils.validarId(idLigaString);
        Integer temporada = ValidationUtils.validarId(temporadaString);
        return ResponseEntity.ok(entrenadorService.obtenerEntrenadoresPorLiga(idLiga, temporada));
    }

    @Override
    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialEntrenadorDTO>> obtenerHistorialClubes(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(entrenadorService.obtenerHistorialClubes(id));
    }

    @Override
    @GetMapping("/equipo/{idEquipo}/historial")
    public ResponseEntity<List<HistorialEntrenadorDTO>> obtenerHistorialPorEquipo(@PathVariable("idEquipo") String idEquipoString) {
        Integer idEquipo = ValidationUtils.validarId(idEquipoString);
        return ResponseEntity.ok(entrenadorService.obtenerHistorialPorEquipo(idEquipo));
    }

    // Mismo límite que el buscador de equipos: es un autocompletado, no un listado.
    @Override
    @GetMapping("/buscar")
    public ResponseEntity<List<EntrenadorDTO>> buscarEntrenadores(@RequestParam String nombre) {
        return ResponseEntity.ok(entrenadorService.buscarPorNombre(nombre, 30));
    }

}