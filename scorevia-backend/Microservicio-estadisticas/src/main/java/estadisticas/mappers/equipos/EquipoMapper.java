package estadisticas.mappers.equipos;

import estadisticas.dto.equipos.EquipoDTO;
import estadisticas.entities.equipos.Equipo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = EstadioMapper.class)
public interface EquipoMapper {

    // Nombres distintos a propósito en el DTO (logo, no escudoUrl) para no acoplar el
    // contrato público al nombre de columna interno -- MapStruct necesita el puente explícito
    // porque, a diferencia del resto de campos, aquí SÍ difieren los nombres.
    @Mapping(source = "escudoUrl", target = "logo")
    EquipoDTO toDto(Equipo equipo);

    @Mapping(source = "logo", target = "escudoUrl")
    Equipo toEntity(EquipoDTO dto);
}