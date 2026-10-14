package predicciones.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import predicciones.dto.FormaPrediccionDTO;
import predicciones.dto.internal.EventoPartidoDTO;

import java.util.List;

@FeignClient(name = "Microservicio-estadisticas", configuration = {IncidenciasFeignConfig.class, CustomFeignErrorDecoder.class})
public interface EstadisticasClient {

    @GetMapping("/internal/contexto-prediccion")
    FormaPrediccionDTO getContextoPrediccion(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestParam("idPartido") Integer idPartido
    );

    @GetMapping("/internal/partidos/{idPartido}/eventos")
    List<EventoPartidoDTO> obtenerEventosPartido(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("idPartido") Integer idPartido
    );
}