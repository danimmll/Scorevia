package incidencias.services.incidencias;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import incidencias.dto.IncidenciaDTO;
import incidencias.entities.Incidencia;
import incidencias.exception.NotFoundException;
import incidencias.exception.NoContentException;
import incidencias.mappers.IncidenciaMapper;
import incidencias.repositories.IncidenciaRepository;

@Service
@RequiredArgsConstructor
public class IncidenciaServiceImpl implements IncidenciaService{

    private final IncidenciaRepository incidenciaRepository;
    private final IncidenciaMapper incidenciaMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IncidenciaDTO crearIncidenciaDTO(IncidenciaDTO incidenciaDTO) {
        Incidencia incidencia = incidenciaMapper.toIncidencia(incidenciaDTO);
        Incidencia aux = incidenciaRepository.save(incidencia);
        return incidenciaMapper.toDTO(aux);
    }

    @Transactional(readOnly = true)
    public IncidenciaDTO getIncidenciaByIdDTO(Integer id) {
        Optional<Incidencia> optional = incidenciaRepository.findById(id);
        if (optional.isPresent()) {
            return incidenciaMapper.toDTO(optional.orElseThrow());
        }
        throw new NotFoundException("Incidencia no encontrada");
    }

    @Transactional(readOnly = true)
    public Page<IncidenciaDTO> getIncidenciasDTO(Pageable pageable) {
    	Page<Incidencia> incidencias = incidenciaRepository.findAll(pageable);
        if (incidencias.isEmpty()) {
            throw new NoContentException("Sin incidencias registradas");
        }
        
        return incidencias.map(incidenciaMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<IncidenciaDTO> getIncidenciasByUsuarioDTO(String usuarioContexto, Pageable pageable) {
        Page<Incidencia> incidencias = incidenciaRepository.findByUsuarioContexto(usuarioContexto, pageable);
        if (incidencias.isEmpty()) {
            throw new NoContentException("No hay incidencias para este usuario");
        }
        return incidencias.map(incidenciaMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<IncidenciaDTO> getIncidenciasByClaseDTO(String clase, Pageable pageable) {
        Page<Incidencia> incidencias = incidenciaRepository.findByClase(clase, pageable);
        if (incidencias.isEmpty()) {
            throw new NoContentException("No hay incidencias para esta clase");
        }
        return incidencias.map(incidenciaMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<IncidenciaDTO> getIncidenciasByMetodoDTO(String metodo, Pageable pageable) {
        Page<Incidencia> incidencias = incidenciaRepository.findByMetodo(metodo, pageable);
        if (incidencias.isEmpty()) {
            throw new NoContentException("No hay incidencias para este método");
        }
        return incidencias.map(incidenciaMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public boolean existeUsuario(String usuarioContexto) {
        return incidenciaRepository.existsByUsuarioContexto(usuarioContexto);
    }

    @Transactional(readOnly = true)
    public boolean existeClase(String clase) {
        return incidenciaRepository.existsByClase(clase);
    }

    @Transactional(readOnly = true)
    public boolean existeMetodo(String metodo) {
        return incidenciaRepository.existsByMetodo(metodo);
    }
}
