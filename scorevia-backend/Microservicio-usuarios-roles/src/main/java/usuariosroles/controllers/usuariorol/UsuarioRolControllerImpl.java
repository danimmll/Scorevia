package usuariosroles.controllers.usuariorol;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import usuariosroles.config.ValidationUtils;
import usuariosroles.dto.RolDTO;
import usuariosroles.dto.UsuarioDTO;
import usuariosroles.dto.UsuarioRolDTO;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.NoContentException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.rol.RolServiceImpl;
import usuariosroles.services.usuariorol.UsuarioRolServiceImpl;
import usuariosroles.services.usuario.UsuarioServiceImpl;

@RestController
@RequiredArgsConstructor
@Tag(name = "Usuarios-Roles", description = "Operaciones de asignación de roles a usuarios y consulta de relaciones")
public class UsuarioRolControllerImpl implements UsuarioRolController{

    private final UsuarioRolServiceImpl service;

    private final UsuarioServiceImpl usuarioService;

    private final RolServiceImpl rolService;

    @PostMapping("/api/v1/usuarios/{id_usuario}/roles")
    public ResponseEntity<Object> asignarRol(@Valid @RequestBody UsuarioRolDTO usuarioRolDTO, BindingResult result,
                                        @PathVariable("id_usuario") String idUsuarioString) {

		Integer idUsuario = ValidationUtils.validarId(idUsuarioString);

        UsuarioDTO usuario = usuarioService.getUsuarioById(idUsuario);
        if (usuario == null) {
            throw new NotFoundException("Usuario no encontrado", "usuario-no-encontrado");
        }

        if (result.hasFieldErrors()) {
            throw new BadRequestException(ValidationUtils.validation(result));
        }

        if (usuarioRolDTO.getIdRol() == null) {
            throw new BadRequestException("ID de rol no proporcionado");
        }

        String usuarioRolIdRolString = usuarioRolDTO.getIdRol().toString();
		Integer idRol = ValidationUtils.validarId(usuarioRolIdRolString);

        if (idRol <= 0) {
            throw new BadRequestException("ID no válido, debe ser un número entero positivo", "id-invalido");
        }

        RolDTO rolDTO = rolService.getRolByIdDTO(idRol);
        if (rolDTO == null) {
            throw new NotFoundException("Rol no encontrado", "rol-no-encontrado");
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.asignarRolUsuario(idUsuario, idRol));
    }

    @DeleteMapping("/api/v1/usuarios/{id_usuario}/roles/{id_rol}")
    public ResponseEntity<UsuarioRolDTO> quitarRol(@PathVariable("id_usuario") String idUsuarioString,
                                                   @PathVariable("id_rol") String idRolString) {

		Integer idUsuario = ValidationUtils.validarId(idUsuarioString);

        UsuarioDTO usuario = usuarioService.getUsuarioById(idUsuario);
        if (usuario == null) {
            throw new NotFoundException("Usuario no encontrado", "usuario-no-encontrado");
        }

		Integer idRol = ValidationUtils.validarId(idRolString);

        RolDTO rolDTO = rolService.getRolByIdDTO(idRol);
        if (rolDTO == null) {
            throw new NotFoundException("Rol no encontrado", "rol-no-encontrado");
        }
        return ResponseEntity.ok(service.quitarRolUsuario(idUsuario, idRol));
    }

    @GetMapping("/api/v1/usuarios/{id_usuario}/roles")
    public ResponseEntity<List<UsuarioRolDTO>> getRolesUsuario(@PathVariable("id_usuario") String idUsuarioString) {

		Integer idUsuario = ValidationUtils.validarId(idUsuarioString);

        // Endpoint solo-ADMIN de verdad (ver @PreAuthorize en service.getRolesUsuario): antes se
        // usaba usuarioService.getUsuarioById aquí, que exige ser admin U el propio dueño -- un
        // usuario consultando sus propios roles pasaba este check y luego chocaba igualmente con
        // el @PreAuthorize del service, un 403 confuso a mitad de camino. usuarioActivo() es la
        // comprobación de existencia real sin lógica de ownership de por medio.
        if (!usuarioService.usuarioActivo(idUsuario)) {
            throw new NotFoundException("Usuario no encontrado", "usuario-no-encontrado");
        }

        return ResponseEntity.ok(service.getRolesUsuario(idUsuario));
    }

    @GetMapping("/api/v1/roles/{id_rol}/usuarios")
    public ResponseEntity<List<UsuarioRolDTO>> getUsuariosRol(@PathVariable("id_rol") String idRolString) {

		Integer idRol = ValidationUtils.validarId(idRolString);

        RolDTO rolDTO = rolService.getRolByIdDTO(idRol);
        if (rolDTO == null) {
            throw new NotFoundException("Rol no encontrado", "rol-no-encontrado");
        }
        return ResponseEntity.ok(service.getUsuariosRol(idRol));
    }

    @GetMapping("/api/v1/usuarios-roles")
    public ResponseEntity<Page<UsuarioRolDTO>> getTodasAsignaciones(@RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size,
                                                                     @RequestParam(defaultValue = "id.idUsuario") String sort,
                                                                     @RequestParam(defaultValue = "asc") String dir) {
        Sort.Direction direction = dir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(direction, sort));
        Page<UsuarioRolDTO> asignaciones = service.getAsignaciones(pageable);
        if (asignaciones.isEmpty()) {
            throw new NoContentException("Sin ninguna asignación");
        }
        return ResponseEntity.ok(asignaciones);
    }


    
    
}
