package usuariosroles.services.rol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import usuariosroles.dto.RolDTO;
import usuariosroles.entities.Rol;
import usuariosroles.entities.Usuario;
import usuariosroles.exception.ConflictException;
import usuariosroles.exception.NotFoundException;
import usuariosroles.mappers.RolMapper;
import usuariosroles.repositories.RolRepository;


@Service
@RequiredArgsConstructor
public class RolServiceImpl implements RolService{
	
	private static final boolean ACTIVO = true;
	
	private final RolRepository rolRepository;
	private final RolMapper rolMapper;

	@Transactional
	@CacheEvict(value = "roles", allEntries = true)
	public RolDTO crearRolDTO(RolDTO rolDTO) {
		Rol rol = rolMapper.toRol(rolDTO);
		Rol aux = rolRepository.save(rol);
		return rolMapper.toDtoPostDelete(aux);
	}
	

	@Transactional(readOnly = true)
	public RolDTO getRolByIdDTO(Integer id) {
    	Optional<Rol> optional = rolRepository.findByIdRolAndActivo(id, ACTIVO);
    	if (optional.isPresent()) {
    		return rolMapper.toDtoGet(optional.orElseThrow());
		}
    	return null;
	}
	

	@Transactional(readOnly = true)
	@Cacheable("roles")
	public List<RolDTO> getRolesDTO() {
		List<Rol> roles = rolRepository.findAllByActivo(ACTIVO);
		List<RolDTO> rolesDTO = new ArrayList<>();
		for (Rol rol : roles) {
			rolesDTO.add(rolMapper.toDtoGet(rol));
		}
		return rolesDTO;
	}
	

	@Transactional
	@CacheEvict(value = "roles", allEntries = true)
	public RolDTO putRolDTO(RolDTO rolDTO) {
		Optional<Rol> optional = rolRepository.findByIdRolAndActivo(rolDTO.getIdRol(), ACTIVO);
		if (optional.isPresent()) {
			Rol rolAux = optional.orElseThrow();
			rolAux.setNombreRol(rolDTO.getNombreRol());
			if (rolDTO.getActivo() != null) {
			rolAux.setActivo(rolDTO.getActivo());
			}
			Rol rolUpd = rolRepository.save(rolAux);
			return rolMapper.toDtoPut(rolUpd);
		}
		return null;
	}

	@Transactional
	@CacheEvict(value = "roles", allEntries = true)
	public RolDTO activarRolDTO(RolDTO rolDTO) {
		Optional<Rol> optional = rolRepository.findByNombreRol(rolDTO.getNombreRol());
		if (optional.isPresent()) {
			Rol rolAux = optional.orElseThrow();
			rolAux.setActivo(true);
			Rol rolUpd = rolRepository.save(rolAux);
			return rolMapper.toDtoPut(rolUpd);
		}
		return null;
	}
	

	@Transactional
	@CacheEvict(value = "roles", allEntries = true)
	public RolDTO deleteRol(Integer id) {
	    Rol rol = rolRepository.findByIdRolAndActivo(id, ACTIVO)
	            .orElseThrow(() -> new NotFoundException("Rol no encontrado", "rol-no-encontrado"));

	    Set<Usuario> usuariosAsignados = rol.getUsuarios();
	    
	    if (usuariosAsignados != null && !usuariosAsignados.isEmpty()) {
	        throw new ConflictException("No se puede eliminar un rol con usuarios asignados");
	    }

	    rol.setActivo(false);
	    Rol rolActualizado = rolRepository.save(rol);

	    return rolMapper.toDtoPostDelete(rolActualizado);
	}


    public boolean existsRol(String nombreRol) {
    	return rolRepository.existsByNombreRol(nombreRol);
    }


    public boolean existsRolDesactivado(String nombreRol) {
    	return rolRepository.existsByNombreRolAndActivo(nombreRol, false);
    }
}
