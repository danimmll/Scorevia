package incidencias.mappers;

import incidencias.dto.ReporteDTO;
import incidencias.entities.Reporte;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReporteMapper {

    ReporteDTO toDto(Reporte entity);

    Reporte toEntity(ReporteDTO dto);
}
