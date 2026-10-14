package incidencias.services.incidencias;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import incidencias.dto.IncidenciaDTO;

/**
 * Service interface for managing incidents.
 * Defines operations related to the creation, retrieval, and verification of incidents.
 */
public interface IncidenciaService {

    /**
     * Creates a new incident.
     *
     * @param incidenciaDTO DTO containing incident data
     * @return DTO of the created incident
     */
    IncidenciaDTO crearIncidenciaDTO(IncidenciaDTO incidenciaDTO);

    /**
     * Retrieves an incident by its identifier.
     *
     * @param id incident identifier
     * @return DTO of the found incident
     */
    IncidenciaDTO getIncidenciaByIdDTO(Integer id);

    /**
     * Retrieves all registered incidents with pagination.
     *
     * @param pageable pagination information
     * @return paginated list of incident DTOs
     */
    Page<IncidenciaDTO> getIncidenciasDTO(Pageable pageable);

    /**
     * Retrieves incidents associated with a specific user, paginated.
     *
     * @param usuarioContexto user identifier
     * @param pageable pagination information
     * @return paginated list of incident DTOs
     */
    Page<IncidenciaDTO> getIncidenciasByUsuarioDTO(String usuarioContexto, Pageable pageable);

    /**
     * Retrieves incidents associated with a specific class, paginated.
     *
     * @param clase class name
     * @param pageable pagination information
     * @return paginated list of incident DTOs
     */
    Page<IncidenciaDTO> getIncidenciasByClaseDTO(String clase, Pageable pageable);

    /**
     * Retrieves incidents associated with a specific method, paginated.
     *
     * @param metodo method name
     * @param pageable pagination information
     * @return paginated list of incident DTOs
     */
    Page<IncidenciaDTO> getIncidenciasByMetodoDTO(String metodo, Pageable pageable);

    /**
     * Checks if there are incidents associated with a specific user.
     *
     * @param usuarioContexto user identifier
     * @return {@code true} if incidents exist, {@code false} otherwise
     */
    boolean existeUsuario(String usuarioContexto);

    /**
     * Checks if there are incidents associated with a specific class.
     *
     * @param clase class name
     * @return {@code true} if incidents exist, {@code false} otherwise
     */
    boolean existeClase(String clase);

    /**
     * Checks if there are incidents associated with a specific method.
     *
     * @param metodo method name
     * @return {@code true} if incidents exist, {@code false} otherwise
     */
    boolean existeMetodo(String metodo);
}
