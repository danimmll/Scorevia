package estadisticas.controllers.ligas;

import estadisticas.config.ValidationUtils;
import estadisticas.dto.ligas.LigaDTO;
import estadisticas.dto.ligas.TemporadaLigaDTO;
import estadisticas.services.ligas.LigaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import estadisticas.dto.ligas.RankingJugadorDTO;
import estadisticas.exception.BadRequestException;
import estadisticas.services.rankings.RankingLigaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ligas")
@RequiredArgsConstructor
public class LigaControllerImpl implements LigaController {

    private final LigaService ligaService;
    private final RankingLigaService rankingLigaService;

    @Override
    @GetMapping
    public ResponseEntity<List<LigaDTO>> obtenerTodas() {
        return ResponseEntity.ok(ligaService.obtenerTodasLigas());
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<LigaDTO> obtenerPorId(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(ligaService.obtenerLigaPorId(id));
    }

    @Override
    @GetMapping("/{id}/temporadas")
    public ResponseEntity<List<TemporadaLigaDTO>> obtenerTemporadas(@PathVariable("id") String idString) {
        Integer id = ValidationUtils.validarId(idString);
        return ResponseEntity.ok(ligaService.obtenerTemporadasDisponibles(id));
    }


    @Override
    @GetMapping("/{id}/rankings")
    public ResponseEntity<List<RankingJugadorDTO>> obtenerRanking(@PathVariable("id") String idString,
                                                                  @RequestParam("temporada") Integer temporada,
                                                                  @RequestParam(name = "tipo", defaultValue = "GOLES") String tipo) {
        Integer id = ValidationUtils.validarId(idString);
        RankingLigaService.Tipo tipoRanking;
        try {
            tipoRanking = RankingLigaService.Tipo.valueOf(tipo.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de ranking no válido. Valores: GOLES, ASISTENCIAS, AMARILLAS, ROJAS");
        }
        return ResponseEntity.ok(rankingLigaService.getRanking(id, temporada, tipoRanking));
    }
}
