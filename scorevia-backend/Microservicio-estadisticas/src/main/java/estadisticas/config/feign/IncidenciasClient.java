package estadisticas.config.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import estadisticas.dto.incidencias.IncidenciaDTO;

// FIX: Añadido configuration = IncidenciasFeignConfig.class
@FeignClient(name = "microservicio-incidencias", path = "/api/v1/incidencias", configuration = IncidenciasFeignConfig.class)
public interface IncidenciasClient {

    @PostMapping
    void reportarIncidencia(@RequestBody IncidenciaDTO incidencia);
}