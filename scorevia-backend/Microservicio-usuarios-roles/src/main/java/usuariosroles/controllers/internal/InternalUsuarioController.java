package usuariosroles.controllers.internal;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Usuarios (Interno)", description = "Operaciones internas de usuarios llamadas por otros microservicios.")
@Hidden // Lo ocultamos del Swagger público porque va protegido por X-Internal-Secret
public interface InternalUsuarioController {

    @Operation(
            summary = "Obtener nicknames por lote",
            description = "Devuelve el nickname de cada usuario solicitado, indexado por su id. "
                    + "Usado por otros microservicios para enriquecer respuestas "
                    + "sin exponer el resto de datos del usuario."
    )
    @ApiResponse(responseCode = "200", description = "Nicknames obtenidos correctamente")
    @ApiResponse(responseCode = "403", description = "Acceso interno denegado (X-Internal-Secret)")
    @GetMapping("/nicknames")
    Map<Integer, String> obtenerNicknames(
            @Parameter(description = "IDs de usuario a resolver", required = true)
            @RequestParam("ids") List<Integer> idsUsuarios
    );

    @Operation(
            summary = "Comprobar existencia de un usuario",
            description = "Confirma si existe un usuario con ese id, sin exponer ningún otro dato. "
                    + "Usado por otros microservicios para validar referencias a un usuario (p.ej. "
                    + "incidencias validando el denunciado de un reporte)."
    )
    @ApiResponse(responseCode = "200", description = "El usuario existe")
    @ApiResponse(responseCode = "404", description = "No existe ningún usuario con ese id")
    @ApiResponse(responseCode = "403", description = "Acceso interno denegado (X-Internal-Secret)")
    @GetMapping("/{id}/existe")
    ResponseEntity<Void> existeUsuario(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable("id") Integer idUsuario
    );

    @Operation(
            summary = "Comprobar si un usuario está activo",
            description = "Confirma si existe un usuario con ese id Y está activo (no baneado/desactivado). "
                    + "Usado por otros microservicios para revalidar sesiones de larga duración "
                    + "frente a un baneo a mitad de sesión."
    )
    @ApiResponse(responseCode = "200", description = "El usuario existe y está activo")
    @ApiResponse(responseCode = "404", description = "No existe, o existe pero no está activo")
    @ApiResponse(responseCode = "403", description = "Acceso interno denegado (X-Internal-Secret)")
    @GetMapping("/{id}/activo")
    ResponseEntity<Void> usuarioActivo(
            @Parameter(description = "ID del usuario", required = true)
            @PathVariable("id") Integer idUsuario
    );
}