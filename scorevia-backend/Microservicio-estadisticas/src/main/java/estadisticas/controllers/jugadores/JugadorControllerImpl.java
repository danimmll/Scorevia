package estadisticas.controllers.jugadores;

import estadisticas.config.ValidationUtils;
import estadisticas.dto.jugadores.HistorialBajaDTO;
import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.dto.jugadores.JugadorDTO;
import estadisticas.dto.equipos.TraspasoDTO;
import estadisticas.dto.ligas.TrofeoDTO;
import estadisticas.services.jugadores.JugadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import estadisticas.dto.jugadores.TemporadaJugadorDTO;
import estadisticas.services.temporadas.TemporadasJugadorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jugadores")
@RequiredArgsConstructor
public class JugadorControllerImpl implements JugadorController {

    private final JugadorService jugadorService;
    private final TemporadasJugadorService temporadasJugadorService;

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<JugadorDTO> obtenerPerfil(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(jugadorService.obtenerJugadorPorId(id));
    }

    @Override
    @GetMapping("/equipo/{idEquipo}")
    public ResponseEntity<Page<JugadorDTO>> obtenerPlantilla(@PathVariable Integer idEquipo, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(jugadorService.obtenerPlantillaPorEquipo(idEquipo, pageable));
    }

    @Override
    @GetMapping("/{id}/trofeos")
    public ResponseEntity<List<TrofeoDTO>> obtenerTrofeos(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(jugadorService.obtenerTrofeosJugador(id));
    }

    @Override
    @GetMapping("/{id}/bajas")
    public ResponseEntity<List<HistorialBajaDTO>> obtenerBajas(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(jugadorService.obtenerHistorialBajasJugador(id));
    }

    @Override
    @GetMapping("/{id}/traspasos")
    public ResponseEntity<List<TraspasoDTO>> obtenerTraspasos(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(jugadorService.obtenerTraspasosJugador(id));
    }

    @Override
    @GetMapping("/liga/{idLiga}")
    public ResponseEntity<Page<JugadorDTO>> getJugadoresPorLiga(@PathVariable Integer idLiga, @RequestParam Integer temporada, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(jugadorService.getJugadoresPorLiga(idLiga, temporada, pageable));
    }

    @Override
    @GetMapping("/buscar")
    public ResponseEntity<Page<JugadorDTO>> buscarJugadores(@RequestParam String nombre, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(jugadorService.buscarPorNombre(nombre, pageable));
    }

    @Override
    @GetMapping
    public ResponseEntity<Page<JugadorDTO>> getAllJugadores(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(jugadorService.getAllJugadores(pageable));
    }

    @Override
    @GetMapping("/{id}/dorsales")
    public ResponseEntity<List<HistorialDorsalDTO>> obtenerHistorialDorsales(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(jugadorService.obtenerHistorialDorsalesJugador(id));
    }

    @Override
    @GetMapping("/{id}/temporadas")
    public ResponseEntity<List<TemporadaJugadorDTO>> obtenerPorTemporadas(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(temporadasJugadorService.porTemporadas(id));
    }
}
