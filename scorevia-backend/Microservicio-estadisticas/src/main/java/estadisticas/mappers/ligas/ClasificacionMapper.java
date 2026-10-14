package estadisticas.mappers.ligas;

import estadisticas.dto.ligas.ClasificacionDTO;
import estadisticas.entities.ligas.Clasificacion;
import estadisticas.mappers.equipos.EquipoMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

// Añadimos uses = {EquipoMapper.class} para que sepa mapear el objeto Equipo anidado
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = {EquipoMapper.class})
public interface ClasificacionMapper {

    // Eliminamos el @Mapping que apuntaba a "nombreEquipo" porque el DTO ya no lo tiene
    ClasificacionDTO toDto(Clasificacion clasificacion);
}