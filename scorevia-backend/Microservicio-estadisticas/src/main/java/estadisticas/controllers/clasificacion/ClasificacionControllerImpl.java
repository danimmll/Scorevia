package estadisticas.controllers.clasificacion;

import estadisticas.config.ValidationUtils;
import estadisticas.dto.ligas.ClasificacionDTO;
import estadisticas.services.clasificacion.ClasificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clasificaciones")
@RequiredArgsConstructor
public class ClasificacionControllerImpl implements ClasificacionController {

    private final ClasificacionService clasificacionService;

    @Override
    @GetMapping("/liga/{idLiga}/temporada/{temporada}")
    public ResponseEntity<List<ClasificacionDTO>> obtenerTabla(
            @PathVariable("idLiga") String idLigaString,
            @PathVariable("temporada") String temporadaString) {

        Integer idLiga = ValidationUtils.validarId(idLigaString);
        Integer temporada = ValidationUtils.validarId(temporadaString);

        return ResponseEntity.ok(clasificacionService.obtenerClasificacion(idLiga, temporada));
    }

}