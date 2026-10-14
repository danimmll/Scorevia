package estadisticas.mappers.equipos;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import estadisticas.dto.equipos.EstadioDTO;
import estadisticas.entities.equipos.Estadio;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EstadioMapper {
    EstadioDTO toDto(Estadio entity);
    Estadio toEntity(EstadioDTO dto);
}