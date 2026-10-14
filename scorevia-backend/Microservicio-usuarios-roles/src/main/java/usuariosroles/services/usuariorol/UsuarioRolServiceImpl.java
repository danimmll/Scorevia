package usuariosroles.services.usuariorol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import usuariosroles.dto.UsuarioRolDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.entities.UsuarioRol;
import usuariosroles.exception.BadRequestException;
import usuariosroles.exception.DuplicateException;
import usuariosroles.mappers.UsuarioRolMapper;
import usuariosroles.models.UsuarioRolId;
import usuariosroles.repositories.RolRepository;
import usuariosroles.repositories.UsuarioRepository;
import usuariosroles.repositories.UsuarioRolRepository;

@Service
@RequiredArgsConstructor
public class UsuarioRolServiceImpl implements UsuarioRolService{
	
	private static final boolean ACTIVO = true;
	
	private final UsuarioRolRepository repository;

	private final UsuarioRepository usuarioRepository;

	private final RolRepository rolRepository;

	private final UsuarioRolMapper mapper;

	@Transactional
	public UsuarioRolDTO asignarRolUsuario(Integer idUsuario, Integer idRol) {
		UsuarioRolId id = new UsuarioRolId(idUsuario, idRol);
		
		Optional<Usuario> optionalUsuario = usuarioRepository.findByIdUsuarioAndActivo(idUsuario, ACTIVO);
		if (optionalUsuario.isEmpty()) {
			return null;
		}
		Usuario usuario = optionalUsuario.orElseThrow();
		
		Optional<Rol> optionalRol = rolRepository.findByIdRolAndActivo(idRol, ACTIVO);
		if (optionalRol.isEmpty()) {
			return null;
		}
		Rol rol = optionalRol.orElseThrow();
		
		Set<Rol> auxRols = usuario.getRoles();
		if (auxRols.contains(rol)) {
			throw new DuplicateException("ROL YA ASIGNADO A USUARIO");
		}
		
		UsuarioRol usuarioRol = new UsuarioRol();
		usuarioRol.setId(id);
		usuarioRol.setUsuario(usuario);
		usuarioRol.setRol(rol);

		// Guardado inmediato con captura de la violación de integridad, no uno diferido: la
		// comprobación de arriba mira la colección en memoria antes de persistir, así que dos
		// asignaciones concurrentes del mismo rol pueden pasarla ambas antes de que ninguna
		// confirme el cambio. La clave compuesta (usuario, rol) hace que la segunda falle por
		// restricción única en vez de duplicar la fila; sin confirmar el cambio aquí mismo,
		// ese fallo llegaría más tarde, fuera de esta captura, como un error interno sin
		// controlar en vez del conflicto esperado.
		try {
			UsuarioRol saved = repository.saveAndFlush(usuarioRol);
			return mapper.toDtoDeleteGetPost(saved);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateException("ROL YA ASIGNADO A USUARIO");
		}
	}

	@Transactional
	public UsuarioRolDTO quitarRolUsuario(Integer idUsuario, Integer idRol) {
	    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
	    if (authentication == null) {
	        throw new BadRequestException("No hay usuario autenticado");
	    }

	    String correoAuth = authentication.getName();
	    Set<String> rolesActuales = authentication.getAuthorities().stream()
	            .map(GrantedAuthority::getAuthority)
	            .collect(Collectors.toSet());

	    boolean isAdmin = rolesActuales.contains("ROLE_ADMIN");
	    Integer idAuth = usuarioRepository.findByCorreoUsuario(correoAuth)
	                                      .map(Usuario::getIdUsuario)
	                                      .orElse(Integer.MAX_VALUE);

	    if (!isAdmin && !idAuth.equals(idUsuario)) {
	        throw new BadRequestException("No puedes quitar roles de otro usuario");
	    }

	    UsuarioRolId id = new UsuarioRolId(idUsuario, idRol);
	    Optional<UsuarioRol> optional = repository.findById(id);
	    if (optional.isPresent()) {
	        UsuarioRol usuarioRol = optional.orElseThrow();
	        repository.deleteById(id);
	        return mapper.toDtoDeleteGetPost(usuarioRol);
	    }
	    return null;
	}


	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<UsuarioRolDTO> getRolesUsuario(Integer idUsuario) {
		List<UsuarioRol> usuarioRols = repository.findByUsuarioIdUsuario(idUsuario);
		List<UsuarioRolDTO> usuarioRolDTOs = new ArrayList<>();
		for (UsuarioRol usuarioRol : usuarioRols) {
			usuarioRolDTOs.add(mapper.toDtoRolesUsuario(usuarioRol));
		}
		return usuarioRolDTOs;
	}
	

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<UsuarioRolDTO> getUsuariosRol(Integer idRol) {
		List<UsuarioRol> usuarioRols = repository.findByRolIdRol(idRol);
		List<UsuarioRolDTO> usuarioRolDTOs = new ArrayList<>();
		for (UsuarioRol usuarioRol : usuarioRols) {
			usuarioRolDTOs.add(mapper.toDtoUsuariosRol(usuarioRol));
		}
		return usuarioRolDTOs;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public Page<UsuarioRolDTO> getAsignaciones(Pageable pageable) {
		return repository.findAll(pageable).map(mapper::toDtoDeleteGetPost);
	}
}
