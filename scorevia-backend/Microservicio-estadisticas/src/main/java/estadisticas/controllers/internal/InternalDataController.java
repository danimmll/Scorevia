package estadisticas.controllers.internal;

import estadisticas.dto.internal.EventoPartidoInternoDTO;
import estadisticas.dto.internal.FormaPrediccionDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RequestMapping("/internal")
@Tag(name = "Internal Data", description = "Endpoints internos protegidos para comunicación directa con el motor de predicciones")
@Hidden // Protegido por X-Internal-Secret, no pensado para consumo desde el Swagger público
public interface InternalDataController {

    @Operation(
            summary = "Contexto de predicción de un partido",
            description = "Forma reciente, clasificación, cara a cara, bajas y estadísticas de jugadores de "
                    + "ambos equipos, usado por el motor de predicciones para calcular probabilidades."
    )
    @GetMapping("/contexto-prediccion")
    ResponseEntity<FormaPrediccionDTO> getContextoPrediccion(@RequestParam("idPartido") Integer idPartido);

    @Operation(
            summary = "Eventos de un partido",
            description = "Timeline de eventos (goles, tarjetas...) de un partido, usado por predicciones "
                    + "para ajustar probabilidades en directo (p. ej. impacto de una tarjeta roja)."
    )
    @GetMapping("/partidos/{idPartido}/eventos")
    ResponseEntity<List<EventoPartidoInternoDTO>> getEventosPartido(@PathVariable("idPartido") Integer idPartido);

}