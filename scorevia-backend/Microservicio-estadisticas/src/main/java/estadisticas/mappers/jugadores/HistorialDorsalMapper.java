package estadisticas.mappers.jugadores;

import estadisticas.dto.jugadores.HistorialDorsalDTO;
import estadisticas.entities.jugadores.HistorialDorsal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HistorialDorsalMapper {

    @Mapping(target = "idEquipo", source = "equipo.idEquipo")
    @Mapping(target = "nombreEquipo", source = "equipo.nombre")
    @Mapping(target = "escudoEquipo", source = "equipo.escudoUrl")
    HistorialDorsalDTO toDto(HistorialDorsal entity);
}