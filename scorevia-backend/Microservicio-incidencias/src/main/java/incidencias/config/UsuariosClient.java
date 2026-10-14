package incidencias.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "microservicio-usuarios-roles",
        configuration = {IncidenciasFeignConfig.class, CustomFeignErrorDecoder.class})
public interface UsuariosClient {

    @GetMapping("/api/internal/usuarios/{id}/existe")
    ResponseEntity<Void> existeUsuario(@PathVariable("id") Integer idUsuario);
}
