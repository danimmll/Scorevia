package predicciones.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import predicciones.dto.IncidenciaDTO;

// El "name" debe ser exactamente el spring.application.name del microservicio de incidencias en Eureka
@FeignClient(name = "microservicio-incidencias", path = "/api/v1/incidencias", configuration = IncidenciasFeignConfig.class)
public interface IncidenciasClient {

    @PostMapping
    void reportarIncidencia(@RequestBody IncidenciaDTO incidencia);
}