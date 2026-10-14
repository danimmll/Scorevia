package incidencias.mappers;

import org.mapstruct.Mapper;

import incidencias.dto.IncidenciaDTO;
import incidencias.entities.Incidencia;

/**
 * Mapper interface for converting between {@link Incidencia} entities and {@link IncidenciaDTO} objects.
 *
 * <p>This mapper uses the MapStruct library to automate the conversion process,
 * avoiding manual code for copying data between classes.</p>
 *
 * <p>Main responsibilities:</p>
 * <ul>
 *   <li>Transform database entities into DTOs to be sent to the client.</li>
 *   <li>Transform DTOs received from the client into entities to be stored in the database.</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface IncidenciaMapper {

    /**
     * Converts an {@link Incidencia} entity into an {@link IncidenciaDTO}.
     *
     * @param incidencia entity containing incident data
     * @return DTO object with data ready to be sent to the client
     */
    IncidenciaDTO toDTO(Incidencia incidencia);

    /**
     * Converts an {@link IncidenciaDTO} into an {@link Incidencia} entity.
     *
     * @param incidenciaDTO DTO containing incident data
     * @return entity ready to be stored in the database
     */
    Incidencia toIncidencia(IncidenciaDTO incidenciaDTO);
}
