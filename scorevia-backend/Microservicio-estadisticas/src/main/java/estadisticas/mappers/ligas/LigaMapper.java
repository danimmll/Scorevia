package estadisticas.mappers.ligas;

import estadisticas.dto.ligas.LigaDTO;
import estadisticas.entities.ligas.Liga;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LigaMapper {
    LigaDTO toDto(Liga liga);
    Liga toEntity(LigaDTO dto);
}