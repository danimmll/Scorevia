package estadisticas.mappers.partidos;

import estadisticas.dto.partidos.EstadisticaEquipoDTO;
import estadisticas.entities.partidos.EstadisticaEquipoPartido;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import java.util.List;

// Mismo patrón limpio: inyectable por Spring e ignorando campos no mapeados para Jenkins
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EstadisticaEquipoMapper {

    @Mapping(source = "partido.idPartido", target = "idPartido")
    @Mapping(source = "equipo.idEquipo", target = "idEquipo")
    EstadisticaEquipoDTO toDto(EstadisticaEquipoPartido entity);

    @Mapping(target = "partido", ignore = true)
    @Mapping(target = "equipo", ignore = true)
    EstadisticaEquipoPartido toEntity(EstadisticaEquipoDTO dto);

    // MapStruct genera automáticamente la iteración de listas sin picar un solo bucle
    List<EstadisticaEquipoDTO> toDtoList(List<EstadisticaEquipoPartido> entities);
}