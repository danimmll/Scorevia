package estadisticas.mappers.arbitros;

import estadisticas.dto.arbitros.ArbitroDTO;
import estadisticas.entities.arbitros.Arbitro;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ArbitroMapper {
    ArbitroDTO toDto(Arbitro arbitro);
}