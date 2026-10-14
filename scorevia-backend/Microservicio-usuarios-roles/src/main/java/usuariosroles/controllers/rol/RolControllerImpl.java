package usuariosroles.controllers.rol;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import usuariosroles.dto.RolDTO;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.ConflictException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.exception.NoContentException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.services.rol.RolServiceImpl;
import usuariosroles.config.ValidationUtils;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Operaciones relacionadas con la gestión de roles")
public class RolControllerImpl implements RolController {

	private final RolServiceImpl service;

	@GetMapping
	public ResponseEntity<List<RolDTO>> getRoles() {
		if (service.getRolesDTO().isEmpty()) {
			throw new NoContentException("Sin ningún rol");
		}
		return ResponseEntity.ok(service.getRolesDTO());
	}

	@GetMapping("/{id_rol}")
	public ResponseEntity<RolDTO> getRolById(@PathVariable("id_rol") String idString) {

		Integer id = ValidationUtils.validarId(idString);
		
		RolDTO rol = service.getRolByIdDTO(id);
		if (rol == null) {
			throw new NotFoundException("Rol no encontrado", "rol-no-encontrado");
		}
		return ResponseEntity.ok(rol);
	}

	@PostMapping
	public ResponseEntity<Object> crearRol(@Valid @RequestBody RolDTO rolDTO, BindingResult result) {
		if (result.hasFieldErrors()) {
			throw new BadRequestException(ValidationUtils.validation(result));
		}

		if (service.existsRolDesactivado(rolDTO.getNombreRol())) {
			return ResponseEntity.status(HttpStatus.CREATED).body(service.activarRolDTO(rolDTO));
		}
		if (service.existsRol(rolDTO.getNombreRol())) {
			throw new DuplicateException("Ya existe un rol con ese nombre.");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(service.crearRolDTO(rolDTO));
	}

	@PutMapping("/{id_rol}")
	public ResponseEntity<Object> putRol(@Valid @RequestBody RolDTO rolDTO, BindingResult result,
			@PathVariable("id_rol") String idString) {
		
		Integer id = ValidationUtils.validarId(idString);

		RolDTO rol = service.getRolByIdDTO(id);
		if (rol == null) {
			throw new NotFoundException("Usuario No encontrado", "usuario-no-encontrado");
		}

		if (result.hasFieldErrors()) {
			throw new BadRequestException(ValidationUtils.validation(result));
		}

		// La comprobación de duplicado va DESPUÉS de hasFieldErrors (igual que en crearRol):
		// si nombreRol viene null/inválido por fallar @Valid, llamar aquí antes lanzaría NPE
		// en el .equals() de abajo en vez de devolver el 400 de validación esperado.
		if (service.existsRol(rolDTO.getNombreRol()) && !rolDTO.getNombreRol().equals(rol.getNombreRol())) {
			throw new DuplicateException("El nombre del rol ya existe");
		}

		if (rolDTO.getIdRol() != null && !rolDTO.getIdRol().equals(id)) {
			throw new ConflictException("Conflicto entre id proporciando y el body");
		}
		rolDTO.setIdRol(id);
		return ResponseEntity.ok(service.putRolDTO(rolDTO));
	}

	@DeleteMapping("/{id_rol}")
	public ResponseEntity<RolDTO> deleteRol(@PathVariable("id_rol") String idString) {
		
		Integer id = ValidationUtils.validarId(idString);

		RolDTO rol = service.getRolByIdDTO(id);
		if (rol == null) {
			throw new NotFoundException("Usuario No encontrado", "usuario-no-encontrado");
		}
		return ResponseEntity.ok(service.deleteRol(id));
	}

}
